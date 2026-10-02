package com.commafeed.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A WebAuthn credential (passkey or security key) used as a second authentication factor */
@Entity
@Table(name = "USERPASSKEYS")
@SuppressWarnings("serial")
@Getter
@Setter
public class UserPasskey extends AbstractModel {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    /** base64url encoded credential id */
    @Column(name = "credential_id", length = 1400, nullable = false)
    private String credentialId;

    /** base64 encoded X.509 SubjectPublicKeyInfo of the credential public key */
    @Column(name = "public_key", length = 2048, nullable = false)
    private String publicKey;

    /** COSE algorithm identifier of the public key */
    @Column(name = "algorithm", nullable = false)
    private int algorithm;

    @Column(name = "sign_count", nullable = false)
    private long signCount;

    /** WebAuthn relying party id (host name) the credential is bound to */
    @Column(name = "rp_id", length = 255, nullable = false)
    private String rpId;

    @Column(name = "created", nullable = false)
    private Instant created;

    @Column(name = "last_used")
    private Instant lastUsed;
}
