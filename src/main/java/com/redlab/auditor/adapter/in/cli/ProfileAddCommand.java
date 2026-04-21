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
                "\nSelect Project Manager (1-Redmine, 2-Jira): ",
                ProjectManagerType::fromId
        );
        String pmName = pmType.getDisplayName();
        String pmIdentifierLabel = (pmType == ProjectManagerType.REDMINE) ? "Trackers" : "IssueTypes";

        String pmUrl = form.askUrl("\n" + pmName + " URL", null);
        String pmToken = form.askRequired("\n" + pmName + " Token", null);
        Set<Long> pmIssueTypes = form.parseSet(
                "\n" + pmName + " " + pmIdentifierLabel + "(IDs, e.g., 11,13,17)",
                null,
                Long::parseLong
        );

        SourceControlType scType = form.askEnum(
                "\nSelect Source Control (1-GitLab, 2-Github): ",
                SourceControlType::fromId
        );
        String scName = scType.getDisplayName();
        String scGroupLabel = (scType == SourceControlType.GITLAB) ? "Group ID" : "Organization/Owner";

        String scUrl = form.askUrl("\n" + scName + " URL", null);
        String scToken = form.askRequired("\n" + scName + " Token", null);
        String scGroupId = form.askRequired("\n" + scGroupLabel, null);
        int scRateLimit = form.askInt("\nAPI Rate Limit", 10);

        Set<Long> projectsToIgnore = form.parseSet(
                "\nProject IDs to Ignore (IDs, e.g., 11,13,17)",
                null,
                Long::parseLong
        );

        List<String> sourceBranches = form.parseList(
                "\nSource Branches (e.g., dev,develop)",
                null,
                Function.identity()
        );

        List<String> targetBranches = form.parseList(
                "\nTarget Branches (e.g., main,master)",
                null,
                Function.identity()
        );

        String taskRegex = form.askRegex("\nCommit Pattern Regex (e.g., #(\\d+))", null);

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