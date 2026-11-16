package com.company.vapt.finding.repository;

import com.company.vapt.finding.entity.Finding;
import com.company.vapt.finding.entity.FindingObservation;
import com.company.vapt.ingestion.entity.ImportBatch;
import com.company.vapt.report.entity.ReportSection;
import com.company.vapt.user.domain.UserScope;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FindingObservationRepository extends JpaRepository<FindingObservation, UUID> {
    List<FindingObservation> findByImportBatchIdOrderBySourceRecordSourceRowNumberAsc(UUID batchId);
    List<FindingObservation> findByFindingIdOrderByObservedAtDesc(UUID findingId);
    @Query("select o from FindingObservation o where o.finding.id in :findingIds and not exists (select later.id from FindingObservation later where later.finding=o.finding and later.observedAt>o.observedAt)")
    List<FindingObservation> findLatestForFindings(@Param("findingIds") java.util.Collection<UUID> findingIds);
    long countByFindingId(UUID findingId);
    long countByImportBatchId(UUID batchId);
    java.util.Optional<FindingObservation> findFirstByFindingIdOrderByObservedAtDesc(UUID findingId);
    java.util.Optional<FindingObservation> findFirstByFindingIdAndClassificationOrderByObservedAtDesc(UUID findingId, String classification);

    @Query(value = """
        select o.* from finding_observation o join import_batch b on b.id=o.import_batch_id
        left join report_import_record rr on rr.id=o.report_import_record_id
        where o.report_section_id=:sectionId and b.is_active=true
          and (:ip is null or lower(o.ip_address)=lower(:ip))
          and (:application is null or lower(o.application_name)=lower(:application))
          and (:appTag is null or lower(o.app_tag)=lower(:appTag))
          and (:riskRating is null or lower(o.risk_rating)=lower(:riskRating))
          and (:compliance is null or lower(o.compliance_status)=lower(:compliance))
          and (:environment is null or lower(o.environment)=lower(:environment))
          and (:query is null or lower(coalesce(o.display_name,'')) like concat('%',lower(:query),'%')
               or lower(coalesce(o.source_values,'')) like concat('%',lower(:query),'%'))
          and (:admin=true or (o.finding_id is not null and exists
               (select 1 from user_scope s where s.user_id=:userId
                and ((s.ip_address is not null and lower(s.ip_address)=lower(o.ip_address))
                  or (s.application_name is not null and lower(s.application_name)=lower(o.application_name))))))
        order by
          case when :sortBy='riskRating' and :direction='asc' then o.risk_rating end asc,
          case when :sortBy='riskRating' and :direction='desc' then o.risk_rating end desc,
          case when :sortBy='severity' and :direction='asc' then o.severity end asc,
          case when :sortBy='severity' and :direction='desc' then o.severity end desc,
          case when :sortBy='appTag' and :direction='asc' then o.app_tag end asc,
          case when :sortBy='appTag' and :direction='desc' then o.app_tag end desc,
          case when :sortBy='applicationName' and :direction='asc' then o.application_name end asc,
          case when :sortBy='applicationName' and :direction='desc' then o.application_name end desc,
          case when :sortBy='ipAddress' and :direction='asc' then o.ip_address end asc,
          case when :sortBy='ipAddress' and :direction='desc' then o.ip_address end desc,
          case when :sortBy='environment' and :direction='asc' then o.environment end asc,
          case when :sortBy='environment' and :direction='desc' then o.environment end desc,
          case when :sortBy='sourceRowNumber' and :direction='asc' then rr.source_row_number end asc,
          case when :sortBy='sourceRowNumber' and :direction='desc' then rr.source_row_number end desc,
          case when :sortBy='observedAt' and :direction='asc' then o.observed_at end asc,
          case when :sortBy='observedAt' and :direction='desc' then o.observed_at end desc,
          o.id asc
        """,
        countQuery = """
        select count(*) from finding_observation o join import_batch b on b.id=o.import_batch_id
        where o.report_section_id=:sectionId and b.is_active=true
          and (:ip is null or lower(o.ip_address)=lower(:ip))
          and (:application is null or lower(o.application_name)=lower(:application))
          and (:appTag is null or lower(o.app_tag)=lower(:appTag))
          and (:riskRating is null or lower(o.risk_rating)=lower(:riskRating))
          and (:compliance is null or lower(o.compliance_status)=lower(:compliance))
          and (:environment is null or lower(o.environment)=lower(:environment))
          and (:query is null or lower(coalesce(o.display_name,'')) like concat('%',lower(:query),'%')
               or lower(coalesce(o.source_values,'')) like concat('%',lower(:query),'%'))
          and (:admin=true or (o.finding_id is not null and exists
               (select 1 from user_scope s where s.user_id=:userId
                and ((s.ip_address is not null and lower(s.ip_address)=lower(o.ip_address))
                  or (s.application_name is not null and lower(s.application_name)=lower(o.application_name))))))
        """,nativeQuery=true)
    Page<FindingObservation> findCurrentPage(@Param("sectionId") String sectionId,
            @Param("admin") boolean admin, @Param("userId") Long userId,
            @Param("ip") String ip, @Param("application") String application,
            @Param("appTag") String appTag, @Param("riskRating") String riskRating,
            @Param("compliance") String compliance, @Param("environment") String environment,
            @Param("query") String query, @Param("sortBy") String sortBy,
            @Param("direction") String direction, Pageable pageable);

    @Query("select o.riskRating, count(o) from FindingObservation o join o.importBatch b where o.finding is not null and o.reportSection.id=:sectionId and b.active=true and (:application is null or lower(o.applicationName)=lower(:application)) and (:appTag is null or lower(o.appTag)=lower(:appTag)) and (:riskRating is null or lower(o.riskRating)=lower(:riskRating)) and (:compliance is null or lower(o.complianceStatus)=lower(:compliance)) and (:environment is null or lower(o.environment)=lower(:environment)) and (:admin=true or exists (select s.id from com.company.vapt.user.domain.UserScope s where s.user.id=:userId and ((s.ipAddress is not null and lower(s.ipAddress)=lower(o.ipAddress)) or (s.applicationName is not null and lower(s.applicationName)=lower(o.applicationName))))) group by o.riskRating")
    List<Object[]> aggregateRisk(@Param("sectionId") String sectionId,@Param("admin") boolean admin,@Param("userId") Long userId,@Param("application") String application,@Param("appTag") String appTag,@Param("riskRating") String riskRating,@Param("compliance") String compliance,@Param("environment") String environment);
    @Query("select o.complianceStatus, count(o) from FindingObservation o join o.importBatch b where o.finding is not null and o.reportSection.id=:sectionId and b.active=true and (:application is null or lower(o.applicationName)=lower(:application)) and (:appTag is null or lower(o.appTag)=lower(:appTag)) and (:riskRating is null or lower(o.riskRating)=lower(:riskRating)) and (:compliance is null or lower(o.complianceStatus)=lower(:compliance)) and (:environment is null or lower(o.environment)=lower(:environment)) and (:admin=true or exists (select s.id from com.company.vapt.user.domain.UserScope s where s.user.id=:userId and ((s.ipAddress is not null and lower(s.ipAddress)=lower(o.ipAddress)) or (s.applicationName is not null and lower(s.applicationName)=lower(o.applicationName))))) group by o.complianceStatus")
    List<Object[]> aggregateCompliance(@Param("sectionId") String sectionId,@Param("admin") boolean admin,@Param("userId") Long userId,@Param("application") String application,@Param("appTag") String appTag,@Param("riskRating") String riskRating,@Param("compliance") String compliance,@Param("environment") String environment);
    @Query("select o.applicationName, count(o) from FindingObservation o join o.importBatch b where o.finding is not null and o.reportSection.id=:sectionId and b.active=true and (:application is null or lower(o.applicationName)=lower(:application)) and (:appTag is null or lower(o.appTag)=lower(:appTag)) and (:riskRating is null or lower(o.riskRating)=lower(:riskRating)) and (:compliance is null or lower(o.complianceStatus)=lower(:compliance)) and (:environment is null or lower(o.environment)=lower(:environment)) and (:admin=true or exists (select s.id from com.company.vapt.user.domain.UserScope s where s.user.id=:userId and ((s.ipAddress is not null and lower(s.ipAddress)=lower(o.ipAddress)) or (s.applicationName is not null and lower(s.applicationName)=lower(o.applicationName))))) group by o.applicationName")
    List<Object[]> aggregateApplication(@Param("sectionId") String sectionId,@Param("admin") boolean admin,@Param("userId") Long userId,@Param("application") String application,@Param("appTag") String appTag,@Param("riskRating") String riskRating,@Param("compliance") String compliance,@Param("environment") String environment);
    @Query("select o.appTag, count(o) from FindingObservation o join o.importBatch b where o.finding is not null and o.reportSection.id=:sectionId and b.active=true and (:application is null or lower(o.applicationName)=lower(:application)) and (:appTag is null or lower(o.appTag)=lower(:appTag)) and (:riskRating is null or lower(o.riskRating)=lower(:riskRating)) and (:compliance is null or lower(o.complianceStatus)=lower(:compliance)) and (:environment is null or lower(o.environment)=lower(:environment)) and (:admin=true or exists (select s.id from com.company.vapt.user.domain.UserScope s where s.user.id=:userId and ((s.ipAddress is not null and lower(s.ipAddress)=lower(o.ipAddress)) or (s.applicationName is not null and lower(s.applicationName)=lower(o.applicationName))))) group by o.appTag")
    List<Object[]> aggregateAppTag(@Param("sectionId") String sectionId,@Param("admin") boolean admin,@Param("userId") Long userId,@Param("application") String application,@Param("appTag") String appTag,@Param("riskRating") String riskRating,@Param("compliance") String compliance,@Param("environment") String environment);
    @Query("select o.environment, count(o) from FindingObservation o join o.importBatch b where o.finding is not null and o.reportSection.id=:sectionId and b.active=true and (:application is null or lower(o.applicationName)=lower(:application)) and (:appTag is null or lower(o.appTag)=lower(:appTag)) and (:riskRating is null or lower(o.riskRating)=lower(:riskRating)) and (:compliance is null or lower(o.complianceStatus)=lower(:compliance)) and (:environment is null or lower(o.environment)=lower(:environment)) and (:admin=true or exists (select s.id from com.company.vapt.user.domain.UserScope s where s.user.id=:userId and ((s.ipAddress is not null and lower(s.ipAddress)=lower(o.ipAddress)) or (s.applicationName is not null and lower(s.applicationName)=lower(o.applicationName))))) group by o.environment")
    List<Object[]> aggregateEnvironment(@Param("sectionId") String sectionId,@Param("admin") boolean admin,@Param("userId") Long userId,@Param("application") String application,@Param("appTag") String appTag,@Param("riskRating") String riskRating,@Param("compliance") String compliance,@Param("environment") String environment);
    @Query("select o.riskScore, count(o) from FindingObservation o join o.importBatch b where o.finding is not null and o.reportSection.id=:sectionId and b.active=true and (:application is null or lower(o.applicationName)=lower(:application)) and (:appTag is null or lower(o.appTag)=lower(:appTag)) and (:riskRating is null or lower(o.riskRating)=lower(:riskRating)) and (:compliance is null or lower(o.complianceStatus)=lower(:compliance)) and (:environment is null or lower(o.environment)=lower(:environment)) and (:admin=true or exists (select s.id from com.company.vapt.user.domain.UserScope s where s.user.id=:userId and ((s.ipAddress is not null and lower(s.ipAddress)=lower(o.ipAddress)) or (s.applicationName is not null and lower(s.applicationName)=lower(o.applicationName))))) group by o.riskScore")
    List<Object[]> aggregateRiskScore(@Param("sectionId") String sectionId,@Param("admin") boolean admin,@Param("userId") Long userId,@Param("application") String application,@Param("appTag") String appTag,@Param("riskRating") String riskRating,@Param("compliance") String compliance,@Param("environment") String environment);
    @Query("select o.severity, count(o) from FindingObservation o join o.importBatch b where o.finding is not null and o.reportSection.id=:sectionId and b.active=true and (:application is null or lower(o.applicationName)=lower(:application)) and (:appTag is null or lower(o.appTag)=lower(:appTag)) and (:riskRating is null or lower(o.riskRating)=lower(:riskRating)) and (:compliance is null or lower(o.complianceStatus)=lower(:compliance)) and (:environment is null or lower(o.environment)=lower(:environment)) and (:admin=true or exists (select s.id from com.company.vapt.user.domain.UserScope s where s.user.id=:userId and ((s.ipAddress is not null and lower(s.ipAddress)=lower(o.ipAddress)) or (s.applicationName is not null and lower(s.applicationName)=lower(o.applicationName))))) group by o.severity")
    List<Object[]> aggregateSeverity(@Param("sectionId") String sectionId,@Param("admin") boolean admin,@Param("userId") Long userId,@Param("application") String application,@Param("appTag") String appTag,@Param("riskRating") String riskRating,@Param("compliance") String compliance,@Param("environment") String environment);

    @Query("select f.lifecycleState, count(f) from Finding f where f.reportSection.id=:sectionId and exists (select o.id from FindingObservation o join o.importBatch b where o.finding=f and b.active=true and (:application is null or lower(o.applicationName)=lower(:application)) and (:appTag is null or lower(o.appTag)=lower(:appTag)) and (:riskRating is null or lower(o.riskRating)=lower(:riskRating)) and (:compliance is null or lower(o.complianceStatus)=lower(:compliance)) and (:environment is null or lower(o.environment)=lower(:environment)) and (:admin=true or exists (select s.id from com.company.vapt.user.domain.UserScope s where s.user.id=:userId and ((s.ipAddress is not null and lower(s.ipAddress)=lower(o.ipAddress)) or (s.applicationName is not null and lower(s.applicationName)=lower(o.applicationName)))))) group by f.lifecycleState")
    List<Object[]> aggregateLifecycle(@Param("sectionId") String sectionId,@Param("admin") boolean admin,@Param("userId") Long userId,@Param("application") String application,@Param("appTag") String appTag,@Param("riskRating") String riskRating,@Param("compliance") String compliance,@Param("environment") String environment);

    @Query("select o from FindingObservation o where o.finding.id=:findingId order by o.observedAt desc")
    Page<FindingObservation> history(@Param("findingId") UUID findingId, Pageable pageable);

    @Query("select o from FindingObservation o where o.finding is not null and o.reportSection.id=:sectionId and o.observedAt<=:asOf and not exists (select later.id from FindingObservation later where later.finding=o.finding and later.observedAt<=:asOf and later.observedAt>o.observedAt) and (:admin=true or exists (select s.id from com.company.vapt.user.domain.UserScope s where s.user.id=:userId and ((s.ipAddress is not null and lower(s.ipAddress)=lower(o.ipAddress)) or (s.applicationName is not null and lower(s.applicationName)=lower(o.applicationName)))))")
    Page<FindingObservation> findLatestAsOf(@Param("sectionId") String sectionId, @Param("asOf") Instant asOf,
            @Param("admin") boolean admin, @Param("userId") Long userId, Pageable pageable);

    @Query("select count(o)>0 from FindingObservation o join o.importBatch b where o.reportSection.id=:sectionId and b.active=true and lower(o.ipAddress)=lower(:ip) and lower(o.applicationName)=lower(:application) and lower(o.appTag)=lower(:appTag) and (:admin=true or exists (select s.id from com.company.vapt.user.domain.UserScope s where s.user.id=:userId and ((s.ipAddress is not null and lower(s.ipAddress)=lower(o.ipAddress)) or (s.applicationName is not null and lower(s.applicationName)=lower(o.applicationName)))))")
    boolean existsCurrentContext(@Param("sectionId") String sectionId, @Param("ip") String ip,
            @Param("application") String application, @Param("appTag") String appTag,
            @Param("admin") boolean admin, @Param("userId") Long userId);

    @Query(value="""
        select o.ip_address,o.application_name,o.app_tag,
          sum(case when lower(o.risk_rating)='critical' then 1 else 0 end),
          sum(case when lower(o.risk_rating)='high' then 1 else 0 end),
          sum(case when lower(o.risk_rating)='medium' then 1 else 0 end),
          sum(case when lower(o.risk_rating)='low' then 1 else 0 end),count(*)
        from finding_observation o join import_batch b on b.id=o.import_batch_id
        where o.finding_id is not null and b.is_active=true and o.report_section_id=:sectionId
          and o.ip_address is not null and o.application_name is not null and o.app_tag is not null
          and (:admin=true or exists(select 1 from user_scope s where s.user_id=:userId and
             ((s.ip_address is not null and lower(s.ip_address)=lower(o.ip_address)) or
              (s.application_name is not null and lower(s.application_name)=lower(o.application_name)))))
        group by o.ip_address,o.application_name,o.app_tag
        order by o.ip_address,o.application_name,o.app_tag
        """,countQuery="""
        select count(*) from (select o.ip_address,o.application_name,o.app_tag
          from finding_observation o join import_batch b on b.id=o.import_batch_id
          where o.finding_id is not null and b.is_active=true and o.report_section_id=:sectionId
            and o.ip_address is not null and o.application_name is not null and o.app_tag is not null
            and (:admin=true or exists(select 1 from user_scope s where s.user_id=:userId and
             ((s.ip_address is not null and lower(s.ip_address)=lower(o.ip_address)) or
              (s.application_name is not null and lower(s.application_name)=lower(o.application_name)))))
          group by o.ip_address,o.application_name,o.app_tag) groups_for_page
        """,nativeQuery=true)
    Page<Object[]> commentGroups(@Param("sectionId") String sectionId,@Param("admin") boolean admin,
            @Param("userId") Long userId,Pageable pageable);
}
