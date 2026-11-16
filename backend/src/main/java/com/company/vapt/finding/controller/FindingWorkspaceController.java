package com.company.vapt.finding.controller;

import com.company.vapt.finding.dto.response.CommentGroupDto;
import com.company.vapt.finding.dto.response.DashboardDto;
import com.company.vapt.finding.dto.response.FindingCommentDto;
import com.company.vapt.finding.dto.response.FindingHistoryItemDto;
import com.company.vapt.finding.dto.response.FindingRcaDto;
import com.company.vapt.finding.dto.response.FindingRowDto;
import com.company.vapt.finding.entity.Finding;

import com.company.vapt.common.response.ApiResponse;
import com.company.vapt.common.response.PageResponse;
import com.company.vapt.common.logging.CorrelationIdFilter;
import com.company.vapt.finding.dto.response.*;
import com.company.vapt.finding.service.FindingWorkspaceService;
import com.company.vapt.finding.service.FindingWorkflowService;
import com.company.vapt.finding.entity.FindingRemediationAction;
import com.company.vapt.finding.entity.FindingRca;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@PreAuthorize("hasAnyRole('ADMIN','USER')")
public class FindingWorkspaceController {
    private final FindingWorkspaceService workspace; private final FindingWorkflowService workflow;
    public FindingWorkspaceController(FindingWorkspaceService workspace, FindingWorkflowService workflow) { this.workspace=workspace; this.workflow=workflow; }

    @GetMapping("/api/reports/sections/{sectionId}/dashboard")
    public ResponseEntity<ApiResponse<DashboardDto>> dashboard(@PathVariable String sectionId,Authentication auth,
            @RequestParam(required=false) String application,@RequestParam(required=false) String appTag,
            @RequestParam(required=false) String riskRating,@RequestParam(required=false) String compliance,
            @RequestParam(required=false) String environment) {
        return ResponseEntity.ok(ApiResponse.ok(workspace.dashboard(sectionId,auth,application,appTag,riskRating,compliance,environment),correlation()));
    }
    @GetMapping("/api/reports/sections/{sectionId}/data")
    public ResponseEntity<ApiResponse<PageResponse<FindingRowDto>>> data(@PathVariable String sectionId,Authentication auth,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,
            @RequestParam(defaultValue="observedAt") String sort,@RequestParam(defaultValue="desc") String direction,
            @RequestParam(required=false) String ip,@RequestParam(required=false) String application,
            @RequestParam(required=false) String appTag,@RequestParam(required=false) String riskRating,
            @RequestParam(required=false) String compliance,@RequestParam(required=false) String environment,
            @RequestParam(required=false) String query) {
        return ResponseEntity.ok(ApiResponse.ok(workspace.data(sectionId,auth,page,Math.max(1,Math.min(100,size)),sort,direction,ip,application,appTag,riskRating,compliance,environment,query),correlation()));
    }
    @GetMapping("/api/reports/sections/{sectionId}/history")
    public ResponseEntity<ApiResponse<PageResponse<FindingRowDto>>> history(@PathVariable String sectionId,Authentication auth,
            @RequestParam(required=false) Instant asOf,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {
        return ResponseEntity.ok(ApiResponse.ok(workspace.history(sectionId,auth,asOf,page,size),correlation()));
    }
    @GetMapping("/api/reports/sections/{sectionId}/comments")
    public ResponseEntity<ApiResponse<PageResponse<FindingCommentDto>>> comments(@PathVariable String sectionId,Authentication auth,
            @RequestParam String ip,@RequestParam String application,@RequestParam String appTag,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {
        return ResponseEntity.ok(ApiResponse.ok(workflow.comments(sectionId,ip,application,appTag,auth,page,size),correlation()));
    }
    @GetMapping("/api/reports/sections/{sectionId}/comments/groups")
    public ResponseEntity<ApiResponse<PageResponse<CommentGroupDto>>> commentGroups(@PathVariable String sectionId,Authentication auth,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {
        return ResponseEntity.ok(ApiResponse.ok(workspace.commentGroups(sectionId,auth,page,size),correlation()));
    }
    @PostMapping("/api/reports/sections/{sectionId}/comments")
    public ResponseEntity<ApiResponse<FindingCommentDto>> addComment(@PathVariable String sectionId,Authentication auth,@Valid @RequestBody CommentRequest request) {
        var saved=workflow.addComment(sectionId,request.ipAddress(),request.applicationName(),request.appTag(),request.findingId(),request.text(),auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(saved,correlation()));
    }
    @PostMapping("/api/findings/{findingId}/remediation")
    public ResponseEntity<ApiResponse<RemediationDto>> remediation(@PathVariable UUID findingId,Authentication auth,@Valid @RequestBody RemediationRequest request) {
        FindingRemediationAction a=workflow.updateRemediation(findingId,request.action(),request.selectedDate(),auth);
        return ResponseEntity.ok(ApiResponse.ok(new RemediationDto(a.getId(),findingId,a.getActionType(),a.getSelectedDate(),a.getActorUserId(),a.getCreatedAt()),correlation()));
    }
    @GetMapping("/api/findings/{findingId}/history")
    public ResponseEntity<ApiResponse<List<FindingHistoryItemDto>>> findingHistory(@PathVariable UUID findingId,Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(workflow.history(findingId,auth),correlation()));
    }
    @PostMapping("/api/findings/{findingId}/rca")
    public ResponseEntity<ApiResponse<FindingRcaDto>> submitRca(@PathVariable UUID findingId,Authentication auth,@Valid @RequestBody RcaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(workflow.submitRca(findingId,request.narrative(),auth),correlation()));
    }
    @PostMapping("/api/admin/findings/{findingId}/rca/{rcaId}/review")
    public ResponseEntity<ApiResponse<FindingRcaDto>> reviewRca(@PathVariable UUID findingId,@PathVariable UUID rcaId,Authentication auth,@Valid @RequestBody RcaReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(workflow.reviewRca(findingId,rcaId,request.approve(),request.note(),auth),correlation()));
    }
    @PostMapping("/api/admin/findings/{findingId}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> closeMissing(@PathVariable UUID findingId,Authentication auth,@Valid @RequestBody CloseRequest request) {
        workflow.closeMissing(findingId,request.reason(),auth);
        return ResponseEntity.ok(ApiResponse.ok("CLOSED",correlation()));
    }

    private String correlation(){String id=MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);return id==null?"unknown":id;}
    public record CommentRequest(@NotBlank String ipAddress,@NotBlank String applicationName,@NotBlank String appTag,UUID findingId,@NotBlank @Size(max=10000) String text) { }
    public record RemediationRequest(@NotBlank String action,LocalDate selectedDate) { }
    public record RcaRequest(@NotBlank @Size(max=10000) String narrative) { }
    public record RcaReviewRequest(boolean approve,@Size(max=1000) String note) { }
    public record CloseRequest(@NotBlank @Size(max=500) String reason) { }
    public record RemediationDto(UUID id,UUID findingId,String action,LocalDate selectedDate,String actorUserId,Instant createdAt) { }
}
