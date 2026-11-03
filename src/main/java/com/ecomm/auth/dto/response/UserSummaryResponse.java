package com.ecomm.auth.dto.response;

import com.ecomm.auth.entity.UserStatus;

import java.util.Set;

public class UserSummaryResponse {

    private String id;
    private String email;
    private String firstName;
    private String lastName;
    private Set<String> roles;
    private UserStatus status;
    private boolean emailVerified;

    public UserSummaryResponse() {}

    public UserSummaryResponse(String id, String email, String firstName, String lastName,
                               Set<String> roles, UserStatus status, boolean emailVerified) {
        this.id = id;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.roles = roles;
        this.status = status;
        this.emailVerified = emailVerified;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }
}

