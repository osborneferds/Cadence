package com.cadence.api.controller;

import com.cadence.api.model.PrefsEntity;
import com.cadence.api.repository.PrefsRepository;
import com.cadence.api.service.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Per-user UI preferences. Anonymous requests use the shared "__guest__" row.
 * GET returns JSON null when nothing was saved yet (frontend treats that as "no server prefs").
 */
@RestController
@RequestMapping("/api/prefs")
public class PrefsController {

    private final PrefsRepository prefsRepository;

    public PrefsController(PrefsRepository prefsRepository) {
        this.prefsRepository = prefsRepository;
    }

    public record PrefsRequest(String theme, Integer goalMinutes) {}

    @GetMapping
    public PrefsEntity get() {
        return prefsRepository.findById(prefsKey()).orElse(null);
    }

    @PutMapping
    public PrefsEntity put(@RequestBody PrefsRequest body) {
        String key = prefsKey();
        PrefsEntity p = prefsRepository.findById(key).orElseGet(() -> {
            PrefsEntity fresh = new PrefsEntity();
            fresh.setUsername(key);
            return fresh;
        });
        if (body.theme() != null) {
            if (!body.theme().equals("light") && !body.theme().equals("dark")) {
                p.setTheme("light");
            } else {
                p.setTheme(body.theme());
            }
        }
        if (body.goalMinutes() != null) {
            int goal = body.goalMinutes();
            p.setGoalMinutes(Math.min(960, Math.max(10, goal)));
        }
        p.setUpdatedAt(Instant.now());
        return prefsRepository.save(p);
    }

    private static String prefsKey() {
        String username = CurrentUser.usernameOrNull();
        return username == null || username.isBlank() ? PrefsEntity.GUEST_KEY : username.toLowerCase();
    }
}