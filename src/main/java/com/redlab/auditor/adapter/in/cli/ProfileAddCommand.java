package com.redlab.auditor.adapter.in.cli;

import com.redlab.auditor.adapter.in.cli.utils.ProfileFormUtils;
import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.domain.model.ProjectManagerType;
import com.redlab.auditor.domain.model.SourceControlType;
import com.redlab.auditor.infrastructure.security.ProfileStorageService;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@CommandLine.Command(name = "add", description = "Adds a new configuration profile.")
public class ProfileAddCommand implements Runnable {

    @Inject
    ProfileStorageService storage;

    @Override
    public void run() {
        ProfileFormUtils form = new ProfileFormUtils();
        Map<String, Profile> profiles = storage.loadProfiles();

        System.out.println("=== New Configuration Profile ===");

        String name = form.askUniqueName("\nProfile Name", profiles.keySet());

        ProjectManagerType pmType = form.askEnum(
                "Select Project Manager (1-Redmine, 2-Jira): ",
                ProjectManagerType::fromId
        );
        String pmName = pmType.getDisplayName();

        String pmUrl = form.askUrl(pmName + " URL", null);
        String pmToken = form.askRequired(pmName + " Token", null);
        Set<Long> pmIssueTypes = form.parseSet(
                pmName + " Trackers/IssueTypes (IDs, e.g., 11,13,17)",
                null,
                Long::parseLong
        );

        SourceControlType scType = form.askEnum(
                "Select Source Control (1-GitLab, 2-Github): ",
                SourceControlType::fromId
        );
        String scName = scType.getDisplayName();

        String scUrl = form.askUrl(scName + " URL", null);
        String scToken = form.askRequired(scName + " Token", null);
        String scGroupId = form.askRequired("Group ID", null);
        int scRateLimit = form.askInt("API Rate Limit", 10);

        Set<Long> projectsToIgnore = form.parseSet(
                "Project IDs to Ignore (IDs, e.g., 11,13,17)",
                null,
                Long::parseLong
        );

        List<String> sourceBranches = form.parseList(
                "Source Branches (e.g., dev,develop)",
                null,
                Function.identity()
        );

        List<String> targetBranches = form.parseList(
                "Target Branches (e.g., main,master)",
                null,
                Function.identity()
        );

        String taskRegex = form.askRegex("Commit Pattern Regex (e.g., #(\\d+))", null);

        Profile newProfile = new Profile(
                name,
                pmType,
                scType,
                pmUrl,
                pmToken,
                pmIssueTypes,
                scUrl,
                scToken,
                scGroupId,
                scRateLimit,
                projectsToIgnore,
                sourceBranches,
                targetBranches,
                taskRegex
        );

        profiles.put(name, newProfile);
        storage.saveProfiles(profiles);

        System.out.println("\n[SUCCESS] Profile '" + name + "' saved.");
    }
}