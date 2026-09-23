package com.cadence.api.service;

import com.cadence.api.model.FocusSessionEntity;
import com.cadence.api.model.ProjectEntity;
import com.cadence.api.model.TaskEntity;
import com.cadence.api.repository.FocusSessionRepository;
import com.cadence.api.repository.ProjectRepository;
import com.cadence.api.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Aggregations for /stats, /report/weekly and /export.
 * Mirrors the calculations the frontend performs in Local mode.
 */
@Service
public class StatsService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final FocusSessionRepository focusSessionRepository;

    public StatsService(ProjectRepository projectRepository,
                        TaskRepository taskRepository,
                        FocusSessionRepository focusSessionRepository) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.focusSessionRepository = focusSessionRepository;
    }

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    private static Instant startOfDay(LocalDate d) {
        return d.atStartOfDay(ZONE).toInstant();
    }

    // ── /stats ──────────────────────────────────────────────────────

    public Map<String, Object> computeStats() {
        List<TaskEntity> tasks = taskRepository.findAll();
        List<FocusSessionEntity> sessions = focusSessionRepository.findAll();

        Instant todayStart = startOfDay(today());
        Instant weekStart = startOfDay(today().minusDays(6));

        int openTasks = 0, doneTotal = 0, doneToday = 0;
        for (TaskEntity t : tasks) {
            boolean done = "DONE".equals(t.getStatus());
            if (done) {
                doneTotal++;
                if (t.getCompletedAt() != null && !t.getCompletedAt().isBefore(todayStart)) {
                    doneToday++;
                }
            } else {
                openTasks++;
            }
        }

        int focusToday = 0, focusWeekTotal = 0;
        Map<LocalDate, Integer> minutesByDay = new TreeMap<>();
        for (FocusSessionEntity s : sessions) {
            if (s.getAt() == null) {
                continue;
            }
            LocalDate day = s.getAt().atZone(ZONE).toLocalDate();
            minutesByDay.merge(day, s.getMinutes(), Integer::sum);
            if (!s.getAt().isBefore(todayStart)) {
                focusToday += s.getMinutes();
            }
            if (!s.getAt().isBefore(weekStart)) {
                focusWeekTotal += s.getMinutes();
            }
        }

        List<Map<String, Object>> week = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today().minusDays(i);
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("label", d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            cell.put("minutes", minutesByDay.getOrDefault(d, 0));
            cell.put("iso", d.toString());
            week.add(cell);
        }

        int streak = computeStreak(minutesByDay, tasks);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projects", projectRepository.count());
        out.put("openTasks", openTasks);
        out.put("doneTotal", doneTotal);
        out.put("doneToday", doneToday);
        out.put("focusToday", focusToday);
        out.put("focusWeekTotal", focusWeekTotal);
        out.put("streak", streak);
        out.put("week", week);
        return out;
    }

    /** Consecutive active days (focus sessions or completed tasks), ending today or yesterday. */
    private int computeStreak(Map<LocalDate, Integer> minutesByDay, List<TaskEntity> tasks) {
        Map<LocalDate, Boolean> active = new TreeMap<>();
        minutesByDay.forEach((d, m) -> {
            if (m > 0) {
                active.put(d, true);
            }
        });
        for (TaskEntity t : tasks) {
            if ("DONE".equals(t.getStatus()) && t.getCompletedAt() != null) {
                active.put(t.getCompletedAt().atZone(ZONE).toLocalDate(), true);
            }
        }
        int streak = 0;
        LocalDate cur = today();
        if (!active.containsKey(cur)) {
            cur = cur.minusDays(1);
        }
        while (active.containsKey(cur)) {
            streak++;
            cur = cur.minusDays(1);
        }
        return streak;
    }

    // ── /report/weekly ──────────────────────────────────────────────

    public Map<String, Object> weeklyReport() {
        LocalDate thisMonday = today().with(DayOfWeek.MONDAY);
        LocalDate lastMonday = thisMonday.minusDays(7);
        Instant now = Instant.now();
        Instant tw0 = startOfDay(thisMonday);
        Instant lw0 = startOfDay(lastMonday);

        List<TaskEntity> tasks = taskRepository.findAll();
        List<FocusSessionEntity> sessions = focusSessionRepository.findAll();

        Map<String, Object> tw = window(tasks, sessions, tw0, now);
        Map<String, Object> lw = window(tasks, sessions, lw0, tw0);
        tw.put("start", thisMonday.toString());
        tw.put("end", thisMonday.plusDays(6).toString());
        lw.put("start", lastMonday.toString());
        lw.put("end", lastMonday.plusDays(6).toString());

        // per-project aggregation for this week
        Map<Long, Map<String, Object>> agg = new LinkedHashMap<>();
        for (TaskEntity t : tasks) {
            if (t.getProjectId() == null) {
                continue;
            }
            Map<String, Object> a = agg.computeIfAbsent(t.getProjectId(), k -> newProjectAgg());
            if ("DONE".equals(t.getStatus())) {
                if (t.getCompletedAt() != null
                        && !t.getCompletedAt().isBefore(tw0)
                        && t.getCompletedAt().isBefore(now)) {
                    a.merge("completed", 1, (x, y) -> (Integer) x + (Integer) y);
                }
            } else {
                a.merge("open", 1, (x, y) -> (Integer) x + (Integer) y);
            }
        }
        for (FocusSessionEntity s : sessions) {
            if (s.getProjectId() == null || s.getAt() == null) {
                continue;
            }
            if (!s.getAt().isBefore(tw0) && s.getAt().isBefore(now)) {
                Map<String, Object> a = agg.computeIfAbsent(s.getProjectId(), k -> newProjectAgg());
                a.merge("focusMinutes", s.getMinutes(), (x, y) -> (Integer) x + (Integer) y);
            }
        }

        List<Map<String, Object>> byProject = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Object>> e : agg.entrySet()) {
            ProjectEntity p = projectRepository.findById(e.getKey()).orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("projectId", e.getKey());
            row.put("name", p != null ? p.getName() : "—");
            row.put("color", p != null ? p.getColor() : null);
            row.putAll(e.getValue());
            byProject.add(row);
        }
        byProject.sort(Comparator.comparingInt(r -> -((Integer) r.get("focusMinutes"))));

        String todayStr = today().toString();
        String soonStr = today().plusDays(7).toString();
        int open = 0, overdue = 0, upcoming = 0;
        for (TaskEntity t : tasks) {
            if ("DONE".equals(t.getStatus())) {
                continue;
            }
            open++;
            if (t.getDueDate() != null && !t.getDueDate().isBlank()) {
                if (t.getDueDate().compareTo(todayStr) < 0) {
                    overdue++;
                } else if (t.getDueDate().compareTo(soonStr) <= 0) {
                    upcoming++;
                }
            }
        }

        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("tasksCompleted", (Integer) tw.get("tasksCompleted") - (Integer) lw.get("tasksCompleted"));
        delta.put("focusMinutes", (Integer) tw.get("focusMinutes") - (Integer) lw.get("focusMinutes"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("generatedAt", Instant.now());
        out.put("thisWeek", tw);
        out.put("lastWeek", lw);
        out.put("delta", delta);
        out.put("byProject", byProject);
        out.put("openTasks", open);
        out.put("overdue", overdue);
        out.put("upcoming7", upcoming);
        return out;
    }

    private static Map<String, Object> newProjectAgg() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("completed", 0);
        a.put("focusMinutes", 0);
        a.put("open", 0);
        return a;
    }

    private static Map<String, Object> window(List<TaskEntity> tasks,
                                              List<FocusSessionEntity> sessions,
                                              Instant from, Instant to) {
        int completed = 0, minutes = 0, sess = 0;
        for (TaskEntity t : tasks) {
            if ("DONE".equals(t.getStatus()) && t.getCompletedAt() != null
                    && !t.getCompletedAt().isBefore(from) && t.getCompletedAt().isBefore(to)) {
                completed++;
            }
        }
        for (FocusSessionEntity s : sessions) {
            if (s.getAt() != null && !s.getAt().isBefore(from) && s.getAt().isBefore(to)) {
                minutes += s.getMinutes();
                sess++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tasksCompleted", completed);
        out.put("focusMinutes", minutes);
        out.put("sessions", sess);
        return out;
    }

    // ── /export ─────────────────────────────────────────────────────

    public Map<String, Object> exportData() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", Instant.now());
        out.put("projects", projectRepository.findAll());
        out.put("tasks", taskRepository.findAll());
        out.put("focus", focusSessionRepository.findAll());
        return out;
    }
}