package com.company.vapt.finding.repository;

import com.company.vapt.finding.entity.Finding;
import com.company.vapt.finding.entity.FindingObservation;
import com.company.vapt.finding.entity.FindingRemediationAction;
import com.company.vapt.ingestion.entity.ImportBatch;
import com.company.vapt.report.entity.ReportSection;
import com.company.vapt.user.domain.UserScope;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface FindingRemediationActionRepository extends JpaRepository<FindingRemediationAction, UUID> {
    List<FindingRemediationAction> findByFindingIdOrderByCreatedAtDesc(UUID findingId);
    boolean existsByFindingId(UUID findingId);
    List<FindingRemediationAction> findByFindingIdIn(Collection<UUID> findingIds);

    @Query("select a.actionType, count(a) from FindingRemediationAction a where a.finding.reportSection.id=:sectionId and not exists (select newer.id from FindingRemediationAction newer where newer.finding=a.finding and newer.createdAt>a.createdAt) and exists (select o.id from FindingObservation o join o.importBatch b where o.finding=a.finding and b.active=true and (:application is null or lower(o.applicationName)=lower(:application)) and (:appTag is null or lower(o.appTag)=lower(:appTag)) and (:riskRating is null or lower(o.riskRating)=lower(:riskRating)) and (:compliance is null or lower(o.complianceStatus)=lower(:compliance)) and (:environment is null or lower(o.environment)=lower(:environment)) and (:admin=true or exists (select s.id from com.company.vapt.user.domain.UserScope s where s.user.id=:userId and ((s.ipAddress is not null and lower(s.ipAddress)=lower(o.ipAddress)) or (s.applicationName is not null and lower(s.applicationName)=lower(o.applicationName)))))) group by a.actionType")
    List<Object[]> aggregateCurrentActions(@Param("sectionId") String sectionId,@Param("admin") boolean admin,@Param("userId") Long userId,@Param("application") String application,@Param("appTag") String appTag,@Param("riskRating") String riskRating,@Param("compliance") String compliance,@Param("environment") String environment);

    @Query("select count(f) from Finding f where f.reportSection.id=:sectionId and f.lifecycleState='OPEN' and exists (select o.id from FindingObservation o join o.importBatch b where o.finding=f and b.active=true and (:application is null or lower(o.applicationName)=lower(:application)) and (:appTag is null or lower(o.appTag)=lower(:appTag)) and (:riskRating is null or lower(o.riskRating)=lower(:riskRating)) and (:compliance is null or lower(o.complianceStatus)=lower(:compliance)) and (:environment is null or lower(o.environment)=lower(:environment)) and (:admin=true or exists (select s.id from com.company.vapt.user.domain.UserScope s where s.user.id=:userId and ((s.ipAddress is not null and lower(s.ipAddress)=lower(o.ipAddress)) or (s.applicationName is not null and lower(s.applicationName)=lower(o.applicationName)))))) and not exists (select a.id from FindingRemediationAction a where a.finding=f)")
    long countCurrentWithoutRemediation(@Param("sectionId") String sectionId,@Param("admin") boolean admin,@Param("userId") Long userId,@Param("application") String application,@Param("appTag") String appTag,@Param("riskRating") String riskRating,@Param("compliance") String compliance,@Param("environment") String environment);
}
