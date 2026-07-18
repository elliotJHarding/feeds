package com.harding.feeds.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.LocalDate;

/**
 * A child feeds are recorded against. v1 UI assumes one baby per group; the
 * entity keeps twins or a second child a data change, not a schema migration.
 */
@Entity
public class Baby {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private LocalDate dateOfBirth;

    @ManyToOne(optional = false)
    @JoinColumn(name = "family_group_uuid", nullable = false)
    private FamilyGroup familyGroup;

    public Baby() {
    }

    public Baby(String name, LocalDate dateOfBirth, FamilyGroup familyGroup) {
        this.name = name;
        this.dateOfBirth = dateOfBirth;
        this.familyGroup = familyGroup;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public FamilyGroup getFamilyGroup() {
        return familyGroup;
    }

    public void setFamilyGroup(FamilyGroup familyGroup) {
        this.familyGroup = familyGroup;
    }
}
