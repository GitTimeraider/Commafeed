package com.madnessfeed.frontend.model.request;

import lombok.Data;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;

@SuppressWarnings("serial")
@Schema
@Data
public class TotpEnableRequest implements Serializable {

    @Schema(description = "code displayed by the authenticator app", required = true)
    private String code;
}
