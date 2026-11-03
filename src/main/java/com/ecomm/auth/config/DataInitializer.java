package com.ecomm.auth.config;

import com.ecomm.auth.entity.Role;
import com.ecomm.auth.entity.RoleName;
import com.ecomm.auth.entity.User;
import com.ecomm.auth.entity.UserStatus;
import com.ecomm.auth.repository.RoleRepository;
import com.ecomm.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;
import java.util.UUID;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    public CommandLineRunner seedDefaultUsers(UserRepository userRepository,
                                             RoleRepository roleRepository,
                                             PasswordEncoder passwordEncoder) {
        return args -> {
            // Seed Customer User
            if (!userRepository.existsByEmailIgnoreCase("customer@ecomm.com")) {
                Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER)
                    .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_CUSTOMER, "Standard e-commerce customer")));

                User customer = new User();
                customer.setUuid(UUID.randomUUID().toString());
                customer.setEmail("customer@ecomm.com");
                customer.setPasswordHash(passwordEncoder.encode("Customer123!"));
                customer.setFirstName("Alice");
                customer.setLastName("Customer");
                customer.setPhoneNumber("+1000000001");
                customer.setStatus(UserStatus.ACTIVE);
                customer.setEmailVerified(true);
                customer.getRoles().add(customerRole);

                userRepository.save(customer);
                log.info("Initialized default customer user: customer@ecomm.com (Password: Customer123!)");
            }

            // Seed Admin User
            if (!userRepository.existsByEmailIgnoreCase("admin@ecomm.com")) {
                Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
                    .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_ADMIN, "Platform administrator")));
                Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER)
                    .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_CUSTOMER, "Standard e-commerce customer")));

                User admin = new User();
                admin.setUuid(UUID.randomUUID().toString());
                admin.setEmail("admin@ecomm.com");
                admin.setPasswordHash(passwordEncoder.encode("Admin123!"));
                admin.setFirstName("Super");
                admin.setLastName("Admin");
                admin.setPhoneNumber("+1000000002");
                admin.setStatus(UserStatus.ACTIVE);
                admin.setEmailVerified(true);
                admin.getRoles().addAll(Set.of(adminRole, customerRole));

                userRepository.save(admin);
                log.info("Initialized default admin user: admin@ecomm.com (Password: Admin123!)");
            }
        };
    }
}

