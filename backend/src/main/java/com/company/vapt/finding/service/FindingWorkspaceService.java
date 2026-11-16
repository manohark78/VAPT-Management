package com.company.vapt.finding.service;

import com.company.vapt.finding.entity.Finding;
import com.company.vapt.finding.entity.FindingEvent;
import com.company.vapt.finding.entity.FindingObservation;
import com.company.vapt.finding.entity.FindingRemediationAction;
import com.company.vapt.finding.repository.FindingEventRepository;
import com.company.vapt.finding.repository.FindingObservationRepository;
import com.company.vapt.finding.repository.FindingRemediationActionRepository;

import com.company.vapt.common.response.PageResponse;
import com.company.vapt.common.exception.ResourceNotFoundException;
import com.company.vapt.common.exception.ValidationException;
import com.company.vapt.common.observability.PerformanceTimer;
import com.company.vapt.finding.dto.response.DashboardDto;
import com.company.vapt.finding.dto.response.FindingRowDto;
import com.company.vapt.finding.dto.response.CommentGroupDto;
import com.company.vapt.finding.repository.*;
import com.company.vapt.user.domain.AppUser;
import com.company.vapt.user.repository.AppUserRepository;
import com.company.vapt.report.domain.ReportSectionId;
import com.company.vapt.report.domain.ReportSectionRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.*;

@Service
public class FindingWorkspaceService {
    private static final Set<String> SECTIONS = Set.of("GDP_VA_REPORT", "INTERNAL_VA_BRINQA", "WIZ", "VERIZON_26_NETWORK_PT", "PT_VERIZON_GCP");
    private final FindingObservationRepository observations; private final FindingRemediationActionRepository actions; private final FindingEventRepository events;
    private final AppUserRepository users; private final ReportSectionRegistry sections;
    private final PerformanceTimer timer; private final MeterRegistry meters; private final ObjectMapper json=JsonMapper.builder().build();
    public FindingWorkspaceService(FindingObservationRepository observations, FindingRemediationActionRepository actions, FindingEventRepository events,
            AppUserRepository users, ReportSectionRegistry sections, PerformanceTimer timer, MeterRegistry meters) {
        this.observations=observations; this.actions=actions; this.events=events; this.users=users; this.sections=sections; this.timer=timer; this.meters=meters;
    }

    @Transactional(readOnly=true)
    public PageResponse<FindingRowDto> data(String sectionId, Authentication auth, int page, int size,
            String sort, String direction, String ip, String application, String appTag, String riskRating,
            String compliance, String environment, String query) {
        String section=resolve(sectionId); UserContext user=context(auth);
        String property=switch (sort==null?"observedAt":sort) {
            case "riskRating" -> "riskRating"; case "severity" -> "severity"; case "appTag" -> "appTag";
            case "applicationName" -> "applicationName"; case "ipAddress" -> "ipAddress"; case "environment" -> "environment";
            case "sourceRowNumber" -> "sourceRecord.sourceRowNumber"; default -> "observedAt";
        };
        String order="asc".equalsIgnoreCase(direction)?"asc":"desc";
        // Internal callers (notably streaming export) can read larger bounded pages. The HTTP
        // controller separately caps interactive requests at 100 rows.
        Pageable pageable=PageRequest.of(Math.max(0,page),Math.max(1,Math.min(1000,size)));
        long started=System.nanoTime();
        Page<FindingObservation> found=observations.findCurrentPage(section,user.admin,user.userId,blankToNull(ip),blankToNull(application),blankToNull(appTag),blankToNull(riskRating),blankToNull(compliance),blankToNull(environment),blankToNull(query),property,order,pageable);
        List<UUID> ids=found.getContent().stream().filter(o->o.getFinding()!=null).map(o->o.getFinding().getId()).toList();
        Map<UUID,FindingRemediationAction> latest=new HashMap<>();
        if(!ids.isEmpty()) actions.findByFindingIdIn(ids).stream().forEach(a->latest.merge(a.getFinding().getId(),a,(x,y)->x.getCreatedAt().isAfter(y.getCreatedAt())?x:y));
        Page<FindingRowDto> result=found.map(o->{
            FindingRemediationAction action=o.getFinding()==null?null:latest.get(o.getFinding().getId());
            return FindingRowDto.from(o,action==null?null:action.getActionType(),action==null?null:action.getSelectedDate(),sourceValues(o));
        });
        duration("data.search.total",section,"SUCCESS",started); meters.counter("data.search.rows","section",section).increment(result.getNumberOfElements());
        return PageResponse.from(result);
    }

