package org.mfa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserGroupApiV1 {

    /**
     * Unique identifier of the user group.
     * Example: "1001"
     */
    private String id;

    /**
     * Name of the user group by which we identify them (unique).
     * Example: "Whitelabel Tenant Number 1"
     */
    private String name;

    /**
     * Name that's shown externally.
     * Example: "Whitelabel Tenant Number 1 Display name"
     */
    private String displayName;
}
