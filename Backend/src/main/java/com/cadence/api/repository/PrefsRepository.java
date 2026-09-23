package com.cadence.api.repository;

import com.cadence.api.model.PrefsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrefsRepository extends JpaRepository<PrefsEntity, String> {
}