package com.medbridge.config;

import com.medbridge.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class RegistrationPolicyTest {
    @Test void disabledByDefault() {
        ApiException error = assertThrows(ApiException.class,
            () -> new RegistrationPolicy(new MockEnvironment()).requireRegistration());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
    }

    @Test void aFlagAloneDoesNotEnableRemoteOrHostedRegistration() {
        assertThrows(IllegalStateException.class, () -> new RegistrationPolicy(
            new MockEnvironment().withProperty("app.allow-registration", "true")).requireRegistration());
    }

    @Test void explicitLocalOptInIsRequired() {
        MockEnvironment environment = LocalAuthSafetyConfigurationTest.isolated();
        RegistrationPolicy policy = new RegistrationPolicy(environment);
        assertThrows(ApiException.class, policy::requireRegistration);
        environment.setProperty("app.allow-registration", "true");
        assertDoesNotThrow(policy::requireRegistration);
        environment.setProperty("app.allow-registration", "false");
        assertThrows(ApiException.class, policy::requireRegistration);
    }
}
