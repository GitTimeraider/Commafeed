package com.commafeed.backend.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/** A public, read-only page showing a selection of the categories of a user */
@Entity
@Table(name = "PUBLICPAGES")
@SuppressWarnings("serial")
@Getter
@Setter
public class PublicPage extends AbstractModel {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** optional, shown next to the application name on the page */
    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** whether feeds that are not in any category are shown */
    @Column(name = "show_uncategorized", nullable = false)
    private boolean showUncategorized;

    /** secret part of the address of the page */
    @Column(name = "token", length = 64, nullable = false, unique = true)
    private String token;

    /** ids of the categories shown on the page */
    @ElementCollection
    @CollectionTable(
            name = "PUBLICPAGECATEGORIES",
            joinColumns = @JoinColumn(name = "public_page_id"))
    @Column(name = "category_id", nullable = false)
    private Set<Long> categoryIds = new HashSet<>();
}
