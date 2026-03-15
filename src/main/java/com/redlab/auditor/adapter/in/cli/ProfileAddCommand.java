package com.redlab.auditor.adapter.in.cli;

import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.infrastructure.security.ProfileStorageService;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.Map;
import java.util.Scanner;

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

						System.out.print("Redmine URL: ");
						String redmineUrl = scanner.nextLine();

						System.out.println("Redmine Token: ");
						String redmineToken = scanner.nextLine();

						System.out.println("Redmine Trackers (Separated by commas, empty = all): ");
						String redmineTrackers = scanner.nextLine();

						System.out.println("Gitlab URL: ");
						String gitlabUrl = scanner.nextLine();

						System.out.println("Gitlab Token: ");
						String gitlabToken = scanner.nextLine();

						System.out.println("Gitlab Group ID: ");
						String gitlabGroupId = scanner.nextLine();

						System.out.println("Gitlab Rate Limit: ");
						int gitlabRateLimit = Integer.parseInt(scanner.nextLine());

						System.out.println("Main Target Branch: ");
						String mainTargetBranch = scanner.nextLine();

						System.out.println("Secondary Target Branch (optional): ");
						String secondaryTargetBranch = scanner.nextLine();

						System.out.println("Task Regex: ");
						String taskRegex = scanner.nextLine();

						Profile newProfile = new Profile(name,
																																						 redmineUrl,
																																						 redmineToken,
																																							redmineTrackers,
																																							gitlabUrl,
																																							gitlabToken,
																																							gitlabGroupId,
																																							gitlabRateLimit,
																																							mainTargetBranch,
																																							secondaryTargetBranch,
																																							taskRegex);

						Map<String, Profile> profiles = storage.loadProfiles();
						profiles.put(name, newProfile);
						storage.saveProfiles(profiles);

						System.out.println("Profile " + name + " successfully saved.");
			}
}
