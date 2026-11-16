package com.company.vapt.audit.controller;

import com.company.vapt.audit.domain.AuditEvent;
import com.company.vapt.audit.repository.AuditEventRepository;
import com.company.vapt.common.response.ApiResponse;
import com.company.vapt.common.response.PageResponse;
import com.company.vapt.common.logging.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/audit/events")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {
    private final AuditEventRepository events;
    public AuditController(AuditEventRepository events) { this.events=events; }
    @GetMapping public ResponseEntity<ApiResponse<PageResponse<AuditEvent>>> list(
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,
            @RequestParam(required=false) String action) {
        var pageable=PageRequest.of(Math.max(0,page),Math.max(1,Math.min(100,size)),Sort.by("timestamp").descending());
        var result=action==null||action.isBlank()?events.findAllByOrderByTimestampDesc(pageable):events.findByActionIgnoreCaseOrderByTimestampDesc(action.trim(),pageable);
        String correlation=MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(result),correlation==null?"unknown":correlation));
    }
}
