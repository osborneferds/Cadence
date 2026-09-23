package com.cadence.api.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_entries")
public class AuditEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private java.time.Instant at;
    private String username;     // nullable for anonymous actions
    private String action;       // e.g. LOGIN, TASK_CREATE, ADMIN_RESET_ALL
    private String detail;       // nullable

    public AuditEntryEntity() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public java.time.Instant getAt() { return at; }
    public void setAt(java.time.Instant at) { this.at = at; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}