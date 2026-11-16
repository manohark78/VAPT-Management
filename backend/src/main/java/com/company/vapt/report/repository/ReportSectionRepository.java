package com.company.vapt.report.repository;

import com.company.vapt.report.entity.ReportSection;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportSectionRepository extends JpaRepository<ReportSection, String> {

    List<ReportSection> findByEnabledTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ReportSection r WHERE r.id = :id")
    Optional<ReportSection> findByIdWithPessimisticLock(@Param("id") String id);
}
