package com.commafeed.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.time.Instant;

@Entity
@Table(name = "USERS")
@SuppressWarnings("serial")
@Getter
@Setter
public class User extends AbstractModel {

    @Column(length = 32, nullable = false, unique = true)
    private String name;

    @Column(length = 255, unique = true)
    private String email;

    @Lob
    @Column(length = Integer.MAX_VALUE, nullable = false)
    @JdbcTypeCode(Types.LONGVARBINARY)
    private byte[] password;

    @Column(length = 40, unique = true)
    private String apiKey;

    @Lob
    @Column(length = Integer.MAX_VALUE, nullable = false)
    @JdbcTypeCode(Types.LONGVARBINARY)
    private byte[] salt;

    @Column(nullable = false)
    private boolean disabled;

    @Column private Instant lastLogin;

    @Column private Instant created;

    @Column(length = 40)
    private String recoverPasswordToken;

    @Column private Instant recoverPasswordTokenDate;

    @Column private Instant lastForceRefresh;

    @Column(name = "public_page_enabled", nullable = false)
    private boolean publicPageEnabled;

    @Column(name = "public_page_uncategorized", nullable = false)
    private boolean publicPageUncategorized;

    @Column(name = "public_page_token", length = 64, unique = true)
    private String publicPageToken;
}
