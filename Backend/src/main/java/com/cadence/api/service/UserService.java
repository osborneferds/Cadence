package com.cadence.api.service;

import com.cadence.api.model.UserEntity;
import com.cadence.api.repository.UserRepository;
import com.cadence.api.security.JwtService;
import com.cadence.api.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final String defaultAdminUsername;
    private final String defaultAdminPassword;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuditService auditService,
                       @Value("${app.security.admin-username:admin}") String defaultAdminUsername,
                       @Value("${app.security.admin-password:admin-change-me}") String defaultAdminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.defaultAdminUsername = defaultAdminUsername == null ? "admin" : defaultAdminUsername.trim().toLowerCase();
        this.defaultAdminPassword = defaultAdminPassword == null ? "admin-change-me" : defaultAdminPassword;
    }

    /** Creates the default admin account on first boot (only when no users exist). */
    @Transactional
    public void ensureDefaultAdmin() {
        if (userRepository.count() > 0) {
            return;
        }
        UserEntity admin = new UserEntity();
        admin.setUsername(defaultAdminUsername);
        admin.setPasswordHash(passwordEncoder.encode(defaultAdminPassword));
        admin.setRole("ADMIN");
        admin.setActive(true);
        admin.setCreatedAt(Instant.now());
        userRepository.save(admin);
    }

    @Transactional
    public Map<String, Object> login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw ApiException.badRequest("Enter username and password");
        }
        UserEntity user = userRepository.findByUsernameIgnoreCase(username.trim()).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            auditService.record("LOGIN_FAILED", "user=" + username.trim());
            throw ApiException.unauthorized("Invalid username or password");
        }
        if (!user.isActive()) {
            throw ApiException.forbidden("Account is deactivated - contact an administrator");
        }
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        auditService.record("LOGIN", "role=" + user.getRole());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", jwtService.issue(user.getUsername(), user.getRole()));
        out.put("username", user.getUsername());
        out.put("role", user.getRole());
        return out;
    }

    @Transactional
    public void changeOwnPassword(String username, String currentPassword, String newPassword) {
        UserEntity user = requireActive(username);
        if (currentPassword == null || currentPassword.isEmpty()
                || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        setPassword(user, newPassword);
        auditService.record("PASSWORD_CHANGE", null);
    }

    // ── Admin operations ────────────────────────────────────────────

    public List<UserEntity> listAll() {
        return userRepository.findAll();
    }

    @Transactional
    public UserEntity create(String username, String password, String role) {
        if (username == null || username.isBlank()) {
            throw ApiException.badRequest("Username is required");
        }
        String normalized = username.trim().toLowerCase();
        if (!normalized.matches("[a-z0-9._-]{2,40}")) {
            throw ApiException.badRequest("Username may only contain letters, digits, dot, dash and underscore");
        }
        if (userRepository.existsByUsernameIgnoreCase(normalized)) {
            throw ApiException.badRequest("Username already exists");
        }
        if (role == null || !(role.equals("USER") || role.equals("ADMIN"))) {
            throw ApiException.badRequest("Role must be USER or ADMIN");
        }
        UserEntity user = new UserEntity();
        user.setUsername(normalized);
        setPassword(user, password);
        user.setRole(role);
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        UserEntity saved = userRepository.save(user);
        auditService.record("USER_CREATE", saved.getUsername() + " role=" + role);
        return saved;
    }

    @Transactional
    public UserEntity updateRole(Long id, String newRole) {
        UserEntity user = requireById(id);
        if (newRole == null || !(newRole.equals("USER") || newRole.equals("ADMIN"))) {
            throw ApiException.badRequest("Role must be USER or ADMIN");
        }
        if (user.getId().equals(idOfCurrentUser()) && !newRole.equals("ADMIN")) {
            throw ApiException.badRequest("You cannot demote your own account");
        }
        user.setRole(newRole);
        auditService.record("USER_UPDATE_ROLE", user.getUsername() + " -> " + newRole);
        return userRepository.save(user);
    }

    @Transactional
    public UserEntity updateActive(Long id, boolean active) {
        UserEntity user = requireById(id);
        if (!active && user.getId().equals(idOfCurrentUser())) {
            throw ApiException.badRequest("You cannot deactivate your own account");
        }
        long activeCount = userRepository.findAll().stream().filter(UserEntity::isActive).count();
        if (!active && user.isActive() && activeCount <= 1) {
            throw ApiException.badRequest("Cannot deactivate the last active account");
        }
        user.setActive(active);
        auditService.record("USER_UPDATE_ACTIVE", user.getUsername() + " -> " + (active ? "active" : "inactive"));
        return userRepository.save(user);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        UserEntity user = requireById(id);
        setPassword(user, newPassword);
        auditService.record("USER_PASSWORD_RESET", user.getUsername());
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private void setPassword(UserEntity user, String raw) {
        if (raw == null || raw.length() < 6) {
            throw ApiException.badRequest("Password must be at least 6 characters");
        }
        user.setPasswordHash(passwordEncoder.encode(raw));
    }

    private UserEntity requireActive(String username) {
        if (username == null || username.isBlank()) {
            throw ApiException.unauthorized("Not signed in");
        }
        UserEntity user = userRepository.findByUsernameIgnoreCase(username).orElse(null);
        if (user == null || !user.isActive()) {
            throw ApiException.unauthorized("Account not found or inactive");
        }
        return user;
    }

    private UserEntity requireById(Long id) {
        if (id == null) {
            throw ApiException.notFound("User not found");
        }
        return userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private Long idOfCurrentUser() {
        String name = CurrentUser.usernameOrNull();
        if (name == null) {
            return null;
        }
        return userRepository.findByUsernameIgnoreCase(name).map(UserEntity::getId).orElse(null);
    }
}