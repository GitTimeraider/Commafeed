package com.madnessfeed.frontend.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import lombok.Data;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("serial")
@Schema(description = "Two-factor authentication status")
@Data
@RegisterForReflection
public class MfaStatus implements Serializable {

    @Schema(description = "whether an authenticator app is set up", required = true)
    private boolean totpEnabled;

    @Schema(description = "registered passkeys", required = true)
    private List<PasskeyInfo> passkeys = new ArrayList<>();
}
