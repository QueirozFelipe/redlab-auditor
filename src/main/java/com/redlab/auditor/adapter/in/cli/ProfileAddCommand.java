package com.redlab.auditor.adapter.in.cli;

import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.domain.model.ProjectManagerType;
import com.redlab.auditor.domain.model.SourceControlType;
import com.redlab.auditor.infrastructure.security.ProfileStorageService;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Function;
import java.util.stream.Collectors;

@CommandLine.Command(name = "add", description = "Adds a new configuration profile.")
public class ProfileAddCommand implements Runnable {

    @Inject
    ProfileStorageService storage;

    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== New Configuration Profile ===");

        System.out.print("Profile Name: ");
        String name = scanner.nextLine();

        // 1. SELEÇÃO PM
        System.out.println("\nSelect Project Manager (1-Redmine, 2-Jira, 3-Mock): ");
        int pmOption = Integer.parseInt(scanner.nextLine());
        ProjectManagerType pmType = ProjectManagerType.fromId(pmOption);

        // DECLARE ANTES COM VALORES DEFAULT
        String redmineUrl = "";
        String redmineToken = "";
        String redmineTrackers = "";

        if (pmType == ProjectManagerType.REDMINE) {
            System.out.print("Redmine URL: ");
            redmineUrl = scanner.nextLine();
            System.out.print("Redmine Token: ");
            redmineToken = scanner.nextLine();
            System.out.print("Redmine Trackers: ");
            redmineTrackers = scanner.nextLine();
        }

        System.out.println("\nSelect Source Control (1-GitLab, 2-Github, 3-Mock): ");
        int scOption = Integer.parseInt(scanner.nextLine());
        SourceControlType scType = SourceControlType.fromId(scOption);

        String gitlabUrl = "";
        String gitlabToken = "";
        String gitlabGroupId = "";
        int gitlabRateLimit = 10;
        List<Long> projectsToIgnore = List.of();
        List<String> sourceBranches = List.of();
        List<String> targetBranches = List.of();
        String taskRegex = "";

        if (scType == SourceControlType.GITLAB) {
            System.out.print("Gitlab URL: ");
            gitlabUrl = scanner.nextLine();
            System.out.print("Gitlab Token: ");
            gitlabToken = scanner.nextLine();
            System.out.print("Gitlab Group ID: ");
            gitlabGroupId = scanner.nextLine();
            System.out.print("Gitlab Rate Limit [10]: ");
            String rateStr = scanner.nextLine();
            gitlabRateLimit = rateStr.isBlank() ? 10 : Integer.parseInt(rateStr);

            System.out.print("Project IDs to Ignore: ");
            projectsToIgnore = parseList(scanner.nextLine(), Long::parseLong);

            System.out.print("Source Branches (dev,develop): ");
            sourceBranches = parseList(scanner.nextLine(), Function.identity());

            System.out.print("Target Branches (main,master): ");
            targetBranches = parseList(scanner.nextLine(), Function.identity());

            System.out.print("Commit Pattern Regex: ");
            taskRegex = scanner.nextLine();
        }

        Profile newProfile = new Profile(
                name,
                pmType,
                scType,
                redmineUrl,
                redmineToken,
                redmineTrackers,
                gitlabUrl,
                gitlabToken,
                gitlabGroupId,
                gitlabRateLimit,
                projectsToIgnore,
                sourceBranches,
                targetBranches,
                taskRegex
        );

        Map<String, Profile> profiles = storage.loadProfiles();
        profiles.put(name, newProfile);
        storage.saveProfiles(profiles);

        System.out.println("\n[SUCCESS] Profile '" + name + "' saved.");
    }

    private <T> List<T> parseList(String input, Function<String, T> mapper) {
        if (input == null || input.isBlank()) {
            return List.of();
        }
        return Arrays.stream(input.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(mapper)
                .collect(Collectors.toList());
    }
}
