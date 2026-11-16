package com.company.vapt.finding.service;

import com.company.vapt.finding.entity.Finding;
import com.company.vapt.report.domain.ReportSectionId;

import com.company.vapt.audit.service.AuditService;
import com.company.vapt.common.observability.PerformanceTimer;
import com.company.vapt.finding.dto.response.DashboardDto;
import com.company.vapt.report.domain.ReportSectionRegistry;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.util.Map;

@Service
public class PowerPointExportService {
    private final FindingWorkspaceService workspace; private final AuditService audit; private final String template;
    private final PerformanceTimer timer; private final ReportSectionRegistry sections;
    public PowerPointExportService(FindingWorkspaceService workspace,AuditService audit,PerformanceTimer timer,
            ReportSectionRegistry sections,@Value("${vapt.export.pptx-template:}") String template) {
        this.workspace=workspace; this.audit=audit; this.timer=timer; this.sections=sections; this.template=template;
    }

    public void write(String sectionId,Authentication auth,ReportExportService.Filters filters,OutputStream output) throws java.io.IOException {
        var sectionMeta=sections.getMetadata(sectionId); String section=sectionMeta.id().name(); String displayName=sectionMeta.displayName();
        var user=workspace.context(auth); long started=System.nanoTime(); String result="FAILURE";
        try(XMLSlideShow deck=openTemplate()) {
            DashboardDto data=workspace.dashboard(sectionId,auth,filters.application(),filters.appTag(),filters.riskRating(),filters.compliance(),filters.environment());
            titleSlide(deck,displayName,data);
            chartSlide(deck,"Risk and compliance",data.riskRating(),data.complianceStatus());
            chartSlide(deck,"Application and remediation",data.applications(),data.remediation());
            deck.write(output); output.flush();
            audit.recordInWorkflowTransaction(user.actorId(),user.admin()?"ADMIN":"USER","REPORT_EXPORT_PPTX","REPORT_SECTION",sectionId,"slides=3");
            result="SUCCESS";
        } catch (java.io.IOException | RuntimeException failure) {
            try { audit.record(user.actorId(),user.admin()?"ADMIN":"USER","REPORT_EXPORT_PPTX_FAILED","REPORT_SECTION",section,"failureType="+failure.getClass().getSimpleName()); } catch(Exception ignored) { }
            throw failure;
        } finally {
            timer.recordDuration("export.pptx.total",section,result,Duration.ofNanos(Math.max(0,System.nanoTime()-started)));
        }
    }
    private XMLSlideShow openTemplate() throws java.io.IOException {
        if(template==null||template.isBlank()) return new XMLSlideShow();
        Path path=Path.of(template).toAbsolutePath().normalize();
        if(!Files.isRegularFile(path)) throw new IllegalStateException("Configured presentation template is unavailable");
        try(InputStream in=Files.newInputStream(path)){return new XMLSlideShow(in);}
    }
    private void titleSlide(XMLSlideShow deck,String section,DashboardDto data) {
        XSLFSlide slide=deck.createSlide(); text(slide,28,25,680,50,"Vulnerability Assessment Summary",26,true);
        text(slide,30,90,660,35,section,18,true);
        text(slide,30,155,660,150,"Open findings: "+data.openFindings()+"\nClosed findings: "+data.closedFindings()+"\nReporting instant (UTC): "+Instant.now(),16,false);
        text(slide,30,350,660,35,"Source section: "+data.reportSectionId()+" — figures reflect the authorized current snapshot.",12,false);
    }
    private void chartSlide(XMLSlideShow deck,String title,Map<String,Long> left,Map<String,Long> right) {
        XSLFSlide slide=deck.createSlide(); text(slide,25,15,680,45,title,22,true);
        addChart(slide,25,75,325,355,"Findings",left); addChart(slide,375,75,325,355,"Breakdown",right);
    }
    private void addChart(XSLFSlide slide,double x,double y,double width,double height,String title,Map<String,Long> values) {
        String[] labels=values.keySet().stream().limit(12).toArray(String[]::new);
        Double[] numbers=values.values().stream().limit(12).map(Long::doubleValue).toArray(Double[]::new);
        if(labels.length==0){text(slide,x,y,width,height,"No data for "+title,14,false);return;}
        XSLFChart chart=slide.getSlideShow().createChart(); chart.setTitleText(title); chart.setTitleOverlay(false);
        XDDFCategoryAxis category=chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis value=chart.createValueAxis(AxisPosition.LEFT); value.setCrosses(AxisCrosses.AUTO_ZERO);
        XDDFChartData data=chart.createData(ChartTypes.BAR,category,value);
        XDDFChartData.Series series=data.addSeries(XDDFDataSourcesFactory.fromArray(labels),XDDFDataSourcesFactory.fromArray(numbers));
        series.setTitle("Count",null); chart.plot(data);
        slide.addChart(chart,new Rectangle2D.Double(x,y,width,height));
    }
    private void text(XSLFSlide slide,double x,double y,double width,double height,String value,int fontSize,boolean bold) {
        XSLFTextBox box=slide.createTextBox(); box.setAnchor(new Rectangle2D.Double(x,y,width,height)); box.setText(value);
        for(XSLFTextParagraph p:box.getTextParagraphs()) for(XSLFTextRun r:p.getTextRuns()){r.setFontSize((double)fontSize);r.setBold(bold);}
    }
}
