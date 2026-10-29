package com.bytevault.userservice.controller;

import com.bytevault.userservice.dto.UpdateRoleRequest;
import com.bytevault.userservice.dto.UpdateUserRequest;
import com.bytevault.userservice.dto.UserResponse;
import com.bytevault.userservice.exception.ForbiddenException;
import com.bytevault.userservice.security.JwtAuthFilter;
import com.bytevault.userservice.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Account CRUD. /me endpoints act on the caller's own account (any logged-in
 * user); the rest manage every account and require an ADMIN token.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse getMe(HttpServletRequest httpRequest) {
        return userService.getByUsername(username(httpRequest));
    }

    @PutMapping("/me")
    public UserResponse updateMe(@Valid @RequestBody UpdateUserRequest request, HttpServletRequest httpRequest) {
        return userService.updateByUsername(username(httpRequest), request);
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(HttpServletRequest httpRequest) {
        userService.deleteByUsername(username(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<UserResponse> listAll(HttpServletRequest httpRequest) {
        requireAdmin(httpRequest);
        return userService.listAll();
    }

    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable Long id, HttpServletRequest httpRequest) {
        requireAdmin(httpRequest);
        return userService.getById(id);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request,
                               HttpServletRequest httpRequest) {
        requireAdmin(httpRequest);
        return userService.update(id, request);
    }

    @PutMapping("/{id}/role")
    public UserResponse updateRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request,
                                   HttpServletRequest httpRequest) {
        requireAdmin(httpRequest);
        return userService.updateRole(id, request.getRole());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        requireAdmin(httpRequest);
        userService.delete(id, username(httpRequest));
        return ResponseEntity.noContent().build();
    }

    private String username(HttpServletRequest request) {
        return (String) request.getAttribute(JwtAuthFilter.USERNAME_ATTRIBUTE);
    }

    private void requireAdmin(HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute(JwtAuthFilter.ROLE_ATTRIBUTE);
        if (!"ADMIN".equals(role)) {
            throw new ForbiddenException("Only an ADMIN can manage other accounts");
        }
    }
}
