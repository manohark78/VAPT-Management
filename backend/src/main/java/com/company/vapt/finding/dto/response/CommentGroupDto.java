package com.company.vapt.finding.dto.response;

import com.company.vapt.finding.entity.Finding;
public record CommentGroupDto(String ipAddress,String applicationName,String appTag,
        long critical,long high,long medium,long low,long findingCount) { }
