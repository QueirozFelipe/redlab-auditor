package com.redlab.auditor.adapter.in.cli;

import picocli.CommandLine.Command;

@Command(name = "profile",
        description = "Manages configuration profiles.",
        subcommands = {
                ProfileAddCommand.class,
                ProfileEditCommand.class
        })
public class ProfileCommand {
}