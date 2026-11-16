package com.company.vapt.audit.service;

import com.company.vapt.audit.domain.AuditEvent;
import com.company.vapt.audit.repository.AuditEventRepository;
import com.company.vapt.common.logging.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actorId, String actorType, String action, String targetType, String targetId, String details) {
        persist(actorId, actorType, action, targetType, targetId, details);
    }

    /**
     * Writes an audit event in the caller's workflow transaction. If persistence
     * fails, the caller's business transaction must roll back as well.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordInWorkflowTransaction(String actorId, String actorType, String action,
                                            String targetType, String targetId, String details) {
        try {
            persist(actorId, actorType, action, targetType, targetId, details);
        } catch (RuntimeException ignored) {
            String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
            log.error("Required audit persistence failed: action={} correlationId={}",
                    action, correlationId != null ? correlationId : "SYSTEM");
            // Do not attach the persistence exception: database exceptions may include
            // bound data, and the caller already receives an actionable failure.
            throw new AuditPersistenceException();
        }
    }

    private void persist(String actorId, String actorType, String action, String targetType, String targetId, String details) {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        if (correlationId == null) {
            correlationId = "SYSTEM";
        }

        AuditEvent event = new AuditEvent(
                actorId,
                actorType != null ? actorType : "SYSTEM",
                action,
                targetType,
                targetId,
                correlationId,
                details
        );

        auditEventRepository.save(event);
    }
}
