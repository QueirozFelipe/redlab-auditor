package com.redlab.auditor.adapter.in.cli;

import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.infrastructure.security.ProfileStorageService;
import com.redlab.auditor.usecase.port.in.AuditCommandPort;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Set;

@Command(name = "audit", description = "Executes a cross-reference audit between Redmine tasks and GitLab commits.")
public class GenerateAuditCommand implements Runnable {

    @Option(names = {"-v", "--version"}, required = true,
            description = "The target version code in Redmine")
    String version;

    @Option(names = {"-p", "--profile"}, description = "Profile name to be used.")
    String profileName;

    @Option(names = {"-t", "--target"}, description = "Overrides the target branch from profile.")
    String targetBranch;

    @Inject
    AuditCommandPort auditCommandPort;

    @Inject
    ProfileStorageService storageService;

    @Override
    public void run() {
        var profile = storageService.loadProfiles().get(profileName);

        if (profile == null) {
            System.err.println("[ERROR] Profile '" + profileName + "' not found.");
            return;
        }

        String sourceBranches = String.join(", ", profile.sourceBranches());
        String targetBranches = String.join(", ", profile.targetBranches());

        System.out.println("==================================================");
        System.out.println("              RedLab Auditor v1.0.0               ");
        System.out.println("==================================================");
        System.out.println("[*] Initializing audit process...");
        System.out.println("    -> Profile: " + profileName);
        System.out.println("    -> Source Branches: " + sourceBranches);
        System.out.println("    -> Target Branches: " + targetBranches);
        System.out.println("--------------------------------------------------");

        try {
            auditCommandPort.execute(version, profileName, profile.sourceBranches(), profile.targetBranches());

            System.out.println("--------------------------------------------------");
            System.out.println("[SUCCESS] Audit completed without critical system errors.");
        } catch (Exception e) {
            System.err.println("[ERROR] The audit process failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}