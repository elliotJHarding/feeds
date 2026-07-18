package com.harding.feeds.service;

import com.harding.feeds.entity.AppUser;
import com.harding.feeds.entity.FamilyGroup;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/**
 * The security invariant of the whole service layer: every data access is
 * scoped to the authenticated user's family group. Cross-group access is
 * reported as 404 - not 403 - so nothing leaks about other groups' data.
 */
final class GroupScope {

    private GroupScope() {
    }

    /** The caller's group, or 404 if they have not created/joined one yet. */
    static FamilyGroup requireGroup(AppUser user) {
        FamilyGroup group = user.getFamilyGroup();
        if (group == null) {
            throw notFound("User is not in a family group");
        }
        return group;
    }

    /** 404 unless the resource's group is the caller's group. */
    static void requireInGroup(AppUser user, FamilyGroup resourceGroup, String resourceName) {
        FamilyGroup group = requireGroup(user);
        if (resourceGroup == null || !Objects.equals(group.getUuid(), resourceGroup.getUuid())) {
            throw notFound(resourceName + " not found");
        }
    }

    static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
