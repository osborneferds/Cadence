package com.cadence.api.repository;

import com.cadence.api.model.FocusSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface FocusSessionRepository extends JpaRepository<FocusSessionEntity, Long> {

    List<FocusSessionEntity> findByAtGreaterThanEqualOrderByAtDesc(Instant since);

    List<FocusSessionEntity> findByProjectId(Long projectId);

    @Modifying
    @Query("update FocusSessionEntity s set s.projectId = null where s.projectId = :projectId")
    void clearProject(@Param("projectId") Long projectId);
}