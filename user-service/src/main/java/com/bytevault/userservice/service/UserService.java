package com.bytevault.userservice.service;

import com.bytevault.userservice.dto.AuthResponse;
import com.bytevault.userservice.dto.LoginRequest;
import com.bytevault.userservice.dto.RegisterRequest;
import com.bytevault.userservice.dto.UpdateUserRequest;
import com.bytevault.userservice.dto.UserResponse;
import com.bytevault.userservice.entity.Role;
import com.bytevault.userservice.entity.User;
import com.bytevault.userservice.exception.ForbiddenException;
import com.bytevault.userservice.exception.InvalidCredentialsException;
import com.bytevault.userservice.exception.UserAlreadyExistsException;
import com.bytevault.userservice.exception.UserNotFoundException;
import com.bytevault.userservice.repository.UserRepository;
import com.bytevault.userservice.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException(request.getUsername());
        }
        User user = new User(
                request.getUsername(),
                passwordEncoder.encode(request.getPassword()),
                request.getEmail(),
                Role.USER
        );
        userRepository.save(user);
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name());
        return new AuthResponse(token, user.getUsername(), user.getRole().name(), jwtUtil.getExpirationMs());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name());
        return new AuthResponse(token, user.getUsername(), user.getRole().name(), jwtUtil.getExpirationMs());
    }

    public List<UserResponse> listAll() {
        return userRepository.findAll().stream().map(UserResponse::from).collect(Collectors.toList());
    }

    public UserResponse getById(Long id) {
        return UserResponse.from(findById(id));
    }

    public UserResponse getByUsername(String username) {
        return UserResponse.from(findByUsername(username));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        return UserResponse.from(applyUpdate(findById(id), request));
    }

    @Transactional
    public UserResponse updateByUsername(String username, UpdateUserRequest request) {
        return UserResponse.from(applyUpdate(findByUsername(username), request));
    }

    @Transactional
    public UserResponse updateRole(Long id, String role) {
        User user = findById(id);
        user.setRole(Role.valueOf(role));
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void delete(Long id, String requestingUsername) {
        User user = findById(id);
        if (user.getUsername().equals(requestingUsername)) {
            throw new ForbiddenException("An ADMIN cannot delete their own account through this endpoint");
        }
        userRepository.delete(user);
    }

    @Transactional
    public void deleteByUsername(String username) {
        userRepository.delete(findByUsername(username));
    }

    private User applyUpdate(User user, UpdateUserRequest request) {
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        return userRepository.save(user);
    }

    private User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
    }

    private User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
    }
}
