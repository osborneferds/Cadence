package com.cadence.api.controller;

import com.cadence.api.service.CurrentUser;
import com.cadence.api.service.UserService;
import com.cadence.api.web.ApiException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    public record LoginRequest(String username, String password) {}

    public record PasswordRequest(String currentPassword, String newPassword) {}

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest body) {
        return userService.login(body.username(), body.password());
    }

    @PostMapping("/password")
    public Map<String, Object> changePassword(@RequestBody PasswordRequest body) {
        String username = CurrentUser.usernameOrNull();
        if (username == null) {
            throw ApiException.unauthorized("Sign in before changing your password");
        }
        userService.changeOwnPassword(username, body.currentPassword(), body.newPassword());
        return Map.of("ok", true);
    }
}