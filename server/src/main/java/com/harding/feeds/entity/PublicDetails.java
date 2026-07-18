package com.harding.feeds.entity;

import jakarta.persistence.Embeddable;

import java.io.Serializable;

/**
 * Display details from the Google ID token, inlined into the app_user table.
 */
@Embeddable
public class PublicDetails implements Serializable {

    private String name;
    private String pictureUrl;
    private String givenName;
    private String familyName;
    private boolean emailVerified;

    public PublicDetails() {
    }

    public PublicDetails(String name, String pictureUrl, String givenName, String familyName, boolean emailVerified) {
        this.name = name;
        this.pictureUrl = pictureUrl;
        this.givenName = givenName;
        this.familyName = familyName;
        this.emailVerified = emailVerified;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPictureUrl() {
        return pictureUrl;
    }

    public void setPictureUrl(String pictureUrl) {
        this.pictureUrl = pictureUrl;
    }

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    public String getFamilyName() {
        return familyName;
    }

    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }
}
