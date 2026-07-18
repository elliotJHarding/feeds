package com.harding.feeds.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * The trust circle every piece of domain data is scoped to.
 *
 * The invite code is a column here rather than a separate table: a group has
 * at most one active code (regenerating overwrites it, invalidating the old
 * one), the code carries no state of its own beyond which group it opens, and
 * v1 has no per-code expiry. A separate table would only add a join.
 */
@Entity
public class FamilyGroup implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    /** Current invite code; null until generated on demand. */
    @Column(unique = true, length = 8)
    private String inviteCode;

    @OneToMany(mappedBy = "familyGroup")
    private List<AppUser> users;

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public List<AppUser> getUsers() {
        return users;
    }

    public void setUsers(List<AppUser> users) {
        this.users = users;
    }
}
