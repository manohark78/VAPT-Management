package com.company.vapt.cmdb.repository;

import com.company.vapt.cmdb.entity.CmdbRecordFieldProfile;
import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;import java.util.*;import java.util.UUID;
public interface CmdbRecordFieldProfileRepository extends JpaRepository<CmdbRecordFieldProfile,UUID>{
 @Modifying @Query("delete from CmdbRecordFieldProfile f where f.versionId=:id") int deleteVersionProfiles(@Param("id")UUID id);
 @Query(value="select field_name,count(distinct value_hash) from cmdb_record_field_profile where version_id=:id and blank=false group by field_name",nativeQuery=true) List<Object[]> countDistinctValues(@Param("id")UUID id);
}
