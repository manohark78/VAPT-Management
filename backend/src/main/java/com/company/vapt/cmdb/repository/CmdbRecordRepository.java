package com.company.vapt.cmdb.repository;

import com.company.vapt.cmdb.entity.CmdbRecord;
import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.jpa.repository.Modifying; import org.springframework.data.jpa.repository.Query; import org.springframework.data.repository.query.Param; import java.util.UUID;
public interface CmdbRecordRepository extends JpaRepository<CmdbRecord,UUID> {
 @Modifying @Query("delete from CmdbRecord r where r.versionId=:id") int deleteVersionRows(@Param("id") UUID id);
 @Query("select count(r) from CmdbRecord r where r.versionId=:id") long countVersionRows(@Param("id") UUID id);
 java.util.List<CmdbRecord> findByVersionIdOrderBySourceRowNumber(UUID versionId);
 java.util.List<CmdbRecord> findByVersionIdAndSourceRowNumberGreaterThanOrderBySourceRowNumberAsc(UUID versionId, int sourceRowNumber, org.springframework.data.domain.Pageable pageable);
 @Query(value="select count(distinct record_key) from cmdb_record where version_id=:id", nativeQuery=true) long countDistinctKeys(@Param("id") UUID id);
}
