import type { MfaLoginOptions, PasskeyRegistrationOptions, PasskeyRegistrationRequest } from "@/app/types"

// passkeys are only available in secure contexts (https, or http://localhost)
export const passkeysSupported = () => window.isSecureContext && typeof window.PublicKeyCredential !== "undefined"

const toBase64Url = (buffer: ArrayBuffer) => {
    const bytes = new Uint8Array(buffer)
    let binary = ""
    for (const b of bytes) binary += String.fromCharCode(b)
    return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "")
}

const fromBase64Url = (value: string) => {
    const base64 = value
        .replace(/-/g, "+")
        .replace(/_/g, "/")
        .padEnd(Math.ceil(value.length / 4) * 4, "=")
    const binary = atob(base64)
    const bytes = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
    return bytes
}

const TIMEOUT = 2 * 60 * 1000

/**
 * create a new passkey with navigator.credentials.create()
 */
export async function createPasskey(options: PasskeyRegistrationOptions, name: string): Promise<PasskeyRegistrationRequest> {
    const credential = (await navigator.credentials.create({
        publicKey: {
            challenge: fromBase64Url(options.challenge),
            // no rp id: the passkey is bound to the host name of the page
            rp: { name: options.rpName },
            user: {
                id: fromBase64Url(options.userId),
                name: options.userName,
                displayName: options.userName,
            },
            pubKeyCredParams: options.algorithms.map(alg => ({ type: "public-key", alg })),
            excludeCredentials: options.excludeCredentialIds.map(id => ({ type: "public-key", id: fromBase64Url(id) })),
            authenticatorSelection: { residentKey: "preferred", userVerification: "preferred" },
            attestation: "none",
            timeout: TIMEOUT,
        },
    })) as PublicKeyCredential | null
    if (!credential) throw new Error("no passkey was created")

    const response = credential.response as AuthenticatorAttestationResponse
    return {
        name,
        clientDataJSON: toBase64Url(response.clientDataJSON),
        attestationObject: toBase64Url(response.attestationObject),
    }
}

/**
 * use a passkey with navigator.credentials.get()
 * @returns the JSON expected by the server in the j_mfa_passkey login form field
 */
export async function getPasskeyAssertion(options: MfaLoginOptions): Promise<string> {
    if (!options.passkeyChallenge) throw new Error("no passkey challenge")

    const credential = (await navigator.credentials.get({
        publicKey: {
            challenge: fromBase64Url(options.passkeyChallenge),
            allowCredentials: options.passkeyCredentialIds.map(id => ({ type: "public-key", id: fromBase64Url(id) })),
            userVerification: "preferred",
            timeout: TIMEOUT,
        },
    })) as PublicKeyCredential | null
    if (!credential) throw new Error("no passkey was selected")

    const response = credential.response as AuthenticatorAssertionResponse
    return JSON.stringify({
        id: toBase64Url(credential.rawId),
        clientDataJSON: toBase64Url(response.clientDataJSON),
        authenticatorData: toBase64Url(response.authenticatorData),
        signature: toBase64Url(response.signature),
    })
}
