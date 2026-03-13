package com.redlab.auditor.infrastructure.config;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "redlab")
public interface RedLabAuditorConfig {

    RedmineConfig redmine();
    GitLabConfig gitlab();

    interface RedmineConfig {
        String url();
        String apiKey();
    }

    interface GitLabConfig {
        String url();
        String personalAccessToken();
        String groupId();
    }
}
