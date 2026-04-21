package com.redlab.auditor.adapter.in.cli;

import com.redlab.auditor.adapter.in.cli.utils.ProfileFormUtils;
import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.domain.model.ProjectManagerType;
import com.redlab.auditor.domain.model.SourceControlType;
import com.redlab.auditor.infrastructure.security.ProfileStorageService;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

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
        ProfileFormUtils form = new ProfileFormUtils();

        System.out.println("=== Editing Profile: " + profileName + " ===");
        System.out.println("(Press Enter to keep current value)");

        ProjectManagerType pmType = form.askEnumWithDefault(
                "\nProject Manager (1-Redmine, 2-Jira)",
                p.pmType(),
                ProjectManagerType::fromId
        );

        String pmName = pmType.getDisplayName();
        String pmIdentifierLabel = (pmType == ProjectManagerType.REDMINE) ? "Trackers" : "IssueTypes";

        String pmUrl = form.askUrl("\n" + pmName + " URL", p.projectManagerURL());
        String pmToken = form.askToken("\n" + pmName + " Token", p.projectManagerToken());
        Set<Long> pmIssueTypes = form.parseSet(
                "\n" + pmName + " " + pmIdentifierLabel,
                form.toCommaString(p.projectManagerIssueTypes()),
                Long::parseLong
        );

        SourceControlType scType = form.askEnumWithDefault(
                "\nSource Control (1-GitLab, 2-Github)",
                p.scType(),
                SourceControlType::fromId
        );

        String scName = scType.getDisplayName();
        String scGroupLabel = (scType == SourceControlType.GITLAB) ? "Group ID" : "Organization/Owner";

        String scUrl = form.askUrl("\n" + scName + " URL", p.sourceControlURL());
        String scToken = form.askToken("\n" + scName + " Token", p.sourceControlToken());
        String scGroupId = form.askRequired("\n" + scName + " " + scGroupLabel, p.sourceControlGroupId());
        int scRateLimit = form.askInt("\n" + scName + " Rate Limit", p.sourceControlRateLimit());

        Set<Long> projectsToIgnore = form.parseSet(
                "\nProject IDs to Ignore",
                form.toCommaString(p.projectsToIgnore()),
                Long::parseLong
        );

        List<String> sourceBranches = form.parseList(
                "\nSource Branches",
                form.toCommaString(p.sourceBranches()),
                Function.identity()
        );

        List<String> targetBranches = form.parseList(
                "\nTarget Branches",
                form.toCommaString(p.targetBranches()),
                Function.identity()
        );

        String taskRegex = form.askRegex("\nTask Regex", p.taskRegex());

        Profile updatedProfile = new Profile(
                profileName,
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

        profiles.put(profileName, updatedProfile);
        storage.saveProfiles(profiles);

        System.out.println("\n[SUCCESS] Profile '" + profileName + "' updated successfully.");
    }
}