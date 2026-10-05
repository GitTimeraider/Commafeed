package com.madnessfeed.frontend.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import lombok.Data;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;
import java.time.Instant;

@SuppressWarnings("serial")
@Schema(description = "Passkey")
@Data
@RegisterForReflection
public class PasskeyInfo implements Serializable {

    @Schema(description = "passkey id", required = true)
    private Long id;

    @Schema(description = "passkey name", required = true)
    private String name;

    @Schema(description = "creation date", type = SchemaType.INTEGER, required = true)
    private Instant created;

    @Schema(description = "last time the passkey was used to log in", type = SchemaType.INTEGER)
    private Instant lastUsed;
}
