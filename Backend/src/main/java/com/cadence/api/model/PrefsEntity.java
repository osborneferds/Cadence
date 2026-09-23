package com.cadence.api.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Per-user UI preferences. Keyed by username, or the special
 * {@link #GUEST_KEY} row for anonymous (not signed in) usage.
 */
@Entity
@Table(name = "prefs")
public class PrefsEntity {

    public static final String GUEST_KEY = "__guest__";

    @Id
    private String username;     // primary key

    private String theme;        // light | dark
    private int goalMinutes;     // daily focus goal
    private java.time.Instant updatedAt;

    public PrefsEntity() {
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public int getGoalMinutes() { return goalMinutes; }
    public void setGoalMinutes(int goalMinutes) { this.goalMinutes = goalMinutes; }

    public java.time.Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.Instant updatedAt) { this.updatedAt = updatedAt; }
}