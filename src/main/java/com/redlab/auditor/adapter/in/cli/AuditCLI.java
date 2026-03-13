package com.redlab.auditor.adapter.in.cli;

import com.redlab.auditor.usecase.GenerateAuditReportUseCase;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import jakarta.inject.Inject;

@Command(name = "redlab", mixinStandardHelpOptions = true, version = "1.0.0",
        description = "Executes a cross-reference audit between Redmine tasks and GitLab commits.")
public class AuditCLI implements Runnable {

    @Option(names = {"-v", "--version"}, required = true,
            description = "The target release version in Redmine (e.g., 1.0.0).")
    String version;

    @Option(names = {"-p", "--production-branch"}, required = true,
            description = "The production branch containing the reference tag (e.g., main or master).")
    String productionBranch;

    @Option(names = {"-t", "--target-branch"}, required = true,
            description = "The target branch containing the new commits to be audited (e.g., dev or pre-update).")
    String targetBranch;

    @Inject
    GenerateAuditReportUseCase useCase;

    @Override
    public void run() {
        System.out.println("==================================================");
        System.out.println("              RedLab Auditor v1.0.0               ");
        System.out.println("==================================================");
        System.out.println("[*] Initializing audit process...");
        System.out.println("    -> Target Version: " + version);
        System.out.println("    -> Production Branch: " + productionBranch);
        System.out.println("    -> Target Branch: " + targetBranch);
        System.out.println("--------------------------------------------------");

        try {
            useCase.execute(version, productionBranch, targetBranch);

            System.out.println("--------------------------------------------------");
            System.out.println("[SUCCESS] Audit completed without critical system errors.");
        } catch (Exception e) {
            System.err.println("[ERROR] The audit process failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}