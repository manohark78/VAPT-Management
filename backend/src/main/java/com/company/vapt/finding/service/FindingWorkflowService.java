package com.company.vapt.finding.service;

import com.company.vapt.finding.dto.response.FindingCommentDto;
import com.company.vapt.finding.dto.response.FindingHistoryItemDto;
import com.company.vapt.finding.dto.response.FindingRcaDto;
import com.company.vapt.finding.entity.Finding;
import com.company.vapt.finding.entity.FindingComment;
import com.company.vapt.finding.entity.FindingEvent;
import com.company.vapt.finding.entity.FindingObservation;
import com.company.vapt.finding.entity.FindingRca;
import com.company.vapt.finding.entity.FindingRemediationAction;
import com.company.vapt.finding.repository.FindingCommentRepository;
import com.company.vapt.finding.repository.FindingEventRepository;
import com.company.vapt.finding.repository.FindingObservationRepository;
import com.company.vapt.finding.repository.FindingRcaRepository;
import com.company.vapt.finding.repository.FindingRemediationActionRepository;
import com.company.vapt.finding.repository.FindingRepository;
import com.company.vapt.report.entity.ReportSection;
import com.company.vapt.report.repository.ReportSectionRepository;

import com.company.vapt.audit.service.AuditService;
import com.company.vapt.common.response.PageResponse;
import com.company.vapt.common.exception.ResourceNotFoundException;
import com.company.vapt.common.exception.ValidationException;
import com.company.vapt.finding.dto.response.*;
import com.company.vapt.finding.repository.*;
import com.company.vapt.user.service.UserScopeService;
import com.company.vapt.report.domain.ReportSectionId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class FindingWorkflowService {
    private final FindingWorkspaceService workspace; private final FindingRepository findings;
    private final FindingObservationRepository observations; private final FindingEventRepository events;
    private final FindingCommentRepository comments; private final FindingRemediationActionRepository actions;
    private final FindingRcaRepository rcas; private final ReportSectionRepository sections;
    private final UserScopeService scopes; private final AuditService audit;
    public FindingWorkflowService(FindingWorkspaceService workspace, FindingRepository findings,
            FindingObservationRepository observations, FindingEventRepository events, FindingCommentRepository comments,
            FindingRemediationActionRepository actions, FindingRcaRepository rcas, ReportSectionRepository sections,
            UserScopeService scopes, AuditService audit) {
        this.workspace=workspace; this.findings=findings; this.observations=observations; this.events=events;
        this.comments=comments; this.actions=actions; this.rcas=rcas; this.sections=sections; this.scopes=scopes; this.audit=audit;
    }

    @Transactional(readOnly=true)
    public PageResponse<FindingCommentDto> comments(String sectionId,String ip,String application,String tag,Authentication auth,int page,int size) {
        FindingWorkspaceService.UserContext actor=workspace.context(auth); validateContext(sectionId,ip,application,tag,actor);
        Page<FindingCommentDto> result=comments.findByReportSectionIdAndIpAddressIgnoreCaseAndApplicationNameIgnoreCaseAndAppTagIgnoreCaseOrderByCreatedAtDesc(
                sectionId,ip,application,tag,PageRequest.of(Math.max(0,page),Math.max(1,Math.min(100,size)),Sort.by("createdAt").descending()))
                .map(FindingCommentDto::from);
        return PageResponse.from(result);
    }

    @Transactional
    public FindingCommentDto addComment(String sectionId,String ip,String application,String tag,UUID findingId,String text,Authentication auth) {
        FindingWorkspaceService.UserContext actor=workspace.context(auth); validateContext(sectionId,ip,application,tag,actor);
        if(text==null||text.isBlank()||text.length()>10000) throw new ValidationException("Comment must contain 1 to 10000 characters");
        Finding finding=null;
        if(findingId!=null) {
            finding=authorizedFinding(findingId,actor);
            FindingObservation latest=observations.findFirstByFindingIdOrderByObservedAtDesc(findingId).orElseThrow();
            if(!sectionId.equals(latest.getReportSection().getId())||!same(ip,latest.getIpAddress())||!same(application,latest.getApplicationName())||!same(tag,latest.getAppTag()))
                throw new ResourceNotFoundException("Finding",findingId.toString());
        }
        var section=sections.findById(sectionId).orElseThrow(()->new ResourceNotFoundException("ReportSection",sectionId));
        FindingComment saved=comments.save(new FindingComment(UUID.randomUUID(),section,finding,ip,application,tag,text,actor.actorId(),Instant.now()));
        audit.recordInWorkflowTransaction(actor.actorId(),actor.admin()?"ADMIN":"USER","FINDING_COMMENT_CREATED","FINDING_COMMENT",saved.getId().toString(),"section="+sectionId+";findingId="+(findingId==null?"GROUP":findingId));
        return FindingCommentDto.from(saved);
    }

    @Transactional
    public FindingRemediationAction updateRemediation(UUID findingId,String action,LocalDate date,Authentication auth) {
        FindingWorkspaceService.UserContext actor=workspace.context(auth); Finding finding=authorizedFinding(findingId,actor);
        Set<String> allowed=Set.of("FALSE_POSITIVE_RAISED","FALSE_POSITIVE_APPROVED","REMEDIATION_DATE","ALREADY_REMEDIATED");
        if(!allowed.contains(action)) throw new ValidationException("Unknown remediation action");
        if("FALSE_POSITIVE_APPROVED".equals(action)&&!actor.admin()) throw new org.springframework.security.access.AccessDeniedException("Administrator approval is required");
        if("REMEDIATION_DATE".equals(action)!=(date!=null)) throw new ValidationException("Choose a date only for the Remediation Date action");
        FindingRemediationAction saved=actions.save(new FindingRemediationAction(UUID.randomUUID(),finding,action,date,actor.actorId(),Instant.now()));
        audit.recordInWorkflowTransaction(actor.actorId(),actor.admin()?"ADMIN":"USER","FINDING_REMEDIATION_UPDATED","FINDING",findingId.toString(),"action="+action+";selectedDate="+(date==null?"":date));
        return saved;
    }

    @Transactional(readOnly=true)
    public List<FindingHistoryItemDto> history(UUID findingId,Authentication auth) {
        FindingWorkspaceService.UserContext actor=workspace.context(auth); authorizedFinding(findingId,actor);
        List<FindingHistoryItemDto> items=new ArrayList<>();
        for(FindingObservation o:observations.findByFindingIdOrderByObservedAtDesc(findingId)) items.add(new FindingHistoryItemDto("OBSERVATION",o.getClassification(),null,"batch="+o.getImportBatch().getId(),null,o.getObservedAt(),o.getFinding().getLifecycleState()));
        for(FindingEvent e:events.findByFindingIdOrderByOccurredAtAsc(findingId)) items.add(new FindingHistoryItemDto("LIFECYCLE",e.getEventType(),e.getActorUserId(),e.getDetails(),null,e.getOccurredAt(),null));
        for(FindingRemediationAction a:actions.findByFindingIdOrderByCreatedAtDesc(findingId)) items.add(new FindingHistoryItemDto("REMEDIATION",a.getActionType(),a.getActorUserId(),a.getSelectedDate()==null?null:"selectedDate="+a.getSelectedDate(),null,a.getCreatedAt(),null));
        for(FindingRca r:rcas.findByFindingIdOrderBySubmittedAtDesc(findingId)) items.add(new FindingHistoryItemDto("RCA", "SUBMITTED", r.getSubmittedByUserId(), r.getNarrative(), r.getId(),r.getSubmittedAt(), r.getStatus()));
        items.sort(Comparator.comparing(FindingHistoryItemDto::at)); return List.copyOf(items);
    }

    @Transactional
    public FindingRcaDto submitRca(UUID findingId,String narrative,Authentication auth) {
        FindingWorkspaceService.UserContext actor=workspace.context(auth); Finding finding=authorizedFinding(findingId,actor);
        if(narrative==null||narrative.isBlank()||narrative.length()>10000) throw new ValidationException("RCA narrative must contain 1 to 10000 characters");
        FindingEvent reopen=events.findByFindingIdAndEventTypeOrderByOccurredAtDesc(findingId,"REOPENED").stream().findFirst().orElseThrow(()->new ValidationException("RCA is only accepted for a reopened finding"));
        List<FindingRca> prior=rcas.findByFindingIdOrderBySubmittedAtDesc(findingId).stream().filter(r->r.getReopenEvent().getId().equals(reopen.getId())).toList();
        if(!prior.isEmpty()&&Set.of("PENDING","APPROVED").contains(prior.getFirst().getStatus())) throw new ValidationException("RCA has already been submitted for this reopen event");
        FindingRca saved=rcas.save(new FindingRca(UUID.randomUUID(),finding,reopen,narrative,actor.actorId(),Instant.now()));
        events.save(new FindingEvent(UUID.randomUUID(),finding,reopen.getImportBatch(),reopen.getSourceRecord(),"RCA_SUBMITTED",Instant.now(),actor.actorId(),"RCA submitted"));
        audit.recordInWorkflowTransaction(actor.actorId(),actor.admin()?"ADMIN":"USER","FINDING_RCA_SUBMITTED","FINDING",findingId.toString(),"rcaId="+saved.getId());
        return FindingRcaDto.from(saved);
    }

    @Transactional
    public FindingRcaDto reviewRca(UUID findingId,UUID rcaId,boolean approve,String note,Authentication auth) {
        FindingWorkspaceService.UserContext actor=workspace.context(auth); if(!actor.admin()) throw new org.springframework.security.access.AccessDeniedException("Administrator review is required");
        Finding finding=authorizedFinding(findingId,actor); FindingRca rca=rcas.findById(rcaId).filter(r->r.getFinding().getId().equals(findingId)).orElseThrow(()->new ResourceNotFoundException("FindingRca",rcaId.toString()));
        if(!"PENDING".equals(rca.getStatus())) throw new ValidationException("Only pending RCA submissions can be reviewed");
        if(note!=null&&note.length()>1000) throw new ValidationException("Review note exceeds the supported length");
        rca.review(actor.actorId(),approve,note,Instant.now());
        events.save(new FindingEvent(UUID.randomUUID(),finding,rca.getReopenEvent().getImportBatch(),rca.getReopenEvent().getSourceRecord(),approve?"RCA_APPROVED":"RCA_REJECTED",Instant.now(),actor.actorId(),"RCA review completed"));
        audit.recordInWorkflowTransaction(actor.actorId(),"ADMIN","FINDING_RCA_REVIEWED","FINDING",findingId.toString(),"rcaId="+rcaId+";decision="+(approve?"APPROVED":"REJECTED"));
        return FindingRcaDto.from(rca);
    }

    @Transactional
    public void closeMissing(UUID findingId,String reason,Authentication auth) {
        FindingWorkspaceService.UserContext actor=workspace.context(auth); if(!actor.admin()) throw new org.springframework.security.access.AccessDeniedException("Administrator closure review is required");
        Finding finding=authorizedFinding(findingId,actor);
        FindingObservation missing=observations.findFirstByFindingIdAndClassificationOrderByObservedAtDesc(findingId,"MISSING_FROM_COMPLETE_SNAPSHOT").orElseThrow(()->new ValidationException("Finding is not missing from a complete snapshot"));
        if(!missing.getImportBatch().isCompleteSnapshot()) throw new ValidationException("Only a declared complete snapshot can support this closure");
        if(reason==null||reason.isBlank()||reason.length()>500) throw new ValidationException("Closure reason must contain 1 to 500 characters");
        if("CLOSED".equals(finding.getLifecycleState())) return;
        Instant now=Instant.now(); finding.close(actor.actorId(),reason,now);
        events.save(new FindingEvent(UUID.randomUUID(),finding,missing.getImportBatch(),null,"CLOSED",now,actor.actorId(),"Administrator confirmed absence from complete snapshot"));
        audit.recordInWorkflowTransaction(actor.actorId(),"ADMIN","FINDING_CLOSED","FINDING",findingId.toString(),"batchId="+missing.getImportBatch().getId()+";reason="+reason);
    }

    private Finding authorizedFinding(UUID id,FindingWorkspaceService.UserContext actor) {
        Finding finding=findings.findById(id).orElseThrow(()->new ResourceNotFoundException("Finding",id.toString()));
        if(!ReportSectionId.APPSEC_FINDINGS_PT.name().equals(finding.getReportSection().getId())) {
            FindingObservation latest=observations.findFirstByFindingIdOrderByObservedAtDesc(id).orElseThrow(()->new ResourceNotFoundException("Finding",id.toString()));
            // Return the same not-found response for unknown and out-of-scope IDs to avoid IDOR probing.
            if(!scopes.hasAccess(actor.userId(),latest.getIpAddress(),latest.getApplicationName()))
                throw new ResourceNotFoundException("Finding",id.toString());
        } else throw new ResourceNotFoundException("Finding",id.toString());
        return finding;
    }
    private void validateContext(String section,String ip,String application,String tag,FindingWorkspaceService.UserContext actor) {
        if(!Set.of("GDP_VA_REPORT","INTERNAL_VA_BRINQA","WIZ","VERIZON_26_NETWORK_PT","PT_VERIZON_GCP").contains(section)) throw new ValidationException("Comments are not enabled for this report section");
        if(blank(ip)||blank(application)||blank(tag)) throw new ValidationException("IP, application and AppTag are required");
        if(!observations.existsCurrentContext(section,ip,application,tag,actor.admin(),actor.userId())) throw new ResourceNotFoundException("ReportContext",section+"/"+ip+"/"+application+"/"+tag);
    }
    private boolean same(String a,String b){return a!=null&&b!=null&&a.equalsIgnoreCase(b);}
    private boolean blank(String value){return value==null||value.isBlank();}
}
