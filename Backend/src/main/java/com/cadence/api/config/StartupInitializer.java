package com.cadence.api.config;

import com.cadence.api.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs once at startup: seeds the default admin account when the
 * users table is empty (admin / admin-change-me by default).
 */
@Component
public class StartupInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupInitializer.class);

    private final UserService userService;

    public StartupInitializer(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        userService.ensureDefaultAdmin();
        log.info("Cadence API ready - default admin available at POST /api/auth/login");
    }
}