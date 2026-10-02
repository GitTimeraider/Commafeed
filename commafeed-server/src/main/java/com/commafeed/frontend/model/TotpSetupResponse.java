package com.commafeed.frontend.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import lombok.Data;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;

@SuppressWarnings("serial")
@Schema(description = "Authenticator app setup")
@Data
@RegisterForReflection
public class TotpSetupResponse implements Serializable {

    @Schema(
            description = "base32 encoded secret, to enter manually in the authenticator app",
            required = true)
    private String secret;

    @Schema(description = "otpauth uri, to display as a QR code", required = true)
    private String uri;
}
