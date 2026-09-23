package com.cadence.api.service;

import com.cadence.api.model.AuditEntryEntity;
import com.cadence.api.repository.AuditEntryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Append-only activity log, capped at app.audit.max-entries entries.
 */
@Service
public class AuditService {

    private final AuditEntryRepository auditEntryRepository;
    private final int maxEntries;

    public AuditService(AuditEntryRepository auditEntryRepository,
                        @Value("${app.audit.max-entries:500}") int maxEntries) {
        this.auditEntryRepository = auditEntryRepository;
        this.maxEntries = Math.max(50, maxEntries);
    }

    @Transactional
    public void record(String action, String detail) {
        AuditEntryEntity entry = new AuditEntryEntity();
        entry.setAt(Instant.now());
        entry.setUsername(CurrentUser.usernameOrNull());
        entry.setAction(action == null ? "UNKNOWN" : action);
        entry.setDetail(detail != null && detail.length() > 500 ? detail.substring(0, 500) : detail);
        auditEntryRepository.save(entry);

        long total = auditEntryRepository.count();
        if (total > maxEntries) {
            int toDelete = (int) (total - maxEntries);
            Pageable oldest = PageRequest.of(0, toDelete);
            List<AuditEntryEntity> stale = auditEntryRepository.findAllByOrderByIdAsc(oldest);
            auditEntryRepository.deleteAllInBatch(stale);
        }
    }

    /** Newest first. */
    public List<AuditEntryEntity> listNewestFirst() {
        return auditEntryRepository.findAllByOrderByAtDescIdDesc();
    }

    public long count() {
        return auditEntryRepository.count();
    }
}