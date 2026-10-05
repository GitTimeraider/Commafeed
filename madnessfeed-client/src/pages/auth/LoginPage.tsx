import { msg } from "@lingui/core/macro"
import { useLingui } from "@lingui/react"
import { Trans } from "@lingui/react/macro"
import {
    Anchor,
    Box,
    Button,
    Center,
    Code,
    Container,
    Divider,
    Group,
    Paper,
    PasswordInput,
    Stack,
    Text,
    TextInput,
    Title,
} from "@mantine/core"
import { useForm } from "@mantine/form"
import { showNotification } from "@mantine/notifications"
import { useRef, useState } from "react"
import { useAsyncCallback } from "react-async-hook"
import { TbKey } from "react-icons/tb"
import { Link } from "react-router-dom"
import { applicationErrorType, client, errorToStrings } from "@/app/client"
import { redirectToRootCategory } from "@/app/redirect/thunks"
import { useAppDispatch, useAppSelector } from "@/app/store"
import type { LoginRequest, MfaLoginOptions } from "@/app/types"
import { getPasskeyAssertion, passkeysSupported } from "@/app/webauthn"
import { Alert } from "@/components/Alert"
import { PageTitle } from "@/pages/PageTitle"

type SecondFactorMode = "code" | "reset"

function SecondFactorStep(
    props: Readonly<{
        credentials: LoginRequest
        options: MfaLoginOptions
        onBack: () => void
        onSuccess: (reset: boolean) => void
    }>
) {
    const { _ } = useLingui()
    const [mode, setMode] = useState<SecondFactorMode>("code")
    const [totpCode, setTotpCode] = useState("")
    const [resetCode, setResetCode] = useState("")
    const [resetRequested, setResetRequested] = useState(false)
    const [passkeyError, setPasskeyError] = useState<string>()

    // whether the last login attempt used a reset code
    const resetCodeUsed = useRef(false)
    const login = useAsyncCallback(
        async (req: LoginRequest) => {
            resetCodeUsed.current = !!req.mfaResetCode
            return await client.user.login(req)
        },
        {
            onSuccess: () => props.onSuccess(resetCodeUsed.current),
        }
    )

    const passkeyLogin = useAsyncCallback(async () => {
        setPasskeyError(undefined)
        // get a new challenge for each attempt, challenges can only be used once
        const options = (await client.mfa.getLoginOptions(props.credentials)).data
        let assertion: string
        try {
            assertion = await getPasskeyAssertion(options)
        } catch (e) {
            setPasskeyError(e instanceof Error ? e.message : String(e))
            return
        }
        await login.execute({ ...props.credentials, mfaPasskey: assertion })
    })

    const requestResetCode = useAsyncCallback(async () => await client.mfa.requestResetCode(props.credentials), {
        onSuccess: () => setResetRequested(true),
    })

    const switchMode = (newMode: SecondFactorMode) => {
        login.reset()
        passkeyLogin.reset()
        setPasskeyError(undefined)
        setMode(newMode)
    }

    const errors = [
        ...(login.error ? errorToStrings(login.error) : []),
        ...(passkeyLogin.error ? errorToStrings(passkeyLogin.error) : []),
        ...(requestResetCode.error ? errorToStrings(requestResetCode.error) : []),
        ...(passkeyError ? [passkeyError] : []),
    ]

    return (
        <Stack>
            <Title order={2}>
                <Trans>Two-factor authentication</Trans>
            </Title>
            {errors.length > 0 && <Alert messages={errors} />}

            {mode === "code" && (
                <>
                    {props.options.totp && (
                        <form
                            onSubmit={async e => {
                                e.preventDefault()
                                await login.execute({ ...props.credentials, mfaTotp: totpCode })
                            }}
                        >
                            <Stack>
                                <TextInput
                                    label={<Trans>Authenticator app code</Trans>}
                                    description={<Trans>Enter the 6-digit code displayed by your authenticator app.</Trans>}
                                    placeholder="123456"
                                    value={totpCode}
                                    onChange={e => setTotpCode(e.currentTarget.value)}
                                    autoComplete="one-time-code"
                                    inputMode="numeric"
                                    maxLength={7}
                                    size="md"
                                    required
                                    data-autofocus
                                    autoFocus
                                />
                                <Button type="submit" loading={login.loading && !passkeyLogin.loading}>
                                    <Trans>Verify</Trans>
                                </Button>
                            </Stack>
                        </form>
                    )}

                    {props.options.totp && props.options.passkey && <Divider label={<Trans>or</Trans>} />}

                    {props.options.passkey && (
                        <Stack gap="xs">
                            <Button
                                variant={props.options.totp ? "default" : "filled"}
                                leftSection={<TbKey size={18} />}
                                onClick={async () => await passkeyLogin.execute()}
                                loading={passkeyLogin.loading}
                                disabled={!passkeysSupported()}
                            >
                                <Trans>Use a passkey</Trans>
                            </Button>
                            {!passkeysSupported() && (
                                <Text size="xs" c="dimmed">
                                    <Trans>Passkeys are only available when MadnessFeed is accessed over HTTPS.</Trans>
                                </Text>
                            )}
                        </Stack>
                    )}

                    <Group justify="space-between">
                        <Anchor component="button" type="button" c="dimmed" size="sm" onClick={props.onBack}>
                            <Trans>Back</Trans>
                        </Anchor>
                        <Anchor component="button" type="button" c="dimmed" size="sm" onClick={() => switchMode("reset")}>
                            <Trans>Lost access to your authenticator?</Trans>
                        </Anchor>
                    </Group>
                </>
            )}

            {mode === "reset" && (
                <>
                    <Text size="sm">
                        <Trans>
                            You can disable two-factor authentication with a reset code. For security reasons, the code is not displayed
                            here: it is written in the logs of the MadnessFeed server, which only the administrator of the server can read.
                        </Trans>
                    </Text>
                    <Text size="sm">
                        <Trans>With Docker, the administrator can find it with:</Trans>
                    </Text>
                    <Code block style={{ whiteSpace: "pre-wrap", wordBreak: "break-all" }}>
                        docker logs &lt;container-name&gt; 2&gt;&amp;1 | grep "Reset code"
                    </Code>

                    {!resetRequested && (
                        <Button variant="default" onClick={async () => await requestResetCode.execute()} loading={requestResetCode.loading}>
                            <Trans>Write a reset code in the server logs</Trans>
                        </Button>
                    )}

                    {resetRequested && (
                        <form
                            onSubmit={async e => {
                                e.preventDefault()
                                await login.execute({ ...props.credentials, mfaResetCode: resetCode })
                            }}
                        >
                            <Stack>
                                <Alert
                                    level="success"
                                    messages={[_(msg`A reset code has been written in the server logs. It is valid for 15 minutes.`)]}
                                />
                                <TextInput
                                    label={<Trans>Reset code</Trans>}
                                    description={
                                        <Trans>
                                            Two-factor authentication will be disabled for your account. You can set it up again in the
                                            settings after logging in.
                                        </Trans>
                                    }
                                    placeholder="ABCDE-12345"
                                    value={resetCode}
                                    onChange={e => setResetCode(e.currentTarget.value)}
                                    autoCapitalize="characters"
                                    autoComplete="off"
                                    size="md"
                                    required
                                />
                                <Button type="submit" color="red" loading={login.loading}>
                                    <Trans>Disable two-factor authentication and log in</Trans>
                                </Button>
                            </Stack>
                        </form>
                    )}

                    <Anchor component="button" type="button" c="dimmed" size="sm" onClick={() => switchMode("code")}>
                        <Trans>Back</Trans>
                    </Anchor>
                </>
            )}
        </Stack>
    )
}

