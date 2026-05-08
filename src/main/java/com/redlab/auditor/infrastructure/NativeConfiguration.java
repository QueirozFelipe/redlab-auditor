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

package com.redlab.auditor.infrastructure;

import com.redlab.auditor.domain.exception.ApiResponseException;
import com.redlab.auditor.domain.exception.ResourceNotFoundException;
import com.redlab.auditor.domain.exception.TooManyRequestsException;
import com.redlab.auditor.domain.exception.UnauthorizedException;
import com.redlab.auditor.domain.model.*;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(targets = {
        ActiveProjectInfo.class,
        AuditReport.class,
        AuditReportItem.class,
        AuditStatus.class,
        Commit.class,
        Profile.class,
        ProjectManagerInfo.class,
        ProjectManagerType.class,
        SourceControlInfo.class,
        SourceControlType.class,
        Task.class,
        Tracker.class,

        ApiResponseException.class,
        ResourceNotFoundException.class,
        TooManyRequestsException.class,
        UnauthorizedException.class,

        java.util.ArrayList.class,
        java.util.HashMap.class,
        java.util.HashSet.class
})
public class NativeConfiguration {
}
