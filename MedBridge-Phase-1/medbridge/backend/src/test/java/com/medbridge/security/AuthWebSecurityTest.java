package com.medbridge.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medbridge.config.RegistrationPolicy;
import com.medbridge.config.SecurityConfig;
import com.medbridge.controller.AuthController;
import com.medbridge.controller.RoleDemoController;
import com.medbridge.dto.UserResponse;
import com.medbridge.model.Role;
import com.medbridge.model.User;
import com.medbridge.repository.UserRepository;
import com.medbridge.service.AuthService;
import io.jsonwebtoken.JwtException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AuthController.class, RoleDemoController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RegistrationPolicy.class})
@TestPropertySource(properties = {"app.frontend-url=http://127.0.0.1:5173", "app.allow-registration=false"})
class AuthWebSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean AuthService auth;
    @MockitoBean JwtService jwt;
    @MockitoBean UserRepository users;

    @Test void disabledDirectRegistrationReturnsJsonWithoutCallingTheService() throws Exception {
        String body = json.writeValueAsString(Map.of("name", "Fictional Donor", "email", "donor@example.test",
            "password", UUID.randomUUID().toString(), "role", "DONOR"));
        mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error").value("Registration is unavailable in this environment."));
        verifyNoInteractions(auth, users, jwt);
    }

    private String bearer(Role role, boolean verified) {
        User user = new User();
        user.setEmail("fictional@example.test"); user.setRole(role); user.setVerified(verified);
        String token = UUID.randomUUID().toString();
        when(jwt.subject(token)).thenReturn(user.getEmail());
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(auth.me(user.getEmail())).thenReturn(UserResponse.from(user));
        return "Bearer " + token;
    }

    @Test void databaseDonorRoleCannotUseNgoOrAdminEndpoints() throws Exception {
        String bearer = bearer(Role.DONOR, false);
        mvc.perform(get("/api/roles/donor").header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/api/roles/ngo").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/roles/admin").header("Authorization", bearer)).andExpect(status().isForbidden());
    }

    @Test void unverifiedNgoHasAProfileButNoPrivilegedRoleAccess() throws Exception {
        String bearer = bearer(Role.NGO, false);
        mvc.perform(get("/api/auth/me").header("Authorization", bearer)).andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("NGO")).andExpect(jsonPath("$.verified").value(false))
            .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/roles/ngo").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/roles/donor").header("Authorization", bearer)).andExpect(status().isForbidden());
    }

    @Test void verifiedNgoStillCannotUseDonorEndpoints() throws Exception {
        String bearer = bearer(Role.NGO, true);
        mvc.perform(get("/api/roles/ngo").header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/api/roles/donor").header("Authorization", bearer)).andExpect(status().isForbidden());
    }

    @Test void missingAndInvalidTokensReturn401() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        String token = UUID.randomUUID().toString();
        when(jwt.subject(token)).thenThrow(new JwtException("Invalid token"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        verifyNoInteractions(auth);
    }
}
