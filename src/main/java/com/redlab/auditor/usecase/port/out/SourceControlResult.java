package com.redlab.auditor.usecase.port.out;

import com.redlab.auditor.domain.model.Commit;

import java.util.List;

public record SourceControlResult(List<Commit> commits,
                                  List<String> activeProjects,
                                  List<String> ignoredProjects) {
}
