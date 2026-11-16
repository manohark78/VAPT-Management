package com.company.vapt.cmdb.repository;

import com.company.vapt.cmdb.entity.CmdbVersion;
import org.springframework.data.domain.Page; import org.springframework.data.domain.Pageable; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface CmdbVersionRepository extends JpaRepository<CmdbVersion,UUID> {
 Page<CmdbVersion> findByDatasetKeyOrderByCreatedAtDesc(String datasetKey, Pageable pageable);
 Optional<CmdbVersion> findByIdAndDatasetKey(UUID id,String datasetKey);
 Optional<CmdbVersion> findByIdAndStatus(UUID id,String status);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select v from CmdbVersion v where v.id = :id")
 Optional<CmdbVersion> lockById(@org.springframework.data.repository.query.Param("id") UUID id);
}
