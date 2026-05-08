/*
 * Copyright 2026 Felipe Queiroz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
