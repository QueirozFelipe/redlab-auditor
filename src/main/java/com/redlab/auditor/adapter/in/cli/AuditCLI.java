package com.redlab.auditor.adapter.in.cli;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine.Command;

@TopCommand
@Command(name = "redlab",
        mixinStandardHelpOptions = true,
        version = "1.0.0",
        description = "RedLab Auditor - Tool for versioning audit.",
        subcommands = {
                GenerateAuditCommand.class,
                ProfileCommand.class
        })
public class AuditCLI {
}