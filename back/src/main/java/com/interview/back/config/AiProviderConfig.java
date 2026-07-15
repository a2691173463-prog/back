package com.interview.back.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiProviderConfig {
    private PythonAgent pythonAgent = new PythonAgent();
    private String internalSecret = "local-agent-secret-change-me";

    @Data
    public static class PythonAgent {
        private String baseUrl = "http://localhost:8000";
        private int timeoutSeconds = 120;
    }
}
