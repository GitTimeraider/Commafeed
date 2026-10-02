package com.commafeed.frontend.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import lombok.Data;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;

@SuppressWarnings("serial")
@Schema(description = "Feed subscription shown on a public page")
@Data
@RegisterForReflection
public class PublicSubscription implements Serializable {

    @Schema(description = "subscription id", required = true)
    private Long id;

    @Schema(description = "subscription name", required = true)
    private String name;

    @Schema(description = "this subscription's website url", required = true)
    private String feedLink;

    @Schema(description = "The favicon url to use for this feed", required = true)
    private String iconUrl;
}
