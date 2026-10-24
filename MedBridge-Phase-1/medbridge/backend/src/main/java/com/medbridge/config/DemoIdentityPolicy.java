package com.medbridge.config;

import com.medbridge.exception.ApiException;
import java.util.Locale;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Public fictional identities are valid only inside the fully verified local environment. */
@Component
public class DemoIdentityPolicy {
    private final Environment environment;

    public DemoIdentityPolicy(Environment environment) { this.environment = environment; }

    public boolean allows(String email) {
        if (email == null || email.isBlank()) return false;
        if (!email.trim().toLowerCase(Locale.ROOT).endsWith("@medbridge.test")) return true;
        try {
            LocalAuthSafetyConfiguration.requireIsolated(environment);
            return true;
        } catch (RuntimeException denied) {
            // Fail closed without disclosing connection details or configuration errors.
            return false;
        }
    }

    public void requireAllowed(String email) {
        if (!allows(email)) throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