export function LoginPage() {
    const serverInfos = useAppSelector(state => state.server.serverInfos)
    const dispatch = useAppDispatch()
    const { _ } = useLingui()
    const [mfaOptions, setMfaOptions] = useState<MfaLoginOptions>()

    const form = useForm<LoginRequest>({
        initialValues: {
            name: "",
            password: "",
        },
    })

    const onLoggedIn = (mfaReset: boolean) => {
        if (mfaReset) {
            showNotification({
                title: _(msg`Two-factor authentication disabled`),
                message: _(msg`You can set it up again in Settings > Security.`),
                color: "orange",
                autoClose: false,
            })
        }
        dispatch(redirectToRootCategory())
    }

    const login = useAsyncCallback(
        async (req: LoginRequest) => {
            try {
                await client.user.login(req)
                return true
            } catch (e) {
                if (applicationErrorType(e) !== "MFA_REQUIRED") throw e
                // the password is correct, ask for the second factor
                setMfaOptions((await client.mfa.getLoginOptions(req)).data)
                return false
            }
        },
        {
            onSuccess: loggedIn => {
                if (loggedIn) onLoggedIn(false)
            },
        }
    )

    return (
        <Container size="xs">
            <PageTitle />
            <Paper>
                {mfaOptions && (
                    <SecondFactorStep
                        credentials={form.values}
                        options={mfaOptions}
                        onBack={() => {
                            setMfaOptions(undefined)
                            login.reset()
                        }}
                        onSuccess={onLoggedIn}
                    />
                )}

                {!mfaOptions && (
                    <>
                        <Title order={2} mb="md">
                            <Trans>Log in</Trans>
                        </Title>
                        {login.error && (
                            <Box mb="md">
                                <Alert messages={errorToStrings(login.error)} />
                            </Box>
                        )}
                        <form onSubmit={form.onSubmit(login.execute)}>
                            <Stack>
                                <TextInput
                                    label={<Trans>User Name or E-mail</Trans>}
                                    placeholder={_(msg`User Name or E-mail`)}
                                    {...form.getInputProps("name")}
                                    description={
                                        serverInfos?.demoAccountEnabled ? (
                                            <Trans>Try out MadnessFeed with the demo account: demo/demo</Trans>
                                        ) : (
                                            ""
                                        )
                                    }
                                    size="md"
                                    required
                                    autoCapitalize="off"
                                />
                                <PasswordInput
                                    label={<Trans>Password</Trans>}
                                    placeholder={_(msg`Password`)}
                                    {...form.getInputProps("password")}
                                    size="md"
                                    required
                                />

                                {serverInfos?.smtpEnabled && (
                                    <Anchor component={Link} to="/passwordRecovery" c="dimmed">
                                        <Trans>Forgot password?</Trans>
                                    </Anchor>
                                )}

                                <Button type="submit" loading={login.loading}>
                                    <Trans>Log in</Trans>
                                </Button>
                                {serverInfos?.allowRegistrations && (
                                    <Center>
                                        <Group>
                                            <Trans>
                                                <Box>Need an account?</Box>
                                                <Anchor component={Link} to="/register">
                                                    Sign up!
                                                </Anchor>
                                            </Trans>
                                        </Group>
                                    </Center>
                                )}
                            </Stack>
                        </form>
                    </>
                )}
            </Paper>
        </Container>
    )
}
