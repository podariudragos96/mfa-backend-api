package org.mfa.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserDto {
    private String id;        // User ID (for output; not needed on create)
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String password;  // Plaintext password for new user (optional)

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private boolean emailVerified;  // Whether the email has been verified (read-only)

    private String phoneNumber; // E.164 preferred (e.g. +4072XXXXXXX)
}
