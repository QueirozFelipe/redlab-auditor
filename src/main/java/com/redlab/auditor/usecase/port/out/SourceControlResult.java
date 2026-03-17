package com.redlab.auditor.usecase.port.out;

import com.redlab.auditor.domain.model.Commit;
import com.redlab.auditor.domain.model.SourceControlInfo;

import java.util.List;

public record SourceControlResult(SourceControlInfo scInfo,
                                  List<Commit> commits,
                                  List<String> activeProjects,
                                  List<String> ignoredProjects) {
}
