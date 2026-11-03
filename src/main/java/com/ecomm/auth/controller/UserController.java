package com.ecomm.auth.controller;

import com.ecomm.auth.dto.response.ApiResponse;
import com.ecomm.auth.dto.response.UserProfileResponse;
import com.ecomm.auth.security.UserPrincipal;
import com.ecomm.auth.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        UserProfileResponse profile = userService.getProfileByUuid(principal.getUuid());
        return ResponseEntity.ok(ApiResponse.ok("User profile retrieved", profile));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN') or #principal.uuid == #uuid")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserByUuid(@PathVariable String uuid,
                                                                         @AuthenticationPrincipal UserPrincipal principal) {
        UserProfileResponse profile = userService.getProfileByUuid(uuid);
        return ResponseEntity.ok(ApiResponse.ok("User details retrieved", profile));
    }
}

