package com.company.vapt.finding.controller;

import com.company.vapt.finding.entity.Finding;

import com.company.vapt.finding.service.PowerPointExportService;
import com.company.vapt.finding.service.ReportExportService;
import com.company.vapt.common.logging.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/reports/sections/{sectionId}/exports")
@PreAuthorize("hasAnyRole('ADMIN','USER')")
public class ReportExportController {
    private final ReportExportService xlsx; private final PowerPointExportService pptx;
    public ReportExportController(ReportExportService xlsx,PowerPointExportService pptx){this.xlsx=xlsx;this.pptx=pptx;}
    @GetMapping("/xlsx")
    public ResponseEntity<StreamingResponseBody> excel(@PathVariable String sectionId,Authentication auth,@ModelAttribute ReportExportService.Filters filters){
        String correlation=correlation(); StreamingResponseBody body=out->{MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY,correlation);try{xlsx.writeExcel(sectionId,auth,filters,out);}finally{MDC.remove(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);}};
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+sectionId+"-report.xlsx\"").body(body);
    }
    @GetMapping("/pptx")
    public ResponseEntity<StreamingResponseBody> powerpoint(@PathVariable String sectionId,Authentication auth,@ModelAttribute ReportExportService.Filters filters){
        String correlation=correlation(); StreamingResponseBody body=out->{MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY,correlation);try{pptx.write(sectionId,auth,filters,out);}finally{MDC.remove(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);}};
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation"))
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+sectionId+"-summary.pptx\"").body(body);
    }
    private String correlation(){String id=MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);return id==null?"SYSTEM":id;}
}
