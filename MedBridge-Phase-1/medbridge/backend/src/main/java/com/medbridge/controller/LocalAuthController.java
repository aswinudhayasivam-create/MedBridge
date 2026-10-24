package com.medbridge.controller;

import com.medbridge.config.LocalAuthSafetyConfiguration;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local-auth")
public class LocalAuthController {
    private final Environment environment;
    public LocalAuthController(Environment environment) { this.environment = environment; }
    @GetMapping("/api/auth/local-mode")
    public Map<String, Object> localMode() {
        LocalAuthSafetyConfiguration.requireIsolated(environment);
        return Map.of("environment", "isolated-local-auth", "demoEnabled", environment.getProperty("app.demo-workflows", Boolean.class, false), "registrationEnabled",
            environment.getProperty("app.allow-registration", Boolean.class, false));
    }
}
