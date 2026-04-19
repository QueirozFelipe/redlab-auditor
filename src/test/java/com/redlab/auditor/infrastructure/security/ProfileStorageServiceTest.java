package com.redlab.auditor.infrastructure.security;

import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.domain.model.ProjectManagerType;
import com.redlab.auditor.domain.model.SourceControlType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@QuarkusTest
class ProfileStorageServiceTest {

    @Inject
    ProfileStorageService storageService;

    @TempDir
    Path tempDir;

    private String originalHome;

    @BeforeEach
    void setUp() {
        originalHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
    }

    @Test
    void shouldSaveAndLoadEncryptedProfilesCorrectly() {
        String profileName = "test-profile";
        Profile profile = new Profile(
                profileName, ProjectManagerType.REDMINE, SourceControlType.GITLAB,
                "http://redmine.local", "token-pm", Set.of(1L),
                 "http://gitlab.local", "token-sc", "group-id", 10,
                Set.of(), List.of(), List.of(),"TASK-\\d+"
        );
        Map<String, Profile> profilesToSave = Map.of(profileName, profile);

        storageService.saveProfiles(profilesToSave);
        Map<String, Profile> loadedProfiles = storageService.loadProfiles();

        assertNotNull(loadedProfiles);
        assertEquals(1, loadedProfiles.size());
        assertEquals(profile.projectManagerURL(), loadedProfiles.get(profileName).projectManagerURL());

        Path expectedFile = tempDir.resolve(".redlab").resolve("profiles.dat");
        assertTrue(Files.exists(expectedFile), "profiles.dat should have been created in directory ~/.redlab/");
    }

    @Test
    void shouldReturnEmptyMapWhenFileDoesNotExist() {
        Map<String, Profile> result = storageService.loadProfiles();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldHandleEncryptionFailureGracefully() throws IOException {
        storageService.saveProfiles(Map.of("p1", mock(Profile.class)));

        String originalUser = System.getProperty("user.name");
        try {
            System.setProperty("user.name", "another-user");
            Map<String, Profile> result = storageService.loadProfiles();
            assertTrue(result.isEmpty());
        } finally {
            System.setProperty("user.name", originalUser);
        }
    }
}