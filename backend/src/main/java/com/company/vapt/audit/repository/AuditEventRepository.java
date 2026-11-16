package com.company.vapt.audit.repository;

import com.company.vapt.audit.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findByActionOrderByTimestampDesc(String action);
    List<AuditEvent> findByActorIdOrderByTimestampDesc(String actorId);
    Page<AuditEvent> findByActionIgnoreCaseOrderByTimestampDesc(String action, Pageable pageable);
    Page<AuditEvent> findAllByOrderByTimestampDesc(Pageable pageable);
}
