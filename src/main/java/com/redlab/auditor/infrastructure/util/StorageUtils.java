package com.redlab.auditor.infrastructure.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class StorageUtils {

    private static final String PROFILES_DIR = "profiles";
    private static final String REPORTS_DIR = "reports";

    public static Path getProfilesPath() {
        return ensureDirectoryExists(Path.of(PROFILES_DIR));
    }

    public static Path getReportsPath() {
        return ensureDirectoryExists(Path.of(REPORTS_DIR));
    }

    private static Path ensureDirectoryExists(Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            return path;
        } catch (IOException e) {
            throw new RuntimeException("Could not create directory: " + path, e);
        }
    }
}
