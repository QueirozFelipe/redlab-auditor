package com.redlab.auditor.domain.model;

import java.util.Arrays;

public enum ProjectManagerType {
    REDMINE(1, "Redmine"),
    JIRA(2, "Jira"),
    PM_MOCK(3, "Test Data");

    private final int id;
    private final String displayName;

    ProjectManagerType(int id, String displayName) { this.id = id; this.displayName = displayName; }
    public String getDisplayName() { return displayName; }

    public static ProjectManagerType fromId(int id) {
        return Arrays.stream(values()).filter(e -> e.id == id).findFirst().orElse(PM_MOCK);
    }
}
