package com.redlab.auditor.domain.model;

import java.util.Arrays;

public enum SourceControlType {
    GITLAB(1, "GitLab"),
    GITHUB(2, "GitHub"),
    SC_MOCK(3, "Test Data");

    private final int id;
    private final String displayName;

    SourceControlType(int id, String displayName) { this.id = id; this.displayName = displayName; }
    public String getDisplayName() { return displayName; }

    public static SourceControlType fromId(int id) {
        return Arrays.stream(values()).filter(e -> e.id == id).findFirst().orElse(SC_MOCK);
    }
}
