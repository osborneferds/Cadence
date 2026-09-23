package com.cadence.api.repository;

import com.cadence.api.dto.AudioLogView;
import com.cadence.api.model.AudioLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AudioLogRepository extends JpaRepository<AudioLogEntity, Long> {

    /**
     * List metadata only - never loads the heavy audio blobs.
     */
    @Query("""
            select new com.cadence.api.dto.AudioLogView(
                a.id, a.projectId, a.note, a.durationSec, a.mimeType, a.sizeBytes, a.at)
            from AudioLogEntity a
            order by a.at desc
            """)
    List<AudioLogView> findAllViews();

    @Modifying
    @Query("update AudioLogEntity a set a.projectId = null where a.projectId = :projectId")
    void clearProject(@Param("projectId") Long projectId);
}