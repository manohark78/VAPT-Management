package com.company.vapt.user.entity;

import com.company.vapt.user.domain.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_activation_token")
public class UserActivationToken {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "used_at") private Instant usedAt;

    protected UserActivationToken() { }
    public UserActivationToken(UUID id, AppUser user, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.id=id; this.user=user; this.tokenHash=tokenHash; this.createdAt=createdAt; this.expiresAt=expiresAt;
    }
    public UUID getId(){return id;}
    public AppUser getUser(){return user;}
    public Instant getExpiresAt(){return expiresAt;}
    public Instant getUsedAt(){return usedAt;}
    public void consume(Instant at){this.usedAt=at;}
}
