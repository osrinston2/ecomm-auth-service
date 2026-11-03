package com.ecomm.auth.service;

import com.ecomm.auth.dto.response.UserProfileResponse;
import com.ecomm.auth.entity.User;
import com.ecomm.auth.entity.UserStatus;
import com.ecomm.auth.exception.ResourceNotFoundException;
import com.ecomm.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfileByUuid(String uuid) {
        User user = userRepository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with uuid: " + uuid));
        return mapToProfile(user);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfileByEmail(String email) {
        User user = userRepository.findByEmailWithRoles(email)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return mapToProfile(user);
    }

    @Transactional
    public void updateUserStatus(String uuid, UserStatus status) {
        User user = userRepository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with uuid: " + uuid));
        user.setStatus(status);
        userRepository.save(user);
    }

    private UserProfileResponse mapToProfile(User user) {
        Set<String> roles = user.getRoles().stream()
            .map(role -> role.getName().name())
            .collect(Collectors.toSet());

        return new UserProfileResponse(
            user.getUuid(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getPhoneNumber(),
            roles,
            user.getStatus(),
            user.isEmailVerified(),
            user.getCreatedAt()
        );
    }
}

