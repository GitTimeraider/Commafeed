package com.commafeed.frontend.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import lombok.Data;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("serial")
@Schema(description = "Category shown on a public page")
@Data
@RegisterForReflection
public class PublicCategory implements Serializable {

    @Schema(description = "category id", required = true)
    private String id;

    @Schema(description = "category name", required = true)
    private String name;

    @Schema(description = "category children categories", required = true)
    private List<PublicCategory> children = new ArrayList<>();

    @Schema(description = "category feeds", required = true)
    private List<PublicSubscription> feeds = new ArrayList<>();
}
