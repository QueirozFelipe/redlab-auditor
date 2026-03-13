package com.redlab.auditor.domain.model;

public record Task(
        String id,
        String title,
        String assignee,
        String status,
        String url
) {
}
