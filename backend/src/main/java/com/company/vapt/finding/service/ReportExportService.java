package com.company.vapt.finding.service;

import com.company.vapt.finding.dto.response.DashboardDto;
import com.company.vapt.finding.dto.response.FindingRowDto;
import com.company.vapt.finding.entity.Finding;
import com.company.vapt.finding.repository.FindingCommentRepository;
import com.company.vapt.report.domain.ReportSectionId;

import com.company.vapt.audit.service.AuditService;
import com.company.vapt.common.response.PageResponse;
import com.company.vapt.common.observability.PerformanceTimer;
import com.company.vapt.finding.dto.response.*;
import com.company.vapt.finding.entity.FindingComment;
import com.company.vapt.report.domain.ReportSectionRegistry;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.io.OutputStream;
import java.util.*;

@Service
public class ReportExportService {
    private static final int CHUNK=1000;
    private final FindingWorkspaceService workspace; private final FindingCommentRepository comments;
    private final AuditService audit; private final PerformanceTimer timer; private final ReportSectionRegistry sections;
    public ReportExportService(FindingWorkspaceService workspace,FindingCommentRepository comments,AuditService audit,PerformanceTimer timer,ReportSectionRegistry sections) {
        this.workspace=workspace; this.comments=comments; this.audit=audit; this.timer=timer; this.sections=sections;
    }

    public void writeExcel(String sectionId,Authentication auth,Filters filter,OutputStream output) throws java.io.IOException {
        String section=sections.resolve(sectionId).name(); var actor=workspace.context(auth); long started=System.nanoTime(); long rows=0;
        try(SXSSFWorkbook book=new SXSSFWorkbook(100)) {
          try {
            book.setCompressTempFiles(true);
            Sheet pivot=book.createSheet("Pivot"); Sheet commentsSheet=book.createSheet("Comments"); Sheet data=book.createSheet("Data");
            writePivot(pivot,workspace.dashboard(section,auth,filter.application(),filter.appTag(),filter.riskRating(),filter.compliance(),filter.environment()));
            row(commentsSheet,0,"IP Address","Application","AppTag","Finding UID","Comment","Actor User ID","Created At");
            long commentRows=writeComments(section,actor,commentsSheet,filter);
            row(data,0,"UID","Type","Port","Compliance Status","Risk Rating","Risk Score","Severity","Results","Targets","IP Address","Tu_AppName","AppTag","Environment","OwnedBy","Application ID","HeadCount Email","CM4","CM5","CM6","Plan Application Owner 1","Plan Application Owner 2","Lifecycle","Remediation Action","Remediation Date","Source Row","Import Batch","Observed At");
            for(int page=0;;page++) {
                PageResponse<FindingRowDto> result=workspace.data(section,auth,page,CHUNK,"sourceRowNumber","asc",
                        filter.ipAddress(),filter.application(),filter.appTag(),filter.riskRating(),filter.compliance(),filter.environment(),filter.query());
                for(FindingRowDto finding:result.content()) {
                    int r=(int)rows+1;
                    row(data,r,finding.sourceUid(),finding.type(),finding.port(),finding.complianceStatus(),finding.riskRating(),finding.riskScore(),finding.severity(),finding.results(),finding.targets(),finding.ipAddress(),finding.applicationName(),finding.appTag(),finding.environment(),finding.ownedBy(),finding.applicationId(),finding.headcountEmail(),finding.cm4(),finding.cm5(),finding.cm6(),finding.planApplicationOwner1(),finding.planApplicationOwner2(),finding.lifecycleState(),finding.remediationAction(),finding.remediationDate(),finding.sourceRowNumber(),finding.importBatchId(),finding.observedAt());
                    rows++;
                }
                if(result.last()||result.content().isEmpty()) break;
            }
            book.write(output); output.flush();
            audit.recordInWorkflowTransaction(actor.actorId(),actor.admin()?"ADMIN":"USER","REPORT_EXPORT_XLSX","REPORT_SECTION",section,"dataRows="+rows+";commentRows="+commentRows);
            timer.recordDuration("export.xlsx.total",section,"SUCCESS",java.time.Duration.ofNanos(Math.max(0,System.nanoTime()-started)));
          } finally {
            // SXSSF writes worksheet data to temporary files; dispose even when streaming fails.
            book.dispose();
          }
        } catch(java.io.IOException | RuntimeException failure) {
            timer.recordDuration("export.xlsx.total",section,"FAILURE",java.time.Duration.ofNanos(Math.max(0,System.nanoTime()-started)));
            try { audit.record(actor.actorId(),actor.admin()?"ADMIN":"USER","REPORT_EXPORT_XLSX_FAILED","REPORT_SECTION",section,"failureType="+failure.getClass().getSimpleName()); } catch(Exception ignored) { }
            throw failure;
        }
    }

    private long writeComments(String section,FindingWorkspaceService.UserContext actor,Sheet sheet,Filters filter) {
        long rowIndex=1,total=0;
        for(int page=0;;page++) {
            var result=comments.findCurrentAuthorizedFiltered(section,actor.admin(),actor.userId(),blank(filter.ipAddress()),blank(filter.application()),blank(filter.appTag()),PageRequest.of(page,CHUNK,Sort.by("createdAt").descending()));
            for(FindingComment c:result.getContent()) { row(sheet,(int)rowIndex++,c.getIpAddress(),c.getApplicationName(),c.getAppTag(),c.getFinding()==null?null:c.getFinding().getSourceUid(),c.getCommentText(),c.getActorUserId(),c.getCreatedAt()); total++; }
            if(result.isLast()||result.isEmpty()) break;
        }
        return total;
    }
    private void writePivot(Sheet sheet,DashboardDto dashboard) {
        row(sheet,0,"VAPT Report Summary","Section",dashboard.reportSectionId(),"As Of",dashboard.asOf()); int index=2;
        row(sheet,index++,"Lifecycle","Open",dashboard.openFindings(),"Closed",dashboard.closedFindings());
        index=map(sheet,index,"Risk Rating",dashboard.riskRating()); index=map(sheet,index,"Compliance Status",dashboard.complianceStatus());
        index=map(sheet,index,"Application",dashboard.applications()); index=map(sheet,index,"AppTag",dashboard.appTags());
        index=map(sheet,index,"Environment",dashboard.environments()); index=map(sheet,index,"Risk Score",dashboard.riskScores());
        index=map(sheet,index,"Severity",dashboard.severities()); map(sheet,index,"Remediation",dashboard.remediation());
    }
    private int map(Sheet sheet,int row,String title,Map<String,Long> values) { row(sheet,row++,title,"Count"); for(var entry:values.entrySet()) row(sheet,row++,entry.getKey(),entry.getValue()); return row+1; }
    private void row(Sheet sheet,int index,Object... values) { Row row=sheet.createRow(index); for(int c=0;c<values.length;c++) { Object value=values[c]; Cell cell=row.createCell(c); if(value==null) continue; if(value instanceof Number n) cell.setCellValue(n.doubleValue()); else cell.setCellValue(safe(String.valueOf(value))); } }
    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
    static String safe(String value) { int i=0; while(i<value.length()&&(value.charAt(i)==' '||value.charAt(i)=='\t'||value.charAt(i)=='\r'||value.charAt(i)=='\n')) i++; return i<value.length()&&"=+-@".indexOf(value.charAt(i))>=0?"'"+value:value; }
    public record Filters(String ipAddress,String application,String appTag,String riskRating,String compliance,String environment,String query) { }
}
