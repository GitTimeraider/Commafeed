package com.commafeed.frontend.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import lombok.Data;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("serial")
@Schema(description = "Second factors available to log in")
@Data
@RegisterForReflection
public class MfaLoginOptions implements Serializable {

    @Schema(description = "whether an authenticator app code can be used", required = true)
    private boolean totp;

    @Schema(description = "whether a passkey can be used", required = true)
    private boolean passkey;

    @Schema(description = "base64url encoded challenge for navigator.credentials.get()")
    private String passkeyChallenge;

    @Schema(description = "base64url encoded ids of the passkeys of the user", required = true)
    private List<String> passkeyCredentialIds = new ArrayList<>();
}
