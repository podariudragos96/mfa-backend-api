package org.mfa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExportUserDto {
    private String username;
    private String displayName;
    private String password; // will be null (Keycloak never exposes real passwords)
}
