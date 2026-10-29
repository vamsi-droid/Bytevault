package com.bytevault.userservice.config;

import com.bytevault.userservice.entity.Role;
import com.bytevault.userservice.entity.User;
import com.bytevault.userservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Registration always creates a USER, so without this there would be no way
 * to obtain an ADMIN token for managing the catalog. Creates the configured
 * admin account once, on first startup.
 */
@Component
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String email;

    public AdminSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       @Value("${admin.username}") String username,
                       @Value("${admin.password}") String password,
                       @Value("${admin.email}") String email) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
        this.email = email;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByUsername(username)) {
            return;
        }
        userRepository.save(new User(username, passwordEncoder.encode(password), email, Role.ADMIN));
        log.info("Created default ADMIN account '{}'", username);
    }
}
