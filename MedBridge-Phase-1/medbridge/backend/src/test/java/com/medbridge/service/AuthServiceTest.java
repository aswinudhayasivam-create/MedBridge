package com.medbridge.service;

import com.medbridge.dto.RegisterRequest;
import com.medbridge.exception.ApiException;
import com.medbridge.model.Role;
import com.medbridge.model.User;
import com.medbridge.repository.NgoProfileRepository;
import com.medbridge.repository.UserRepository;
import com.medbridge.security.JwtService;
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
    private final AuthService auth = new AuthService(users, ngos, encoder, jwt);

    @Test
    void publicAdminRegistrationIsRejectedWithoutWrites() {
        RegisterRequest request = new RegisterRequest("Test Admin", "admin@example.test",
            "test-password-123", Role.ADMIN, null, null, null, null, null);
        assertThrows(ApiException.class, () -> auth.register(request));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void duplicateDonorEmailIsRejectedWithoutWrites() {
        when(users.existsByEmailIgnoreCase("donor@example.test")).thenReturn(true);
        RegisterRequest request = new RegisterRequest("Test Donor", "DONOR@example.test",
            "test-password-123", Role.DONOR, null, null, null, null, null);
        assertThrows(ApiException.class, () -> auth.register(request));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void invalidCredentialsDoNotIssueJwt() {
        User donor = new User();
        donor.setEmail("donor@example.test");
        donor.setPasswordHash("stored-hash");
        when(users.findByEmailIgnoreCase("donor@example.test")).thenReturn(Optional.of(donor));
        when(encoder.matches("wrong-password", "stored-hash")).thenReturn(false);
        assertThrows(ApiException.class,
            () -> auth.login(new com.medbridge.dto.LoginRequest("donor@example.test", "wrong-password")));
        verifyNoInteractions(jwt);
    }

    @Test
    void ngoRegistrationRequiresOrganizationDetails() {
        RegisterRequest request = new RegisterRequest("Test NGO", "ngo@example.test",
            "test-password-123", Role.NGO, null, null, "", "", "");
        assertThrows(ApiException.class, () -> auth.register(request));
        verify(users, never()).saveAndFlush(any());
    }
}
