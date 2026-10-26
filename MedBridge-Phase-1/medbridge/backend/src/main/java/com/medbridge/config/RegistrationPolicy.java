package com.medbridge.config;

import com.medbridge.exception.ApiException;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RegistrationPolicy {
    private final Environment environment;

    public RegistrationPolicy(Environment environment) { this.environment = environment; }

    public void requireRegistration() {
        if (!environment.getProperty("app.allow-registration", Boolean.class, false)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Registration is unavailable in this environment.");
        }
        LocalAuthSafetyConfiguration.requireIsolated(environment);
    }
}
