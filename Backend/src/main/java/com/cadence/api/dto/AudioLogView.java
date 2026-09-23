package com.cadence.api.dto;

import java.time.Instant;

/**
 * Metadata projection of an audio log (no audio bytes).
 * Field names match what the frontend expects.
 */
public class AudioLogView {

    private Long id;
    private Long projectId;
    private String note;
    private int durationSec;
    private String mimeType;
    private long sizeBytes;
    private Instant at;

    public AudioLogView(Long id, Long projectId, String note, int durationSec,
                        String mimeType, long sizeBytes, Instant at) {
        this.id = id;
        this.projectId = projectId;
        this.note = note;
        this.durationSec = durationSec;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.at = at;
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public String getNote() { return note; }
    public int getDurationSec() { return durationSec; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public Instant getAt() { return at; }
}