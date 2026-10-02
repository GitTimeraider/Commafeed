package com.commafeed.frontend.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import lombok.Data;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("serial")
@Schema(description = "Public page settings")
@Data
@RegisterForReflection
public class PublicPageSettings implements Serializable {

    @Schema(
            description = "whether the public, read-only page of this user is enabled",
            required = true)
    private boolean enabled;

    @Schema(
            description = "whether feeds that are not in any category are shown on the public page",
            required = true)
    private boolean showUncategorized;

    @Schema(description = "ids of the categories shown on the public page", required = true)
    private List<Long> categoryIds = new ArrayList<>();
}
