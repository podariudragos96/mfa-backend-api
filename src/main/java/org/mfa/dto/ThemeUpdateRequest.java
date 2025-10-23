package org.mfa.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ThemeUpdateRequest {
    private String loginTheme;   // null/empty -> clear to default
    private String accountTheme;
    private String emailTheme;
    private String adminTheme;
}
