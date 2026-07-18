package com.harding.feeds.entity;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * A Google-authenticated user. {@code username} is the stable Google subject
 * claim; all domain data is reached through the (single) {@link FamilyGroup}.
 */
@Entity
@Table(indexes = {
        @Index(name = "idx_app_user_email", columnList = "email"),
        @Index(name = "idx_app_user_family_group", columnList = "family_group_uuid")
})
public class AppUser implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Google subject claim - the stable identifier used for login upsert. */
    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private boolean enabled;

    private String email;

    @ManyToOne
    private FamilyGroup familyGroup;

    private PublicDetails publicDetails;

    public AppUser() {
        this.enabled = true;
    }

    public AppUser(GoogleIdToken idToken) {
        this();
        GoogleIdToken.Payload payload = idToken.getPayload();

        this.email = payload.getEmail();
        this.username = payload.getSubject();
        this.publicDetails = new PublicDetails(
                (String) payload.get("name"),
                (String) payload.get("picture"),
                (String) payload.get("given_name"),
                (String) payload.get("family_name"),
                payload.getEmailVerified()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return null;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public FamilyGroup getFamilyGroup() {
        return familyGroup;
    }

    public void setFamilyGroup(FamilyGroup familyGroup) {
        this.familyGroup = familyGroup;
    }

    public PublicDetails getPublicDetails() {
        return publicDetails;
    }

    public void setPublicDetails(PublicDetails publicDetails) {
        this.publicDetails = publicDetails;
    }
}
