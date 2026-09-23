package com.cadence.api.repository;

import com.cadence.api.model.AuditEntryEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEntryRepository extends JpaRepository<AuditEntryEntity, Long> {

    /** Newest first. */
    List<AuditEntryEntity> findAllByOrderByAtDescIdDesc();

    List<AuditEntryEntity> findAllByOrderByIdAsc(Pageable pageable);
}