package com.cadence.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "audio_logs")
public class AudioLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long projectId;      // nullable
    private String note;         // caption, nullable
    private int durationSec;
    private String mimeType;     // e.g. audio/webm;codecs=opus
    private long sizeBytes;

    @Lob
    @Basic(fetch = FetchType.EAGER)
    private byte[] data;         // raw audio bytes - never serialized to JSON

    private java.time.Instant at;

    public AudioLogEntity() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public int getDurationSec() { return durationSec; }
    public void setDurationSec(int durationSec) { this.durationSec = durationSec; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

    @JsonIgnore
    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }

    public java.time.Instant getAt() { return at; }
    public void setAt(java.time.Instant at) { this.at = at; }
}