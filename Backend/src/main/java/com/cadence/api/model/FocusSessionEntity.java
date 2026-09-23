package com.cadence.api.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "focus_sessions")
public class FocusSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int minutes;
    private String note;         // nullable
    private Long projectId;      // nullable - unassigned session
    private java.time.Instant at;

    public FocusSessionEntity() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public int getMinutes() { return minutes; }
    public void setMinutes(int minutes) { this.minutes = minutes; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public java.time.Instant getAt() { return at; }
    public void setAt(java.time.Instant at) { this.at = at; }
}