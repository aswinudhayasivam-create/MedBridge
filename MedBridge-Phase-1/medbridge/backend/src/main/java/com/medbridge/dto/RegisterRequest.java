package com.medbridge.dto;

import com.medbridge.model.Role;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record RegisterRequest(
    @NotBlank @Size(max=120) String name,
    @NotBlank @Email @Size(max=190) String email,
    @NotBlank @Size(min=12,max=72) String password,
    @NotNull Role role,
    @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
    @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
    String orgName,
    String licenseNo,
    String serviceArea
) {}
