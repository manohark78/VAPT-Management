package com.company.vapt.finding.entity;

import com.company.vapt.report.entity.ReportSection;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "finding_comment")
public class FindingComment {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "report_section_id") private ReportSection reportSection;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "finding_id") private Finding finding;
    @Column(name = "ip_address", nullable = false) private String ipAddress;
    @Column(name = "application_name", nullable = false) private String applicationName;
    @Column(name = "app_tag", nullable = false) private String appTag;
    @Column(name = "comment_text", nullable = false, columnDefinition = "CLOB") private String commentText;
    @Column(name = "actor_user_id", nullable = false) private String actorUserId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected FindingComment() { }
    public FindingComment(UUID id, ReportSection section, Finding finding, String ip, String application,
                          String appTag, String text, String actor, Instant createdAt) {
        this.id=id; this.reportSection=section; this.finding=finding; this.ipAddress=ip;
        this.applicationName=application; this.appTag=appTag; this.commentText=text;
        this.actorUserId=actor; this.createdAt=createdAt;
    }
    public UUID getId(){return id;} public ReportSection getReportSection(){return reportSection;}
    public Finding getFinding(){return finding;} public String getIpAddress(){return ipAddress;}
    public String getApplicationName(){return applicationName;} public String getAppTag(){return appTag;}
    public String getCommentText(){return commentText;} public String getActorUserId(){return actorUserId;}
    public Instant getCreatedAt(){return createdAt;}
}