    @Transactional(readOnly=true)
    public DashboardDto dashboard(String sectionId, Authentication auth,String application,String appTag,String riskRating,String compliance,String environment) {
        String section=resolve(sectionId); UserContext user=context(auth); Instant now=Instant.now(); long started=System.nanoTime();
        application=blankToNull(application); appTag=blankToNull(appTag); riskRating=blankToNull(riskRating); compliance=blankToNull(compliance); environment=blankToNull(environment);
        Map<String,Long> risk=aggregate(observations.aggregateRisk(section,user.admin,user.userId,application,appTag,riskRating,compliance,environment));
        Map<String,Long> complianceCounts=aggregate(observations.aggregateCompliance(section,user.admin,user.userId,application,appTag,riskRating,compliance,environment));
        Map<String,Long> apps=aggregate(observations.aggregateApplication(section,user.admin,user.userId,application,appTag,riskRating,compliance,environment));
        Map<String,Long> tags=aggregate(observations.aggregateAppTag(section,user.admin,user.userId,application,appTag,riskRating,compliance,environment));
        Map<String,Long> env=aggregate(observations.aggregateEnvironment(section,user.admin,user.userId,application,appTag,riskRating,compliance,environment));
        Map<String,Long> scores=aggregate(observations.aggregateRiskScore(section,user.admin,user.userId,application,appTag,riskRating,compliance,environment));
        Map<String,Long> severity=aggregate(observations.aggregateSeverity(section,user.admin,user.userId,application,appTag,riskRating,compliance,environment));
        Map<String,Long> lifecycle=aggregate(observations.aggregateLifecycle(section,user.admin,user.userId,application,appTag,riskRating,compliance,environment));
        Map<String,Long> remediation=aggregate(actions.aggregateCurrentActions(section,user.admin,user.userId,application,appTag,riskRating,compliance,environment));
        remediation.put("NOT_STARTED",actions.countCurrentWithoutRemediation(section,user.admin,user.userId,application,appTag,riskRating,compliance,environment));
        duration("dashboard.summary.total",section,"SUCCESS",started);
        return new DashboardDto(section,now,lifecycle.getOrDefault("OPEN",0L),lifecycle.getOrDefault("CLOSED",0L),risk,complianceCounts,apps,tags,env,scores,severity,remediation);
    }

    @Transactional(readOnly=true)
    public PageResponse<FindingRowDto> history(String sectionId, Authentication auth, Instant asOf, int page, int size) {
        String section=resolve(sectionId); UserContext user=context(auth); Instant point=asOf==null?Instant.now():asOf;
        Page<FindingObservation> found=observations.findLatestAsOf(section,point,user.admin,user.userId,PageRequest.of(Math.max(0,page),Math.max(1,Math.min(100,size)),Sort.by("observedAt").descending().and(Sort.by("id").ascending())));
        List<UUID> ids=found.getContent().stream().filter(o->o.getFinding()!=null).map(o->o.getFinding().getId()).toList();
        Map<UUID,FindingRemediationAction> latest=new HashMap<>();
        if(!ids.isEmpty()) actions.findByFindingIdIn(ids).stream().filter(a->!a.getCreatedAt().isAfter(point)).forEach(a->latest.merge(a.getFinding().getId(),a,(x,y)->x.getCreatedAt().isAfter(y.getCreatedAt())?x:y));
        Map<UUID,FindingEvent> lifecycleEvents=new HashMap<>();
        if(!ids.isEmpty()) events.findByFindingIdInAndEventTypeIn(ids,List.of("CLOSED","REOPENED")).stream()
                .filter(e->!e.getOccurredAt().isAfter(point))
                .forEach(e->lifecycleEvents.merge(e.getFinding().getId(),e,
                        (previous,next)->previous.getOccurredAt().isAfter(next.getOccurredAt())?previous:next));
        return PageResponse.from(found.map(o->{ FindingRemediationAction a=o.getFinding()==null?null:latest.get(o.getFinding().getId());
            FindingRowDto row=FindingRowDto.from(o,a==null?null:a.getActionType(),a==null?null:a.getSelectedDate(),sourceValues(o));
            FindingEvent event=o.getFinding()==null?null:lifecycleEvents.get(o.getFinding().getId());
            return o.getFinding()==null?row:row.withLifecycleState(event!=null&&"CLOSED".equals(event.getEventType())?"CLOSED":"OPEN"); }));
    }

    @Transactional(readOnly=true)
    public PageResponse<CommentGroupDto> commentGroups(String sectionId,Authentication auth,int page,int size) {
        String section=resolve(sectionId); UserContext user=context(auth);
        Page<Object[]> rows=observations.commentGroups(section,user.admin,user.userId,PageRequest.of(Math.max(0,page),Math.max(1,Math.min(100,size))));
        return PageResponse.from(rows.map(r->new CommentGroupDto(Objects.toString(r[0],""),Objects.toString(r[1],""),Objects.toString(r[2],""),
                number(r[3]),number(r[4]),number(r[5]),number(r[6]),number(r[7]))));
    }

    public UserContext context(Authentication auth) {
        if(auth==null || auth.getName()==null) throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        AppUser user=users.findByEmail(auth.getName().trim().toLowerCase(Locale.ROOT)).orElseThrow(()->new ResourceNotFoundException("User",auth.getName()));
        boolean admin="ADMIN".equalsIgnoreCase(user.getRole());
        return new UserContext(user.getId(),user.getId().toString(),admin);
    }
    private String resolve(String sectionId) { String id=sections.resolve(sectionId).name(); if(!SECTIONS.contains(id)) throw new ValidationException("AppSec Findings PT is excluded from this UID-based workspace"); return id; }
    private String blankToNull(String value) { return value==null||value.isBlank()?null:value.trim(); }
    private Map<String,Long> aggregate(List<Object[]> rows) { Map<String,Long> values=new LinkedHashMap<>(); for(Object[] row:rows) values.put(row[0]==null?"UNSPECIFIED":row[0].toString(),((Number)row[1]).longValue()); return values; }
    private long number(Object value){return value==null?0:((Number)value).longValue();}
    private Map<String,String> sourceValues(FindingObservation o) {
        if(o.getSourceValues()==null) return Map.of();
        try { return json.readValue(o.getSourceValues(),json.getTypeFactory().constructMapType(Map.class,String.class,String.class)); }
        catch(Exception ignored) { return Map.of(); }
    }
    private void duration(String name,String section,String result,long started) { timer.recordDuration(name,section,result,java.time.Duration.ofNanos(Math.max(0,System.nanoTime()-started))); }
    public record UserContext(Long userId,String actorId,boolean admin) { }
}
