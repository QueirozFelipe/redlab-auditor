package com.redlab.auditor.adapter.in.cli;

import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.domain.model.ProjectManagerType;
import com.redlab.auditor.domain.model.SourceControlType;
import com.redlab.auditor.infrastructure.security.ProfileStorageService;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Function;
import java.util.stream.Collectors;

@Command(name = "edit", description = "Edits an existing profile.")
public class ProfileEditCommand implements Runnable {

    @Inject
    ProfileStorageService storage;

    @Parameters(index = "0", description = "Name of the profile to be edited.")
    String profileName;

    @Override
    public void run() {
        Map<String, Profile> profiles = storage.loadProfiles();

        if (!profiles.containsKey(profileName)) {
            System.err.println("[ERROR] Profile '" + profileName + "' not found.");
            return;
        }

        Profile p = profiles.get(profileName);
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== Editing Profile: " + profileName + " ===");
        System.out.println("(Press Enter to keep current value)");

        System.out.println("\nProject Manager (1-Redmine, 2-Jira, 3-Mock)");
        String pmInput = ask(scanner, "Current", p.pmType().getDisplayName());

        ProjectManagerType pmType = isNumber(pmInput)
                ? ProjectManagerType.fromId(Integer.parseInt(pmInput))
                : p.pmType();

        String redmineUrl = "";
        String redmineToken = "";
        String redmineTrackers = "";

        if (pmType == ProjectManagerType.REDMINE) {
            redmineUrl = ask(scanner, "Redmine URL", p.redmineUrl());
            redmineToken = askToken(scanner, "Redmine Token", p.redmineToken());
            redmineTrackers = ask(scanner, "Redmine Trackers", p.redmineTrackers());
        }

        System.out.println("\nSource Control (1-GitLab, 2-Github, 3-Mock)");
        String scInput = ask(scanner, "Current", p.scType().getDisplayName());

        SourceControlType scType = isNumber(scInput)
                ? SourceControlType.fromId(Integer.parseInt(scInput))
                : p.scType();

        String gitlabUrl = "";
        String gitlabToken = "";
        String gitlabGroupId = "";
        int gitlabRateLimit = 10;
        List<Long> projectsToIgnore = List.of();
        List<String> sourceBranches = List.of();
        List<String> targetBranches = List.of();
        String taskRegex = "";

        if (scType == SourceControlType.GITLAB) {
            gitlabUrl = ask(scanner, "Gitlab URL", p.gitlabUrl());
            gitlabToken = askToken(scanner, "Gitlab Token", p.gitlabToken());
            gitlabGroupId = ask(scanner, "Gitlab Group ID", p.gitlabGroupId());

            gitlabRateLimit = Integer.parseInt(
                    ask(scanner, "Gitlab Rate Limit", String.valueOf(p.gitlabRateLimit()))
            );

            String currentIgnoredStr = (p.projectsToIgnore() != null)
                    ? p.projectsToIgnore().stream().map(String::valueOf).collect(Collectors.joining(","))
                    : "";
            projectsToIgnore = parseList(ask(scanner, "Projects To Ignore", currentIgnoredStr), Long::parseLong);

            String currentSourceStr = String.join(",", p.sourceBranches());
            String currentTargetStr = String.join(",", p.targetBranches());

            sourceBranches = parseList(ask(scanner, "Source Branches", currentSourceStr), Function.identity());
            targetBranches = parseList(ask(scanner, "Target Branches", currentTargetStr), Function.identity());

            taskRegex = ask(scanner, "Task Regex", p.taskRegex());
        }

        Profile updatedProfile = new Profile(
                profileName, pmType, scType,
                redmineUrl, redmineToken, redmineTrackers,
                gitlabUrl, gitlabToken, gitlabGroupId, gitlabRateLimit,
                projectsToIgnore, sourceBranches, targetBranches, taskRegex
        );

        profiles.put(profileName, updatedProfile);
        storage.saveProfiles(profiles);

        System.out.println("\n[SUCCESS] Profile '" + profileName + "' updated successfully.");
    }

    private boolean isNumber(String input) {
        if (input == null || input.isBlank()) return false;
        return input.matches("\\d+");
    }

    private String ask(Scanner scanner, String label, String currentValue) {
        System.out.print(label + " [" + (currentValue == null ? "" : currentValue) + "]: ");
        String input = scanner.nextLine();
        return input.isBlank() ? currentValue : input;
    }

    private String askToken(Scanner scanner, String label, String currentToken) {
        String masked = mask(currentToken);
        System.out.print(label + " [" + masked + "]: ");
        String input = scanner.nextLine();
        return input.isBlank() ? currentToken : input;
    }

    private String mask(String token) {
        if (token == null || token.isBlank()) return "NOT SET";
        if (token.length() < 8) return "****";
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }

    private <T> List<T> parseList(String input, Function<String, T> mapper) {
        if (input == null || input.isBlank()) return List.of();
        return Arrays.stream(input.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(mapper)
                .collect(Collectors.toList());
    }
}