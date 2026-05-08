/*
 * Copyright 2026 Felipe Queiroz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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