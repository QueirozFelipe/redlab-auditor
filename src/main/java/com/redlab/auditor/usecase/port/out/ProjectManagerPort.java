package com.redlab.auditor.usecase.port.out;

import com.redlab.auditor.domain.model.Task;

import java.util.List;

public interface ProjectManagerPort {
    /**
     * Retrieves all tasks associated with a specific version.
     *
     * @param version The version code or identifier provided by the user.
     * @return A list of {@link Task} objects belonging to the specified version.
     */
    List<Task> fetchTasksByVersion(String version);
}
