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
