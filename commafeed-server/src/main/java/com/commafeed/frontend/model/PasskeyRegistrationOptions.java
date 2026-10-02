package com.commafeed.frontend.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import lombok.Data;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("serial")
@Schema(description = "Options for navigator.credentials.create()")
@Data
@RegisterForReflection
public class PasskeyRegistrationOptions implements Serializable {

    @Schema(description = "base64url encoded challenge", required = true)
    private String challenge;

    @Schema(description = "relying party name", required = true)
    private String rpName;

    @Schema(description = "base64url encoded user handle", required = true)
    private String userId;

    @Schema(description = "user name", required = true)
    private String userName;

    @Schema(description = "supported COSE algorithms", required = true)
    private List<Integer> algorithms = new ArrayList<>();

    @Schema(
            description = "base64url encoded ids of the passkeys already registered",
            required = true)
    private List<String> excludeCredentialIds = new ArrayList<>();
}
