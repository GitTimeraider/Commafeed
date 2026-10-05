package com.madnessfeed.frontend.model.request;

import lombok.Data;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;

@SuppressWarnings("serial")
@Schema
@Data
public class PasskeyRegistrationRequest implements Serializable {

    @Schema(description = "name of the passkey", required = true)
    private String name;

    @Schema(description = "base64url encoded clientDataJSON", required = true)
    private String clientDataJSON;

    @Schema(description = "base64url encoded attestationObject", required = true)
    private String attestationObject;
}
