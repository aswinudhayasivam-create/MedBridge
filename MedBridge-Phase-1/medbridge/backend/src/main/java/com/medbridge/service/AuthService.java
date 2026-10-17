package com.medbridge.service;

import com.medbridge.dto.*;
import com.medbridge.model.*;
import com.medbridge.repository.*;
import com.medbridge.security.JwtService;
import com.medbridge.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Service
public class AuthService {
    private final UserRepository users;
    private final NgoProfileRepository ngos;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    public AuthService(UserRepository users, NgoProfileRepository ngos, PasswordEncoder passwords, JwtService jwt) {
        this.users=users; this.ngos=ngos; this.passwords=passwords; this.jwt=jwt;
    }
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.role()==Role.ADMIN) throw new ApiException(HttpStatus.BAD_REQUEST,"Admin registration is disabled");
        String email=request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) throw new ApiException(HttpStatus.CONFLICT,"Email already registered");
        if (request.role()==Role.NGO) {
            if (blank(request.orgName())||blank(request.licenseNo())||blank(request.serviceArea()))
                throw new ApiException(HttpStatus.BAD_REQUEST,"NGO organization name, license and service area are required");
            if (request.orgName().length()>200 || request.licenseNo().length()>100 || request.serviceArea().length()>200)
                throw new ApiException(HttpStatus.BAD_REQUEST,"NGO details exceed allowed length");
            if (ngos.existsByLicenseNoIgnoreCase(request.licenseNo().trim()))
                throw new ApiException(HttpStatus.CONFLICT,"NGO license already registered");
        }
        User user=new User();
        user.setName(request.name().trim()); user.setEmail(email);
        user.setPasswordHash(passwords.encode(request.password()));
        user.setRole(request.role()); user.setVerified(false);
        user.setLatitude(request.latitude()); user.setLongitude(request.longitude());
        if (request.role()==Role.NGO) {
            NgoProfile profile=new NgoProfile();
            profile.setOrgName(request.orgName().trim()); profile.setLicenseNo(request.licenseNo().trim());
            profile.setServiceArea(request.serviceArea().trim()); user.setNgoProfile(profile);
        }
        User saved=users.saveAndFlush(user);
        return new AuthResponse(jwt.generate(saved),"Bearer",jwt.expirySeconds(),UserResponse.from(saved));
    }
    @Transactional(readOnly=true)
    public AuthResponse login(LoginRequest request) {
        User user=users.findByEmailIgnoreCase(request.email().trim())
            .orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"Invalid email or password"));
        if (!passwords.matches(request.password(),user.getPasswordHash()))
            throw new ApiException(HttpStatus.UNAUTHORIZED,"Invalid email or password");
        return new AuthResponse(jwt.generate(user),"Bearer",jwt.expirySeconds(),UserResponse.from(user));
    }
    @Transactional(readOnly=true)
    public UserResponse me(String email) {
        return users.findByEmailIgnoreCase(email).map(UserResponse::from)
            .orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"User not found"));
    }
    private boolean blank(String value) { return value==null || value.isBlank(); }
}
