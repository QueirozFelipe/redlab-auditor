package com.redlab.auditor.domain.model;

import java.util.List;

public record Commit(String hash,
                     String message,
                     String author,
                     List<String> associatedTaskIds,
                     String url) {
}
