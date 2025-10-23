package org.mfa.util;

import org.mfa.dto.Attempt;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PendingMfaStore {

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final Map<String, String> stateToAttempt = new ConcurrentHashMap<>();

    public String create(String realm, String username, String userId, String password) {
        String id = UUID.randomUUID().toString();
        attempts.put(id, Attempt.builder()
                .realm(realm)
                .username(username)
                .userId(userId)
                .password(password)
                .createdAt(Instant.now())
                .build());
        return id;
    }

    public Attempt get(String id) {
        return attempts.get(id);
    }

    public void remove(String id) {
        attempts.remove(id);
    }

    public void setEmailOtp(String id, String code, Instant expiry) {
        Attempt a = attempts.get(id);
        if (a != null) {
            a.setEmailOtp(code);
            a.setEmailOtpExpiry(expiry);
        }
    }

    public boolean consumeValidEmailOtp(String id, String code) {
        Attempt a = attempts.get(id);
        if (a == null || a.getEmailOtp() == null || a.getEmailOtpExpiry() == null) return false;
        if (Instant.now().isAfter(a.getEmailOtpExpiry())) return false;
        boolean ok = a.getEmailOtp().equals(code);
        if (ok) {
            a.setEmailOtp(null);
            a.setEmailOtpExpiry(null);
        }
        return ok;
    }

    public void bindState(String state, String attemptId) {
        stateToAttempt.put(state, attemptId);
    }

    public void clearState(String state) {
        stateToAttempt.remove(state);
    }
}