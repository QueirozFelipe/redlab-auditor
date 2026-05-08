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

import java.util.Arrays;

public enum SourceControlType {
    GITLAB(1, "GitLab"),
    GITHUB(2, "GitHub");

    private final int id;
    private final String displayName;

    SourceControlType(int id, String displayName) { this.id = id; this.displayName = displayName; }
    public String getDisplayName() { return displayName; }

    public static SourceControlType fromId(int id) {
        return Arrays.stream(values()).filter(e -> e.id == id).findFirst().orElse(null);
    }
}
