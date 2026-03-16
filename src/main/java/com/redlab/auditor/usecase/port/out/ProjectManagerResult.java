package com.redlab.auditor.usecase.port.out;

import com.redlab.auditor.domain.model.ProjectManagerInfo;
import com.redlab.auditor.domain.model.Task;

import java.util.List;

public record ProjectManagerResult(ProjectManagerInfo pmInfo,
																																			List<Task> tasks) {
}
