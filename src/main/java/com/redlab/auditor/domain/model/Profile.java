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

package com.redlab.auditor.domain.model;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

public record Profile(
        String name,
        ProjectManagerType pmType,
        SourceControlType scType,
        String projectManagerURL,
        String projectManagerToken,
        Set<Long> projectManagerIssueTypes,
        String sourceControlURL,
        String sourceControlToken,
        String sourceControlGroupId,
        int sourceControlRateLimit,
        Set<Long> projectsToIgnore,
        List<String> sourceBranches,
        List<String> targetBranches,
        String taskRegex
) implements Serializable {

    public String maskedToken(String token) {
        if (token == null || token.length() < 8) return "****";
        return token.substring(0, 4) + "...." + token.substring(token.length() - 4);
    }
}