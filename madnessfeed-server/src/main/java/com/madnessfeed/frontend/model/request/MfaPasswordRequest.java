package com.madnessfeed.frontend.model.request;

import lombok.Data;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;

@SuppressWarnings("serial")
@Schema
@Data
public class MfaPasswordRequest implements Serializable {

    @Schema(description = "current password of the user", required = true)
    private String password;
}
