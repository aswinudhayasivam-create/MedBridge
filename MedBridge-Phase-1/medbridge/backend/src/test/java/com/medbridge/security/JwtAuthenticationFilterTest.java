package com.medbridge.security;

import com.medbridge.config.DemoIdentityPolicy;
import org.springframework.mock.env.MockEnvironment;
import com.medbridge.model.Role;
import com.medbridge.model.User;
import com.medbridge.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    private User ngo(boolean verified) {
        User user = new User();
        user.setEmail("ngo@example.test");
        user.setRole(Role.NGO);
        user.setVerified(verified);
        return user;
    }

    @Test
    void unverifiedNgoGetsNoNgoAuthority() throws Exception {
        JwtService jwt = mock(JwtService.class);
        UserRepository users = mock(UserRepository.class);
        when(jwt.subject("valid")).thenReturn("ngo@example.test");
        when(users.findByEmailIgnoreCase("ngo@example.test")).thenReturn(Optional.of(ngo(false)));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/roles/ngo");
        request.addHeader("Authorization", "Bearer valid");
        new JwtAuthenticationFilter(jwt, users, new DemoIdentityPolicy(new MockEnvironment())).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().isEmpty());
    }

    @Test
    void verifiedNgoGetsNgoAuthority() throws Exception {
        JwtService jwt = mock(JwtService.class);
        UserRepository users = mock(UserRepository.class);
        when(jwt.subject("valid")).thenReturn("ngo@example.test");
        when(users.findByEmailIgnoreCase("ngo@example.test")).thenReturn(Optional.of(ngo(true)));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/roles/ngo");
        request.addHeader("Authorization", "Bearer valid");
        new JwtAuthenticationFilter(jwt, users, new DemoIdentityPolicy(new MockEnvironment())).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities()
            .stream().anyMatch(a -> a.getAuthority().equals("ROLE_NGO")));
    }

    @Test
    void missingBearerTokenDoesNotAuthenticate() throws Exception {
        JwtService jwt = mock(JwtService.class);
        UserRepository users = mock(UserRepository.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        new JwtAuthenticationFilter(jwt, users, new DemoIdentityPolicy(new MockEnvironment())).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwt, users);
    }
}
