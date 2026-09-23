package com.cadence.api.controller;

import com.cadence.api.dto.AudioLogView;
import com.cadence.api.model.AudioLogEntity;
import com.cadence.api.repository.AudioLogRepository;
import com.cadence.api.repository.ProjectRepository;
import com.cadence.api.service.AuditService;
import com.cadence.api.web.ApiException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audiologs")
public class AudioLogsController {

    private static final long MAX_BYTES = 5L * 1024 * 1024;   // 5 MB, matches the frontend limit
    private static final int MAX_DURATION_SEC = 300;          // 2 min enforced client-side, slack server-side

    private final AudioLogRepository audioLogRepository;
    private final ProjectRepository projectRepository;
    private final AuditService auditService;

    public AudioLogsController(AudioLogRepository audioLogRepository,
                               ProjectRepository projectRepository,
                               AuditService auditService) {
        this.audioLogRepository = audioLogRepository;
        this.projectRepository = projectRepository;
        this.auditService = auditService;
    }

    public record AudioRequest(Long projectId, String note, Integer durationSec,
                               String mimeType, String data) {}

    @GetMapping
    public List<AudioLogView> list() {
        return audioLogRepository.findAllViews();
    }

    @PostMapping
    public AudioLogView create(@RequestBody AudioRequest body) {
        if (body.data() == null || body.data().isBlank()) {
            throw ApiException.badRequest("Audio data is required");
        }
        byte[] bytes;
        try {
            bytes = java.util.Base64.getMimeDecoder().decode(body.data());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Audio data must be base64 encoded");
        }
        if (bytes.length == 0) {
            throw ApiException.badRequest("Audio data is empty");
        }
        if (bytes.length > MAX_BYTES) {
            throw ApiException.badRequest("Audio exceeds the 5 MB limit");
        }
        int duration = body.durationSec() == null ? 0 : body.durationSec();
        if (duration < 1 || duration > MAX_DURATION_SEC) {
            throw ApiException.badRequest("durationSec must be between 1 and " + MAX_DURATION_SEC);
        }
        if (body.projectId() != null && !projectRepository.existsById(body.projectId())) {
            throw ApiException.badRequest("Linked project does not exist");
        }
        String mime = body.mimeType() == null || body.mimeType().isBlank()
                ? "audio/webm" : body.mimeType().trim();
        if (!mime.toLowerCase().startsWith("audio/")) {
            throw ApiException.badRequest("mimeType must be an audio type");
        }

        AudioLogEntity log = new AudioLogEntity();
        log.setProjectId(body.projectId());
        log.setNote(body.note() == null || body.note().isBlank() ? null : body.note().trim());
        log.setDurationSec(duration);
        log.setMimeType(mime);
        log.setSizeBytes(bytes.length);
        log.setData(bytes);
        log.setAt(Instant.now());
        AudioLogEntity saved = audioLogRepository.save(log);
        auditService.record("AUDIO_ADD", saved.getSizeBytes() + " bytes");
        return new AudioLogView(saved.getId(), saved.getProjectId(), saved.getNote(),
                saved.getDurationSec(), saved.getMimeType(), saved.getSizeBytes(), saved.getAt());
    }

    /** Raw audio bytes for playback (frontend wraps the response in a blob URL). */
    @GetMapping("/{id}/data")
    public ResponseEntity<byte[]> data(@PathVariable Long id) {
        AudioLogEntity log = audioLogRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Audio log not found"));
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(log.getMimeType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(log.getSizeBytes())
                .body(log.getData());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        AudioLogEntity log = audioLogRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Audio log not found"));
        audioLogRepository.delete(log);
        auditService.record("AUDIO_DELETE", log.getSizeBytes() + " bytes");
        return Map.of("ok", true);
    }
}