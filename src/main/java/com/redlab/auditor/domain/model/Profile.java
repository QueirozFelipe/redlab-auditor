package com.redlab.auditor.domain.model;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

public record Profile(
        String name,
        ProjectManagerType pmType,
        SourceControlType scType,
        String projectManagerURL,
        String projectManagerToken,
        Set<Long> projectManagerIssueTypes,
        String sourceControlURL,
        String sourceControlToken,
        String sourceControlGroupId,
        int sourceControlRateLimit,
        Set<Long> projectsToIgnore,
        List<String> sourceBranches,
        List<String> targetBranches,
        String taskRegex
) implements Serializable {

    public String maskedToken(String token) {
        if (token == null || token.length() < 8) return "****";
        return token.substring(0, 4) + "...." + token.substring(token.length() - 4);
    }
}