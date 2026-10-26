package com.medbridge.service;

import com.medbridge.dto.RegisterRequest;
import com.medbridge.exception.ApiException;
import com.medbridge.model.Role;
import com.medbridge.model.User;
import com.medbridge.repository.NgoProfileRepository;
import com.medbridge.repository.UserRepository;
import com.medbridge.security.JwtService;
import com.medbridge.config.RegistrationPolicy;
import com.medbridge.config.DemoIdentityPolicy;
import com.medbridge.config.LocalAuthSafetyConfigurationTest;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final NgoProfileRepository ngos = mock(NgoProfileRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final JwtService jwt = mock(JwtService.class);
    private final AuthService auth = new AuthService(users, ngos, encoder, jwt,
        new RegistrationPolicy(LocalAuthSafetyConfigurationTest.isolated().withProperty("app.allow-registration", "true")),
        new DemoIdentityPolicy(LocalAuthSafetyConfigurationTest.isolated()));

    @Test
    void disabledRegistrationCannotReadOrWriteAccounts() {
        AuthService disabled = new AuthService(users, ngos, encoder, jwt,
            new RegistrationPolicy(new org.springframework.mock.env.MockEnvironment()),
            new DemoIdentityPolicy(new org.springframework.mock.env.MockEnvironment()));
        RegisterRequest request = new RegisterRequest("Fictional Donor", "donor@example.test",
            java.util.UUID.randomUUID().toString(), Role.DONOR, null, null, null, null, null);
        ApiException error = assertThrows(ApiException.class, () -> disabled.register(request));
        assertEquals(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        verifyNoInteractions(users, ngos, encoder, jwt);
    }

    @Test
    void duplicateNgoLicenseIsRejectedWithoutWrites() {
        when(ngos.existsByLicenseNoIgnoreCase("FICTIONAL-LICENSE")).thenReturn(true);
        RegisterRequest request = new RegisterRequest("Fictional NGO", "ngo@example.test",
            java.util.UUID.randomUUID().toString(), Role.NGO, null, null, "Fictional NGO", "FICTIONAL-LICENSE", "Test Area");
        ApiException error = assertThrows(ApiException.class, () -> auth.register(request));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, error.getStatus());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void registrationHashesPasswordsAndNeverVerifiesNewNgos() {
        String raw = java.util.UUID.randomUUID().toString();
        var bcrypt = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4);
        AuthService service = new AuthService(users, ngos, bcrypt, jwt,
            new RegistrationPolicy(LocalAuthSafetyConfigurationTest.isolated().withProperty("app.allow-registration", "true")),
        new DemoIdentityPolicy(LocalAuthSafetyConfigurationTest.isolated()));
        when(users.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.register(new RegisterRequest("Fictional NGO", "ngo@example.test", raw,
            Role.NGO, null, null, "Fictional NGO", "FICTIONAL-LICENSE", "Test Area"));
        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertNotEquals(raw, saved.getPasswordHash());
        assertTrue(bcrypt.matches(raw, saved.getPasswordHash()));
        assertFalse(saved.isVerified());
        assertFalse(result.user().verified());
    }

    @Test
    void publicAdminRegistrationIsRejectedWithoutWrites() {
        RegisterRequest request = new RegisterRequest("Test Admin", "admin@example.test",
            java.util.UUID.randomUUID().toString(), Role.ADMIN, null, null, null, null, null);
        assertThrows(ApiException.class, () -> auth.register(request));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void duplicateDonorEmailIsRejectedWithoutWrites() {
        when(users.existsByEmailIgnoreCase("donor@example.test")).thenReturn(true);
        RegisterRequest request = new RegisterRequest("Test Donor", "DONOR@example.test",
            java.util.UUID.randomUUID().toString(), Role.DONOR, null, null, null, null, null);
        assertThrows(ApiException.class, () -> auth.register(request));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void invalidCredentialsDoNotIssueJwt() {
        User donor = new User();
        donor.setEmail("donor@example.test");
        donor.setPasswordHash("stored-hash");
        when(users.findByEmailIgnoreCase("donor@example.test")).thenReturn(Optional.of(donor));
        String wrongPassword = java.util.UUID.randomUUID().toString();
        when(encoder.matches(wrongPassword, "stored-hash")).thenReturn(false);
        assertThrows(ApiException.class,
            () -> auth.login(new com.medbridge.dto.LoginRequest("donor@example.test", wrongPassword)));
        verifyNoInteractions(jwt);
    }

    @Test
    void ngoRegistrationRequiresOrganizationDetails() {
        RegisterRequest request = new RegisterRequest("Test NGO", "ngo@example.test",
            java.util.UUID.randomUUID().toString(), Role.NGO, null, null, "", "", "");
        assertThrows(ApiException.class, () -> auth.register(request));
        verify(users, never()).saveAndFlush(any());
    }
}
