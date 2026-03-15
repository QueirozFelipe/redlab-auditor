package com.redlab.auditor.usecase.port.out;

import com.redlab.auditor.domain.model.Commit;
import com.redlab.auditor.domain.model.Profile;

import java.util.List;

public interface SourceControlPort {
    /**
     * Retrieves all commits made after the latest tag on the production branch,
     * iterating across all projects within a repository group.
     *
     * @param profile          The profile with all configuration needed to fetch data from the APIs.
     * @param targetBranch     The branch intended to be merged into the production branch,
     *                         which will be scanned for new commits.
     * @return A consolidated list of {@link Commit} objects from all projects within the group.
     */
    SourceControlResult fetchCommitsSinceLastTag(Profile profile, String targetBranch);
}
