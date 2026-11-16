package com.company.vapt.cmdb.repository;

import com.company.vapt.cmdb.entity.CmdbRecordLookup;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CmdbRecordLookupRepository extends JpaRepository<CmdbRecordLookup, UUID> {
    @Query("select l.valueHash, count(l) from CmdbRecordLookup l " +
            "where l.versionId = :versionId and l.fieldName = :fieldName and l.valueHash in :hashes " +
            "group by l.valueHash")
    List<Object[]> countByHashes(@Param("versionId") UUID versionId,
                                 @Param("fieldName") String fieldName,
                                 @Param("hashes") Collection<String> hashes);

    @Query(value = "select id as id, record_id as recordId, value_hash as valueHash, " +
            "source_row_number as sourceRowNumber, cast(normalized_value as varchar) as normalizedValue " +
            "from (select l.id, l.record_id, l.value_hash, l.source_row_number, l.normalized_value, " +
            "row_number() over (partition by l.value_hash order by l.source_row_number) as rn " +
            "from cmdb_record_lookup l where l.version_id = :versionId and l.field_name = :fieldName " +
            "and l.value_hash in (:hashes)) candidates where rn <= :candidateLimit",
            nativeQuery = true)
    List<Object[]> candidateSamples(@Param("versionId") UUID versionId,
                                    @Param("fieldName") String fieldName,
                                    @Param("hashes") Collection<String> hashes,
                                    @Param("candidateLimit") int candidateLimit);

    @Query("select count(l) from CmdbRecordLookup l where l.versionId = :versionId")
    long countByVersionId(@Param("versionId") UUID versionId);

    @Query("delete from CmdbRecordLookup l where l.versionId = :versionId")
    @org.springframework.data.jpa.repository.Modifying
    int deleteByVersionId(@Param("versionId") UUID versionId);

}
