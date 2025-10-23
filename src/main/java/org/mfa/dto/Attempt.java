package org.mfa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attempt {
    private String realm;
    private String username;
    private String userId;
    private String password;      // PoC: reuse on TOTP verify
    private String emailOtp;
    private Instant emailOtpExpiry;
    private Instant createdAt;
}
