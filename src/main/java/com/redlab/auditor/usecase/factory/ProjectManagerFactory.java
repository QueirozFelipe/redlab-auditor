package com.redlab.auditor.usecase.factory;

import com.redlab.auditor.domain.model.ProjectManagerType;
import com.redlab.auditor.usecase.port.out.ProjectManagerPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.inject.Inject;

@ApplicationScoped
public class ProjectManagerFactory {

    @Inject
    @Any
    Instance<ProjectManagerPort> projectManagerPorts;

    public ProjectManagerPort getAdapter(ProjectManagerType type) {
        if (type == null) {
            throw new IllegalArgumentException("Project Manager type cannot be null");
        }
        return projectManagerPorts.select(NamedLiteral.of(type.name())).get();
    }
}
