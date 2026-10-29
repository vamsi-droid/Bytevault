package com.bytevault.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class UpdateRoleRequest {

    @NotBlank(message = "role is required")
    @Pattern(regexp = "USER|ADMIN", message = "role must be USER or ADMIN")
    private String role;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
