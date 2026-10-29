package com.bytevault.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Partial update: any field left null is kept as-is.
 */
public class UpdateUserRequest {

    @Email(message = "email must be a valid address")
    private String email;

    @Size(min = 6, message = "password must be at least 6 characters")
    private String password;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
