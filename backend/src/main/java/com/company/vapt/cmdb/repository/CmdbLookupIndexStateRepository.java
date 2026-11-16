package com.company.vapt.cmdb.repository;

import com.company.vapt.cmdb.entity.CmdbLookupIndexState;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CmdbLookupIndexStateRepository extends JpaRepository<CmdbLookupIndexState, UUID> { }
