package com.cadence.api.controller;

import com.cadence.api.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReportsController {

    private final StatsService statsService;

    public ReportsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/report/weekly")
    public Map<String, Object> weeklyReport() {
        return statsService.weeklyReport();
    }

    @GetMapping("/export")
    public Map<String, Object> export() {
        return statsService.exportData();
    }
}