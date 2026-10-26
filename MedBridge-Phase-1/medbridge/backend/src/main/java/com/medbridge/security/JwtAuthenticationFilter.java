package com.medbridge.security;

import com.medbridge.repository.UserRepository;
import com.medbridge.config.DemoIdentityPolicy;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserRepository users;
    private final DemoIdentityPolicy demoIdentities;
    public JwtAuthenticationFilter(JwtService jwt, UserRepository users, DemoIdentityPolicy demoIdentities) {
        this.jwt=jwt; this.users=users; this.demoIdentities=demoIdentities;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                               FilterChain chain) throws ServletException, IOException {
        String header=request.getHeader("Authorization");
        if (header!=null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication()==null) {
            try {
                String email=jwt.subject(header.substring(7));
                if (demoIdentities.allows(email)) users.findByEmailIgnoreCase(email)
                    .filter(user -> demoIdentities.allows(user.getEmail())).ifPresent(user -> {
                    var authorities=(user.getRole()==com.medbridge.model.Role.NGO && !user.isVerified())
                        ? List.<SimpleGrantedAuthority>of()
                        : List.of(new SimpleGrantedAuthority("ROLE_"+user.getRole().name()));
                    var auth=new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
            } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ignored) {
                // Invalid, expired or tampered tokens are treated as unauthenticated.
            }
        }
        chain.doFilter(request,response);
    }
}
