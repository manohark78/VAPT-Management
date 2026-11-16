package com.company.vapt.user.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_scope")
public class UserScope {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "application_name")
    private String applicationName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UserScope() {
    }

    public UserScope(AppUser user, String ipAddress, String applicationName) {
        this.user = user;
        this.ipAddress = ipAddress;
        this.applicationName = applicationName;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
