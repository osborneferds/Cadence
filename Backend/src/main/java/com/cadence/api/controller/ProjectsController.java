package com.cadence.api.controller;

import com.cadence.api.model.ProjectEntity;
import com.cadence.api.repository.AudioLogRepository;
import com.cadence.api.repository.FocusSessionRepository;
import com.cadence.api.repository.ProjectRepository;
import com.cadence.api.repository.TaskRepository;
import com.cadence.api.service.AuditService;
import com.cadence.api.web.ApiException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class ProjectsController {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final FocusSessionRepository focusSessionRepository;
    private final AudioLogRepository audioLogRepository;
    private final AuditService auditService;

    public ProjectsController(ProjectRepository projectRepository,
                              TaskRepository taskRepository,
                              FocusSessionRepository focusSessionRepository,
                              AudioLogRepository audioLogRepository,
                              AuditService auditService) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.focusSessionRepository = focusSessionRepository;
        this.audioLogRepository = audioLogRepository;
        this.auditService = auditService;
    }

    public record ProjectRequest(String name, String color) {}

    @GetMapping
    public List<ProjectEntity> list() {
        return projectRepository.findAll();
    }

    @PostMapping
    public ProjectEntity create(@RequestBody ProjectRequest body) {
        if (body.name() == null || body.name().isBlank()) {
            throw ApiException.badRequest("Project name is required");
        }
        ProjectEntity p = new ProjectEntity();
        p.setName(body.name().trim());
        p.setColor(normalizeColor(body.color()));
        p.setCreatedAt(Instant.now());
        ProjectEntity saved = projectRepository.save(p);
        auditService.record("PROJECT_CREATE", saved.getName());
        return saved;
    }

    @PutMapping("/{id}")
    public ProjectEntity update(@PathVariable Long id, @RequestBody ProjectRequest body) {
        ProjectEntity p = projectRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Project not found"));
        if (body.name() == null || body.name().isBlank()) {
            throw ApiException.badRequest("Project name is required");
        }
        p.setName(body.name().trim());
        p.setColor(normalizeColor(body.color()));
        ProjectEntity saved = projectRepository.save(p);
        auditService.record("PROJECT_UPDATE", saved.getName());
        return saved;
    }

    /** Deletes the project, its tasks and unlinks sessions/audio (mirrors local mode). */
    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> delete(@PathVariable Long id) {
        ProjectEntity p = projectRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Project not found"));
        taskRepository.deleteByProjectId(id);
        focusSessionRepository.clearProject(id);
        audioLogRepository.clearProject(id);
        projectRepository.delete(p);
        auditService.record("PROJECT_DELETE", p.getName());
        return Map.of("ok", true);
    }

    static String normalizeColor(String color) {
        if (color == null || !color.matches("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")) {
            return "#0f7a55";
        }
        return color.toLowerCase();
    }
}
