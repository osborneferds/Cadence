package com.cadence.api.controller;

import com.cadence.api.model.AudioLogEntity;
import com.cadence.api.model.FocusSessionEntity;
import com.cadence.api.model.ProjectEntity;
import com.cadence.api.model.TaskEntity;
import com.cadence.api.model.UserEntity;
import com.cadence.api.repository.AudioLogRepository;
import com.cadence.api.repository.FocusSessionRepository;
import com.cadence.api.repository.ProjectRepository;
import com.cadence.api.repository.TaskRepository;
import com.cadence.api.service.AuditService;
import com.cadence.api.service.UserService;
import com.cadence.api.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final long IDLE_DAYS = 14; // project with no activity in 14 days = idle

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final FocusSessionRepository focusSessionRepository;
    private final AudioLogRepository audioLogRepository;
    private final UserService userService;
    private final AuditService auditService;
    private final String appVersion;
    private final boolean securityEnabled;
    private final Path dataDir;

    public AdminController(ProjectRepository projectRepository,
                           TaskRepository taskRepository,
                           FocusSessionRepository focusSessionRepository,
                           AudioLogRepository audioLogRepository,
                           UserService userService,
                           AuditService auditService,
                           @Value("${app.version:1.0.0}") String appVersion,
                           @Value("${app.security.enabled:false}") boolean securityEnabled) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.focusSessionRepository = focusSessionRepository;
        this.audioLogRepository = audioLogRepository;
        this.userService = userService;
        this.auditService = auditService;
        this.appVersion = appVersion;
        this.securityEnabled = securityEnabled;
        this.dataDir = Path.of("data");
    }

    // ── Overview ────────────────────────────────────────────────────

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        List<TaskEntity> tasks = taskRepository.findAll();
        List<FocusSessionEntity> sessions = focusSessionRepository.findAll();
        List<AudioLogEntity> audio = audioLogRepository.findAll();

        long tasksDone = tasks.stream().filter(t -> "DONE".equals(t.getStatus())).count();
        long focusMinutes = sessions.stream().mapToLong(FocusSessionEntity::getMinutes).sum();
        long audioBytes = audio.stream().mapToLong(AudioLogEntity::getSizeBytes).sum();
        long activeUsers = userService.listAll().stream().filter(UserEntity::isActive).count();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projects", projectRepository.count());
        out.put("tasks", tasks.size());
        out.put("tasksDone", tasksDone);
        out.put("focusSessions", sessions.size());
        out.put("focusMinutes", focusMinutes);
        out.put("audioLogs", audio.size());
        out.put("audioBytes", audioBytes);
        out.put("users", userService.listAll().size());
        out.put("activeUsers", activeUsers);
        out.put("auditEntries", auditService.count());
        return out;
    }

    // ── System ──────────────────────────────────────────────────────

    @GetMapping("/system")
    public Map<String, Object> system() {
        Runtime rt = Runtime.getRuntime();
        long heapUsedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long heapMaxMb = rt.maxMemory() / (1024 * 1024);
        long uptimeSec = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", appVersion);
        out.put("javaVersion", System.getProperty("java.version"));
        out.put("uptimeSec", uptimeSec);
        out.put("heapUsedMb", heapUsedMb);
        out.put("heapMaxMb", heapMaxMb);
        out.put("dbBytes", databaseBytes());
        out.put("securityEnabled", securityEnabled);
        out.put("serverTime", Instant.now());
        return out;
    }

    /** Total size of the H2 database files in ./data. */
    private long databaseBytes() {
        try {
            if (!Files.isDirectory(dataDir)) {
                return 0L;
            }
            try (var stream = Files.list(dataDir)) {
                return stream.filter(Files::isRegularFile)
                        .mapToLong(f -> {
                            try {
                                return Files.size(f);
                            } catch (IOException e) {
                                return 0L;
                            }
                        })
                        .sum();
            }
        } catch (IOException e) {
            return 0L;
        }
    }

    // ── Users ───────────────────────────────────────────────────────

    public record CreateUserRequest(String username, String password, String role) {}

    public record UpdateUserRequest(String role, Boolean active) {}

    public record ResetPasswordRequest(String password) {}

    @GetMapping("/users")
    public List<UserEntity> users() {
        return userService.listAll();
    }

    @PostMapping("/users")
    public UserEntity createUser(@RequestBody CreateUserRequest body) {
        return userService.create(body.username(), body.password(), body.role());
    }

    @PutMapping("/users/{id}")
    public UserEntity updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest body) {
        if (body.role() != null && body.active() != null) {
            throw ApiException.badRequest("Send either role or active, not both");
        }
        if (body.role() != null) {
            return userService.updateRole(id, body.role());
        }
        if (body.active() != null) {
            return userService.updateActive(id, body.active());
        }
        throw ApiException.badRequest("Nothing to update - send role or active");
    }

    @PutMapping("/users/{id}/password")
    public Map<String, Object> resetPassword(@PathVariable Long id, @RequestBody ResetPasswordRequest body) {
        userService.resetPassword(id, body.password());
        return Map.of("ok", true);
    }

    // ── Projects (with health) ──────────────────────────────────────

    @GetMapping("/projects")
    public List<Map<String, Object>> projects() {
        List<ProjectEntity> projects = projectRepository.findAll();
        List<TaskEntity> allTasks = taskRepository.findAll();
        List<FocusSessionEntity> allSessions = focusSessionRepository.findAll();
        String todayStr = LocalDate.now(ZoneId.systemDefault()).toString();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProjectEntity p : projects) {
            List<TaskEntity> ts = allTasks.stream()
                    .filter(t -> p.getId().equals(t.getProjectId()))
                    .toList();
            long done = ts.stream().filter(t -> "DONE".equals(t.getStatus())).count();
            long overdue = ts.stream()
                    .filter(t -> !"DONE".equals(t.getStatus()))
                    .filter(t -> t.getDueDate() != null && !t.getDueDate().isBlank())
                    .filter(t -> t.getDueDate().compareTo(todayStr) < 0)
                    .count();

            Instant lastActivity = ts.stream()
                    .map(TaskEntity::getCreatedAt)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            for (TaskEntity t : ts) {
                if ("DONE".equals(t.getStatus()) && t.getCompletedAt() != null
                        && (lastActivity == null || t.getCompletedAt().isAfter(lastActivity))) {
                    lastActivity = t.getCompletedAt();
                }
            }
            for (FocusSessionEntity s : allSessions) {
                if (p.getId().equals(s.getProjectId()) && s.getAt() != null
                        && (lastActivity == null || s.getAt().isAfter(lastActivity))) {
                    lastActivity = s.getAt();
                }
            }
            if (lastActivity == null) {
                lastActivity = p.getCreatedAt();
            }

            String health;
            if (ts.isEmpty()) {
                health = "empty";
            } else if (overdue > 0) {
                health = "overdue";
            } else if (lastActivity == null
                    || lastActivity.isBefore(Instant.now().minusSeconds(IDLE_DAYS * 86400L))) {
                health = "idle";
            } else {
                health = "healthy";
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.getId());
            row.put("name", p.getName());
            row.put("color", p.getColor());
            row.put("tasks", ts.size());
            row.put("done", done);
            row.put("overdue", overdue);
            row.put("lastActivityAt", lastActivity);
            row.put("health", health);
            rows.add(row);
        }
        return rows;
    }

    // ── Audit ───────────────────────────────────────────────────────

    @GetMapping("/audit")
    public List<?> audit() {
        return auditService.listNewestFirst();
    }

    // ── Danger zone ─────────────────────────────────────────────────

    @PostMapping("/clear-focus")
    @Transactional
    public Map<String, Object> clearFocus() {
        long n = focusSessionRepository.count();
        focusSessionRepository.deleteAllInBatch();
        auditService.record("ADMIN_CLEAR_FOCUS", n + " sessions deleted");
        return Map.of("ok", true, "deleted", n);
    }

    @PostMapping("/clear-audio")
    @Transactional
    public Map<String, Object> clearAudio() {
        long n = audioLogRepository.count();
        audioLogRepository.deleteAllInBatch();
        auditService.record("ADMIN_CLEAR_AUDIO", n + " logs deleted");
        return Map.of("ok", true, "deleted", n);
    }

    @PostMapping("/reset-all")
    @Transactional
    public Map<String, Object> resetAll() {
        long projects = projectRepository.count();
        long tasks = taskRepository.count();
        long sessions = focusSessionRepository.count();
        long audio = audioLogRepository.count();
        audioLogRepository.deleteAllInBatch();
        focusSessionRepository.deleteAllInBatch();
        taskRepository.deleteAllInBatch();
        projectRepository.deleteAllInBatch();
        auditService.record("ADMIN_RESET_ALL",
                projects + " projects, " + tasks + " tasks, "
                        + sessions + " sessions, " + audio + " audio logs deleted");
        return Map.of("ok", true);
    }
}