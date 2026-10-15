package com.aalsikke.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column()
    private String username;

    @Column(unique = true)
    private String email;

    @Column(name = "phone_number", unique = true, length = 50)
    private String phoneNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "profile_mode", nullable = false, length = 20)
    private String profileMode;

    @Column(name = "account_status", nullable = false, length = 20)
    private String accountStatus = "ACTIVE";

    @JsonIgnore
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(length = 50)
    private String status;

    /** KAN-291: selected color palette preset (see ColorPaletteRequest for the allowed ids). */
    @Column(name = "color_palette", nullable = false, length = 30)
    private String colorPalette = "brand";

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified;

    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        if (this.accountStatus == null) {
            this.accountStatus = "ACTIVE";
        }
        if (this.profileMode == null) {
            this.profileMode = "PERSONAL";
        }
    }
}