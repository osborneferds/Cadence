package com.cadence.api.controller;

import com.cadence.api.model.FocusSessionEntity;
import com.cadence.api.repository.FocusSessionRepository;
import com.cadence.api.repository.ProjectRepository;
import com.cadence.api.service.AuditService;
import com.cadence.api.web.ApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/focus")
public class FocusController {

    private final FocusSessionRepository focusSessionRepository;
    private final ProjectRepository projectRepository;
    private final AuditService auditService;

    public FocusController(FocusSessionRepository focusSessionRepository,
                           ProjectRepository projectRepository,
                           AuditService auditService) {
        this.focusSessionRepository = focusSessionRepository;
        this.projectRepository = projectRepository;
        this.auditService = auditService;
    }

    public record FocusRequest(Integer minutes, String note, Long projectId) {}

    /** GET /api/focus?since=yyyy-MM-dd - sessions since that local date, newest first. */
    @GetMapping
    public List<FocusSessionEntity> list(@RequestParam(name = "since", required = false) String since) {
        if (since == null || since.isBlank()) {
            return focusSessionRepository.findAll().stream()
                    .sorted((a, b) -> b.getAt().compareTo(a.getAt()))
                    .toList();
        }
        LocalDate day;
        try {
            day = LocalDate.parse(since);
        } catch (Exception e) {
            throw ApiException.badRequest("since must be formatted as yyyy-MM-dd");
        }
        Instant from = day.atStartOfDay(ZoneId.systemDefault()).toInstant();
        return focusSessionRepository.findByAtGreaterThanEqualOrderByAtDesc(from);
    }

    @PostMapping
    public FocusSessionEntity create(@RequestBody FocusRequest body) {
        if (body.minutes() == null || body.minutes() < 1 || body.minutes() > 1440) {
            throw ApiException.badRequest("minutes must be between 1 and 1440");
        }
        if (body.projectId() != null && !projectRepository.existsById(body.projectId())) {
            throw ApiException.badRequest("Linked project does not exist");
        }
        FocusSessionEntity s = new FocusSessionEntity();
        s.setMinutes(body.minutes());
        s.setNote(body.note() == null || body.note().isBlank() ? null : body.note().trim());
        s.setProjectId(body.projectId());
        s.setAt(Instant.now());
        FocusSessionEntity saved = focusSessionRepository.save(s);
        auditService.record("FOCUS_ADD", saved.getMinutes() + " min");
        return saved;
    }
}