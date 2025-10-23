package org.mfa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Needs {
    private boolean emailMissing;
    private boolean verifyEmail;
    private boolean configureTotp;
}
