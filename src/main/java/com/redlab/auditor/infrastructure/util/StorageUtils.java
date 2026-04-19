package com.redlab.auditor.infrastructure.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class StorageUtils {

    private static final String REDLAB_HIDDEN_DIR = ".redlab";

    public static Path getProfilesPath() {
        String userHome = System.getProperty("user.home");
        Path path = Paths.get(userHome, REDLAB_HIDDEN_DIR);
        return ensureDirectoryExists(path);
    }

    public static Path getReportsPath() {
        String currentWorkingDir = System.getProperty("user.dir");
        return Paths.get(currentWorkingDir);
    }

    private static Path ensureDirectoryExists(Path path) {
        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path);
            }
            return path;
        } catch (IOException e) {
            throw new RuntimeException("Could not create directory: " + path, e);
        }
    }
}