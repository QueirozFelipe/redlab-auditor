package com.redlab.auditor.adapter.in.cli;

import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.infrastructure.security.ProfileStorageService;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.Map;
import java.util.Scanner;

@Command(name = "profile-edit", description = "Edits an existing profile.")
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

						String url = ask(scanner, "Redmine URL", p.redmineUrl());
						String rToken = askToken(scanner, "Redmine Token", p.redmineToken());
						String rTrackers = askToken(scanner, "Redmine Trackers", p.redmineTrackers());
						String gUrl = ask(scanner, "Gitlab URL", p.gitlabUrl());
						String gToken = askToken(scanner, "Gitlab Token", p.gitlabToken());
						String gGroupId = ask(scanner, "Gitlab Group ID", p.gitlabGroupId());

						int gRateLimit = Integer.parseInt(ask(scanner, "Gitlab Rate Limit", String.valueOf(p.gitlabRateLimit())));

						String mainTarget = ask(scanner, "Main Target Branch", p.mainTargetBranch());
						String secTarget = ask(scanner, "Secondary Target Branch", p.secondaryTargetBranch());
						String regex = ask(scanner, "Task Regex", p.taskRegex());

						Profile updatedProfile = new Profile(
								profileName, url, rToken, rTrackers, gUrl, gToken,
								gGroupId, gRateLimit, mainTarget, secTarget, regex
						);

						profiles.put(profileName, updatedProfile);
						storage.saveProfiles(profiles);

						System.out.println("\n[SUCCESS] Profile '" + profileName + "' updated successfully.");
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
						if (token == null || token.length() < 8) return "****";
						return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
			}
}