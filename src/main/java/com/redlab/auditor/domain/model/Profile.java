package com.redlab.auditor.domain.model;

import java.io.Serializable;
import java.util.List;

public record Profile(
        String name,
        String redmineUrl,
        String redmineToken,
        String redmineTrackers,
        String gitlabUrl,
        String gitlabToken,
        String gitlabGroupId,
        int gitlabRateLimit,
        List<String> sourceBranches,
        List<String> targetBranches,
        String taskRegex
) implements Serializable {

    public String maskedToken(String token) {
        if (token == null || token.length() < 8) return "****";
        return token.substring(0, 4) + "...." + token.substring(token.length() - 4);
    }
}