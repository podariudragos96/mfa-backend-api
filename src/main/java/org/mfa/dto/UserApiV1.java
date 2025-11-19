package org.mfa.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserApiV1 {

    /**
     * Unique identifier of the user.
     * Example: "123"
     */
    private String id;

    /**
     * Unique username of the user.
     * Example: "dragos.podariu"
     */
    private String username;

    /**
     * User's email address.
     * Example: "dragos.podariu@example.com"
     */
    private String email;

    /**
     * User's first name.
     * Example: "Dragoș"
     */
    private String firstName;

    /**
     * User's last name.
     * Example: "Podariu"
     */
    private String lastName;

    /**
     * B-crypt encrypted password.
     * Example: ""
     */
    private String password;

    /**
     * User's phone number (E.164 format preferred).
     * Example: "+40721234567"
     */
    private String phoneNumber;

    /**
     * User's user group.
     * Example: "Test User Group"
     */
    private String userGroup;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private boolean emailVerified;  // Whether the email has been verified (read-only)
}