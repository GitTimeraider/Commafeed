package com.commafeed.frontend.model.request;

import lombok.Data;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;

@SuppressWarnings("serial")
@Schema
@Data
public class MfaLoginRequest implements Serializable {

    @Schema(description = "user name or e-mail", required = true)
    private String name;

    @Schema(description = "password", required = true)
    private String password;
}
