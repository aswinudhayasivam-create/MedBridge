package com.medbridge.config;

import com.medbridge.exception.ApiException;
import com.medbridge.review.ReviewModePolicy;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Locale;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Public fictional identities are valid only inside the fully verified local environment. */
@Component
public class DemoIdentityPolicy {
    private final Environment environment;

    private final ReviewModePolicy review;
    public DemoIdentityPolicy(Environment environment) { this(environment, new ReviewModePolicy(environment)); }
    @Autowired public DemoIdentityPolicy(Environment environment, ReviewModePolicy review) { this.environment = environment; this.review = review; }

    public boolean allows(String email) {
        if (email == null || email.isBlank()) return false;
        if (review.isReviewIdentity(email)) return review.allowsIdentity(email);
        if (!email.trim().toLowerCase(Locale.ROOT).endsWith("@medbridge.test")) return !review.requested();
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
