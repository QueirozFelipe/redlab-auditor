package com.redlab.auditor.domain.model;

public record SourceControlInfo(String providerName,
																																String providerUrl,
																																String groupName,
																																String groupId,
																																int totalProjects,
																																int validProjects,
																																int activeProjectsCount) {
}
