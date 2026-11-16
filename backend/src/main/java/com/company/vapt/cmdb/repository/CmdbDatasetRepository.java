package com.company.vapt.cmdb.repository;

import com.company.vapt.cmdb.entity.CmdbDataset;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface CmdbDatasetRepository extends JpaRepository<CmdbDataset,String> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select d from CmdbDataset d where d.datasetKey=:key") Optional<CmdbDataset> lockByKey(@Param("key") String key);
}
