package com.medbridge.dto;
import com.medbridge.model.*;
import java.math.BigDecimal;
public record UserResponse(Long id, String name, String email, Role role, boolean verified,
                           BigDecimal latitude, BigDecimal longitude, String orgName, String serviceArea) {
    public static UserResponse from(User user) {
        NgoProfile profile = user.getNgoProfile();
        return new UserResponse(user.getId(),user.getName(),user.getEmail(),user.getRole(),user.isVerified(),
            user.getLatitude(),user.getLongitude(),profile==null?null:profile.getOrgName(),
            profile==null?null:profile.getServiceArea());
    }
}
