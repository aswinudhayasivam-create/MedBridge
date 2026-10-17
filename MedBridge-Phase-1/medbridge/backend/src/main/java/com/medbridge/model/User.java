package com.medbridge.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, unique = true, length = 190) private String email;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Role role;
    @Column(precision = 10, scale = 7) private BigDecimal latitude;
    @Column(precision = 10, scale = 7) private BigDecimal longitude;
    @Column(nullable = false) private boolean verified;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private NgoProfile ngoProfile;
    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { name=v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { email=v; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String v) { passwordHash=v; }
    public Role getRole() { return role; }
    public void setRole(Role v) { role=v; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal v) { latitude=v; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal v) { longitude=v; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean v) { verified=v; }
    public Instant getCreatedAt() { return createdAt; }
    public NgoProfile getNgoProfile() { return ngoProfile; }
    public void setNgoProfile(NgoProfile v) { ngoProfile=v; if (v!=null) v.setUser(this); }
}
