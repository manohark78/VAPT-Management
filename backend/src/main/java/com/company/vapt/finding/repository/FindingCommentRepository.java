package com.company.vapt.finding.repository;

import com.company.vapt.finding.entity.Finding;
import com.company.vapt.finding.entity.FindingComment;
import com.company.vapt.finding.entity.FindingObservation;
import com.company.vapt.ingestion.entity.ImportBatch;
import com.company.vapt.report.entity.ReportSection;
import com.company.vapt.user.domain.UserScope;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface FindingCommentRepository extends JpaRepository<FindingComment, UUID> {
    Page<FindingComment> findByReportSectionIdAndIpAddressIgnoreCaseAndApplicationNameIgnoreCaseAndAppTagIgnoreCaseOrderByCreatedAtDesc(
            String sectionId, String ipAddress, String applicationName, String appTag, Pageable pageable);

    @Query("select c from FindingComment c where c.reportSection.id=:sectionId and exists (select o.id from FindingObservation o join o.importBatch b where o.reportSection.id=c.reportSection.id and b.active=true and lower(o.ipAddress)=lower(c.ipAddress) and lower(o.applicationName)=lower(c.applicationName) and lower(o.appTag)=lower(c.appTag) and (:admin=true or exists (select s.id from com.company.vapt.user.domain.UserScope s where s.user.id=:userId and ((s.ipAddress is not null and lower(s.ipAddress)=lower(o.ipAddress)) or (s.applicationName is not null and lower(s.applicationName)=lower(o.applicationName))))))")
    Page<FindingComment> findCurrentAuthorized(@Param("sectionId") String sectionId,@Param("admin") boolean admin,
            @Param("userId") Long userId,Pageable pageable);

    @Query(value="select c from FindingComment c where c.reportSection.id=:sectionId and (:ip is null or lower(c.ipAddress)=lower(:ip)) and (:application is null or lower(c.applicationName)=lower(:application)) and (:appTag is null or lower(c.appTag)=lower(:appTag)) and exists (select o.id from FindingObservation o join o.importBatch b where o.reportSection.id=c.reportSection.id and b.active=true and lower(o.ipAddress)=lower(c.ipAddress) and lower(o.applicationName)=lower(c.applicationName) and lower(o.appTag)=lower(c.appTag) and (:admin=true or exists (select s.id from com.company.vapt.user.domain.UserScope s where s.user.id=:userId and ((s.ipAddress is not null and lower(s.ipAddress)=lower(o.ipAddress)) or (s.applicationName is not null and lower(s.applicationName)=lower(o.applicationName))))))",
        countQuery="select count(c) from FindingComment c where c.reportSection.id=:sectionId and (:ip is null or lower(c.ipAddress)=lower(:ip)) and (:application is null or lower(c.applicationName)=lower(:application)) and (:appTag is null or lower(c.appTag)=lower(:appTag)) and exists (select o.id from FindingObservation o join o.importBatch b where o.reportSection.id=c.reportSection.id and b.active=true and lower(o.ipAddress)=lower(c.ipAddress) and lower(o.applicationName)=lower(c.applicationName) and lower(o.appTag)=lower(c.appTag) and (:admin=true or exists (select s.id from com.company.vapt.user.domain.UserScope s where s.user.id=:userId and ((s.ipAddress is not null and lower(s.ipAddress)=lower(o.ipAddress)) or (s.applicationName is not null and lower(s.applicationName)=lower(o.applicationName))))))")
    Page<FindingComment> findCurrentAuthorizedFiltered(@Param("sectionId") String sectionId,@Param("admin") boolean admin,
            @Param("userId") Long userId,@Param("ip") String ip,@Param("application") String application,
            @Param("appTag") String appTag,Pageable pageable);
}
