package com.cadence.api.controller;

import com.cadence.api.model.TaskEntity;
import com.cadence.api.repository.ProjectRepository;
import com.cadence.api.repository.TaskRepository;
import com.cadence.api.service.AuditService;
import com.cadence.api.web.ApiException;
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
import java.util.Set;

@RestController
@RequestMapping("/api/tasks")
public class TasksController {

    private static final Set<String> STATUSES = Set.of("TODO", "DOING", "DONE");
    private static final Set<String> PRIORITIES = Set.of("HIGH", "MEDIUM", "LOW");

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final AuditService auditService;

    public TasksController(TaskRepository taskRepository,
                           ProjectRepository projectRepository,
                           AuditService auditService) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.auditService = auditService;
    }

    @GetMapping
    public List<TaskEntity> list() {
        return taskRepository.findAll();
    }

    /** Accepts the full task JSON the frontend sends (id may be present but is ignored). */
    public record TaskRequest(Long id, Long projectId, String title, String status,
                              String priority, String dueDate, String notes,
                              String tags, Instant createdAt, Instant completedAt) {}

    @PostMapping
    public TaskEntity create(@RequestBody TaskRequest body) {
        TaskEntity t = new TaskEntity();
        apply(t, body);
        if (t.getCreatedAt() == null) {
            t.setCreatedAt(Instant.now());
        }
        // a task created directly as DONE gets its completion timestamp now
        if ("DONE".equals(t.getStatus()) && t.getCompletedAt() == null) {
            t.setCompletedAt(Instant.now());
        }
        TaskEntity saved = taskRepository.save(t);
        auditService.record("TASK_CREATE", trunc(saved.getTitle()));
        return saved;
    }

    @PutMapping("/{id}")
    public TaskEntity update(@PathVariable Long id, @RequestBody TaskRequest body) {
        TaskEntity t = taskRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Task not found"));
        boolean wasDone = "DONE".equals(t.getStatus());
        Instant previousCompletedAt = t.getCompletedAt();
        apply(t, body);
        if ("DONE".equals(t.getStatus())) {
            // keep the original completion time when the task was already done
            t.setCompletedAt(wasDone && previousCompletedAt != null ? previousCompletedAt : Instant.now());
        } else {
            t.setCompletedAt(null);
        }
        TaskEntity saved = taskRepository.save(t);
        auditService.record("TASK_UPDATE", trunc(saved.getTitle()) + " status=" + saved.getStatus());
        return saved;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        TaskEntity t = taskRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Task not found"));
        taskRepository.delete(t);
        auditService.record("TASK_DELETE", trunc(t.getTitle()));
        return Map.of("ok", true);
    }

    private void apply(TaskEntity t, TaskRequest body) {
        if (body.title() == null || body.title().isBlank()) {
            throw ApiException.badRequest("Task title is required");
        }
        String status = body.status() == null ? "TODO" : body.status().trim().toUpperCase();
        if (!STATUSES.contains(status)) {
            throw ApiException.badRequest("Status must be TODO, DOING or DONE");
        }
        String priority = body.priority() == null ? "MEDIUM" : body.priority().trim().toUpperCase();
        if (!PRIORITIES.contains(priority)) {
            throw ApiException.badRequest("Priority must be HIGH, MEDIUM or LOW");
        }
        if (body.dueDate() != null && !body.dueDate().isBlank()
                && !body.dueDate().matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            throw ApiException.badRequest("dueDate must be formatted as yyyy-MM-dd");
        }
        if (body.projectId() != null && !projectRepository.existsById(body.projectId())) {
            throw ApiException.badRequest("Linked project does not exist");
        }
        t.setTitle(body.title().trim());
        t.setProjectId(body.projectId());
        t.setStatus(status);
        t.setPriority(priority);
        t.setDueDate(body.dueDate() == null || body.dueDate().isBlank() ? null : body.dueDate());
        t.setNotes(blankToNull(body.notes()));
        t.setTags(blankToNull(body.tags()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String trunc(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 60 ? s.substring(0, 60) : s;
    }
}