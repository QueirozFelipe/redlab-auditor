package com.redlab.auditor.usecase.port.out;

import com.redlab.auditor.domain.model.Commit;
import com.redlab.auditor.domain.model.Profile;

import java.util.List;

public interface SourceControlPort {
    /**
     * Retrieves all commits made after the latest tag on the production branch,
     * iterating across all projects within a repository group.
     *
     * @param profile        The profile with all configuration needed to fetch data from the APIs.
     * @param sourceBranches A prioritized list of branches containing new development (e.g., ["dev", "develop"]).
     * @param targetBranches A prioritized list of stable/production branches (e.g., ["main", "master"]).
     * @return A consolidated list of {@link Commit} objects from all projects within the group.
     */
    SourceControlResult compareBranches(Profile profile, List<String> sourceBranches, List<String> targetBranches);
}
