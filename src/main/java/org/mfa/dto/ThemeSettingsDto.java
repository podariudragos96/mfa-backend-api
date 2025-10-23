package org.mfa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ThemeSettingsDto {
    private String loginTheme;
    private String accountTheme;
    private String emailTheme;
    private String adminTheme;
}
