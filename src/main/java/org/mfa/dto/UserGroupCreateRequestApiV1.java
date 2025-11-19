package org.mfa.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserGroupCreateRequestApiV1 {

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
