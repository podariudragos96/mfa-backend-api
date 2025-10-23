package org.mfa.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateRealmRequest {
    private String realmName;    // required
    private String displayName;  // optional
}
