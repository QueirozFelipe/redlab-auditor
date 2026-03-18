package com.redlab.auditor.domain.model;

public record ActiveProjectInfo(String projectName,
                                String sourceBranch,
                                String targetBranch,
                                String totalCommits,
                                String lastCommitedOn,
                                String totalRelatedTasks) {
}
