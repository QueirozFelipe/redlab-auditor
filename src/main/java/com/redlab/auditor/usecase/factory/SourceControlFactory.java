package com.redlab.auditor.usecase.factory;

import com.redlab.auditor.domain.model.SourceControlType;
import com.redlab.auditor.usecase.port.out.SourceControlPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.inject.Inject;

@ApplicationScoped
public class SourceControlFactory {

    @Inject
    @Any
    Instance<SourceControlPort> sourceControlPorts;

    public SourceControlPort getAdapter(SourceControlType type) {
        if (type == null) {
            throw new IllegalArgumentException("Source Control type cannot be null");
        }
        return sourceControlPorts.select(NamedLiteral.of(type.name())).get();
    }
}
