package com.redlab.auditor.domain.model;

import java.util.List;

public record ProjectManagerInfo(String providerName,
                                 String providerUrl,
                                 String projectName,
                                 String projectId,
                                 String versionName,
                                 String versionId,
                                 String versionUrl,
                                 String versionStatus,
                                 String dueDate,
                                 List<Tracker> trackers) {
}
