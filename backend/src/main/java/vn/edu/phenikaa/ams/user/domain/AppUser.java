package vn.edu.phenikaa.ams.user.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {
    public enum Role { STUDENT, ADMIN }
    public enum Status { ACTIVE, DISABLED }

    @Id
    private UUID id;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(nullable = false, length = 100)
    private String passwordHash;
    @Column(length = 80)
    private String displayName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status accountStatus;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected AppUser() {}

    public AppUser(String email, String passwordHash, String displayName) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = Role.STUDENT;
        this.accountStatus = Status.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    @PreUpdate
    void updateTimestamp() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public Role getRole() { return role; }
    public Status getAccountStatus() { return accountStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
