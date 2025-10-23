package org.mfa.controller;

import org.mfa.dto.*;
import org.mfa.util.PendingMfaStore;
import org.mfa.service.DirectGrantService;
import org.mfa.util.EmailSender;
import org.mfa.service.TwilioVerifyService;
import org.mfa.security.JwtService;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/**
 * Combined controller: Email OTP + TOTP, backend-orchestrated with Keycloak.
 * Endpoints used by your Angular app:
 * - POST /auth/login
 * - POST /auth/mfa/email/send
 * - POST /auth/mfa/email/verify
 * - POST /auth/mfa/totp/enroll
 * - POST /auth/mfa/totp/verify
 */
@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "http://localhost:4200")
public class AuthController {

    private final Keycloak keycloak;
    private final DirectGrantService dgs;
    private final PendingMfaStore store;
    private final EmailSender emailSender;
    private final JwtService jwt;
    private final TwilioVerifyService smsVerify;

    public AuthController(Keycloak keycloak,
                          DirectGrantService dgs,
                          PendingMfaStore store,
                          EmailSender emailSender,
                          JwtService jwt,
                          TwilioVerifyService smsVerify) {
        this.keycloak = keycloak;
        this.dgs = dgs;
        this.store = store;
        this.emailSender = emailSender;
        this.jwt = jwt;
        this.smsVerify = smsVerify;
    }

    @Value("${keycloak.server-url}")
    private String keycloakBaseUrlRaw; // e.g. http://localhost:8180

    @Value("${browser.client-id}")
    private String browserClientId;

    @Value("${browser.client-secret}")
    private String browserClientSecret;

    @Value("${browser.redirect-uri}")
    private String browserRedirectUri;


    @Value("${login.nootp.client-id}") private String noOtpClientId;
    @Value("${login.nootp.client-secret}") private String noOtpClientSecret;


    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        String realm = Optional.ofNullable(req.getRealm()).orElse("").trim();
        String username = Optional.ofNullable(req.getUsername()).orElse("").trim();
        String password = Optional.ofNullable(req.getPassword()).orElse("");

        RealmResource rr;
        try {
            rr = keycloak.realms().realm(realm);
            rr.roles().list();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REALM"));
        }

        var users = rr.users().search(username, true);
        if (users == null || users.isEmpty()) {
            return ResponseEntity.status(404).body(new ErrorResponse("USER_NOT_FOUND"));
        }
        String userId = users.get(0).getId();

        var dag = dgs.validateCredentialsDetailed(realm, username, password, noOtpClientId, noOtpClientSecret);

        // Helper to build the dynamic MFA methods list
        java.util.function.Supplier<String[]> computeMethods = () -> {
            var methods = new ArrayList<String>();
            // Email OTP only if email exists & is verified (optional: allow unverified if you want)
            var rep = rr.users().get(userId).toRepresentation();
            boolean hasEmail = rep.getEmail() != null && !rep.getEmail().isBlank();
            if (hasEmail) methods.add("email");
            methods.add("totp");
            String phone = getUserPhoneE164(realm, userId);
            if (phone != null) methods.add("sms");
            return methods.toArray(String[]::new);
        };

        if (!dag.ok()) {
            String err  = Optional.ofNullable(dag.error()).orElse("");
            String desc = Optional.ofNullable(dag.errorDescription()).orElse("").toLowerCase();

            // When required actions are pending, Keycloak blocks password grant
            if ("invalid_grant".equals(err) && desc.contains("account is not fully set up")) {
                // Build needs {}
                var ur  = rr.users().get(userId);
                var rep = ur.toRepresentation();

                boolean emailMissing = (rep.getEmail() == null || rep.getEmail().isBlank());
                boolean emailVerified = Boolean.TRUE.equals(rep.isEmailVerified());
                boolean hasTotp = userHasTotp(realm, userId);
                var ra = Optional.ofNullable(rep.getRequiredActions()).orElse(List.of());

                var needs = new Needs();
                needs.setEmailMissing(emailMissing);
                needs.setVerifyEmail(!emailMissing && !emailVerified || ra.contains("VERIFY_EMAIL"));
                needs.setConfigureTotp(!hasTotp || ra.contains("CONFIGURE_TOTP"));

                // Create attempt so the UI can continue the flow
                String attemptId = store.create(realm, username, userId, password);

                return ResponseEntity.ok(new LoginResponse(
                        true,
                        computeMethods.get(),
                        attemptId,
                        needs
                ));
            }

            if ("invalid_grant".equals(err) && desc.contains("invalid user credentials")) {
                return ResponseEntity.status(401).body(new ErrorResponse("INVALID_PASSWORD"));
            }
            return ResponseEntity.status(401).body(new ErrorResponse("LOGIN_FAILED"));
        }

        // Credentials OK and no blocking required actions; proceed to MFA
        String attemptId = store.create(realm, username, userId, password);

        var needs = new Needs();
        var rep = rr.users().get(userId).toRepresentation();
        boolean emailMissing = (rep.getEmail() == null || rep.getEmail().isBlank());
        boolean emailVerified = Boolean.TRUE.equals(rep.isEmailVerified());
        boolean hasTotp = userHasTotp(realm, userId);
        needs.setEmailMissing(emailMissing);
        needs.setVerifyEmail(!emailMissing && !emailVerified);
        needs.setConfigureTotp(!hasTotp);

        return ResponseEntity.ok(new LoginResponse(true, computeMethods.get(), attemptId, needs));
    }

    @PostMapping("/mfa/email/send")
    public ResponseEntity<?> sendEmailOtp(@RequestBody SendEmailReq req) {
        var a = store.get(req.getLoginAttemptId());
        if (a == null) return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_ATTEMPT"));

        UserRepresentation u = keycloak.realms().realm(a.getRealm())
                .users().get(a.getUserId()).toRepresentation();
        String email = u.getEmail();
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("NO_EMAIL_ON_ACCOUNT"));
        }

        // Generate cryptographically secure 6-digit OTP
        String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        Instant exp = Instant.now().plusSeconds(5 * 60); // 5 min TTL
        store.setEmailOtp(req.getLoginAttemptId(), code, exp);

        // Send email (your EmailSender implementation)
        emailSender.sendOtp(email, code);

        return ResponseEntity.ok(Map.of("sent", true));
    }

    @PostMapping("/mfa/email/verify")
    public ResponseEntity<?> verifyEmailOtp(@RequestBody VerifyEmailReq req) {
        var a = store.get(req.getLoginAttemptId());
        if (a == null) return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_ATTEMPT"));

        boolean ok = store.consumeValidEmailOtp(req.getLoginAttemptId(), req.getCode());
        if (!ok) return ResponseEntity.status(400).body(new ErrorResponse("INVALID_OR_EXPIRED_OTP"));

        String token = jwt.issue(a.getRealm(), a.getUserId(), a.getUsername());
        store.remove(req.getLoginAttemptId());
        return ResponseEntity.ok(new FinalTokenResponse(token));
    }

    @PostMapping("/mfa/totp/enroll")
    public ResponseEntity<?> enrollTotp(@RequestBody EnrollTotpRequest req) {
        var a = store.get(req.getLoginAttemptId());
        if (a == null) return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_ATTEMPT"));

        if (userHasTotp(a.getRealm(), a.getUserId())) {
            return ResponseEntity.ok(Map.of("alreadyConfigured", true));
        }

        keycloak.realms().realm(a.getRealm())
                .users().get(a.getUserId())
                .executeActionsEmail(List.of("CONFIGURE_TOTP"));

        return ResponseEntity.ok(Map.of("emailSent", true));
    }

    @PostMapping("/mfa/totp/verify")
    public ResponseEntity<?> verifyTotp(@RequestBody VerifyTotpRequest req) {
        var a = store.get(req.getLoginAttemptId());
        if (a == null) return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_ATTEMPT"));
        if (a.getPassword() == null || a.getPassword().isBlank()) {
            return ResponseEntity.status(400).body(new ErrorResponse("PASSWORD_MISSING"));
        }

        boolean ok = dgs.validateCredentialsWithTotp(a.getRealm(), a.getUsername(), a.getPassword(), req.getCode());
        if (!ok) {
            return ResponseEntity.status(400).body(new ErrorResponse("INVALID_TOTP"));
        }

        String token = jwt.issue(a.getRealm(), a.getUserId(), a.getUsername());
        store.remove(req.getLoginAttemptId());
        return ResponseEntity.ok(new FinalTokenResponse(token));
    }

    @PostMapping("/mfa/totp/start-session")
    public ResponseEntity<?> startTotpSession(@RequestBody StartTotpSessionRequest req) {
        var a = store.get(req.getLoginAttemptId());
        if (a == null) return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_ATTEMPT"));

        var rr = keycloak.realms().realm(a.getRealm());
        var ur = rr.users().get(a.getUserId());

        // Only add Required Action if OTP is NOT configured yet
        if (!userHasTotp(a.getRealm(), a.getUserId())) {
            var rep = ur.toRepresentation();
            var ra = Optional.ofNullable(rep.getRequiredActions()).orElse(new ArrayList<>());
            if (!ra.contains("CONFIGURE_TOTP")) ra.add("CONFIGURE_TOTP");
            rep.setRequiredActions(ra);
            ur.update(rep);
        }

        String base = keycloakBaseUrlRaw.replaceAll("/+$", "");
        String realm = a.getRealm();

        String state = realm + ":" + UUID.randomUUID();
        store.bindState(state, req.getLoginAttemptId());

        String nonce = UUID.randomUUID().toString();

        String authUrl = UriComponentsBuilder
                .fromHttpUrl(base + "/realms/" + realm + "/protocol/openid-connect/auth")
                .queryParam("client_id", browserClientId)
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", browserRedirectUri)
                .queryParam("scope", "openid")
                .queryParam("state", state)
                .queryParam("nonce", nonce)
                .queryParam("prompt", "login")       // force an explicit login screen if no SSO
                .queryParam("login_hint", a.getUsername()) // pre-fills username
                .build(true)
                .toUriString();

        System.out.println("[TOTP] start-session redirect: " + authUrl);
        return ResponseEntity.ok(new StartTotpSessionResponse(authUrl));
    }


    @GetMapping("/mfa/totp/callback")
    public ResponseEntity<Void> totpCallback(
            @RequestParam("code") String code,
            @RequestParam(value = "state", required = false) String state) {

        // Optional: exchange code (assert flow); we need the realm from state
        String realm = null;
        if (state != null && state.contains(":")) {
            realm = state.substring(0, state.indexOf(':'));
        }

        try {
            if (realm != null) {
                String base = keycloakBaseUrlRaw.replaceAll("/+$", "");
                String tokenUrl = base + "/realms/" + realm + "/protocol/openid-connect/token";

                MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                form.add("grant_type", "authorization_code");
                form.add("code", code);
                form.add("client_id", browserClientId);
                form.add("client_secret", browserClientSecret);
                form.add("redirect_uri", browserRedirectUri);

                new RestTemplate().postForEntity(tokenUrl, new HttpEntity<>(form, new HttpHeaders()), String.class);
            }
        } catch (Exception ignored) {
            // In PoC we can ignore errors here; the important part is the OTP required action was completed.
        } finally {
            if (state != null) store.clearState(state);
        }

        // Redirect user back to SPA
        HttpHeaders h = new HttpHeaders();
        h.setLocation(URI.create("http://localhost:4200/"));
        return new ResponseEntity<>(h, HttpStatus.FOUND);
    }

    @PostMapping("/mfa/sms/send")
    public ResponseEntity<?> sendSms(@RequestBody SendSmsReq req) {
        var a = store.get(req.getLoginAttemptId());
        if (a == null) return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_ATTEMPT"));

        String phone = getUserPhoneE164(a.getRealm(), a.getUserId());
        if (phone == null) return ResponseEntity.badRequest().body(new ErrorResponse("NO_PHONE_ON_ACCOUNT"));

        // Optional local throttling: e.g., 30s cooldown using store timestamps
        // (Verify also rate-limits on Twilio side)
        smsVerify.sendSms(phone);
        return ResponseEntity.ok(java.util.Map.of("sent", true));
    }

    @PostMapping("/mfa/sms/verify")
    public ResponseEntity<?> verifySms(@RequestBody VerifySmsReq req) {
        var a = store.get(req.getLoginAttemptId());
        if (a == null) return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_ATTEMPT"));

        String phone = getUserPhoneE164(a.getRealm(), a.getUserId());
        if (phone == null) return ResponseEntity.badRequest().body(new ErrorResponse("NO_PHONE_ON_ACCOUNT"));

        boolean ok = smsVerify.checkCode(phone, req.getCode());
        if (!ok) return ResponseEntity.status(400).body(new ErrorResponse("INVALID_OR_EXPIRED_OTP"));

        String token = jwt.issue(a.getRealm(), a.getUserId(), a.getUsername());
        store.remove(req.getLoginAttemptId());
        return ResponseEntity.ok(new FinalTokenResponse(token));
    }

    @PostMapping("/profile/email")
    public ResponseEntity<?> setEmail(@RequestBody SetEmailRequest req) {
        var a = store.get(req.getLoginAttemptId());
        if (a == null) return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_ATTEMPT"));

        String email = Optional.ofNullable(req.getEmail()).orElse("").trim();
        if (email.isBlank() || !email.contains("@")) {
            return ResponseEntity.badRequest().body(new ErrorResponse("BAD_EMAIL"));
        }

        var ur = keycloak.realms().realm(a.getRealm()).users().get(a.getUserId());
        var rep = ur.toRepresentation();
        rep.setEmail(email);
        rep.setEmailVerified(false);
        ur.update(rep);

        return ResponseEntity.ok(Map.of("updated", true));
    }

    @PostMapping("/profile/verify-email")
    public ResponseEntity<?> sendVerifyEmail(@RequestBody SendEmailReq req) {
        var a = store.get(req.getLoginAttemptId());
        if (a == null) return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_ATTEMPT"));

        keycloak.realms().realm(a.getRealm())
                .users().get(a.getUserId())
                .executeActionsEmail(List.of("VERIFY_EMAIL"));

        return ResponseEntity.ok(Map.of("emailSent", true));
    }

    @GetMapping("/profile/status")
    public ResponseEntity<?> profileStatus(@RequestParam("loginAttemptId") String attemptId) {
        var a = store.get(attemptId);
        if (a == null) return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_ATTEMPT"));

        var ur = keycloak.realms().realm(a.getRealm()).users().get(a.getUserId());
        var rep = ur.toRepresentation();

        boolean emailMissing = (rep.getEmail() == null || rep.getEmail().isBlank());
        boolean emailVerified = Boolean.TRUE.equals(rep.isEmailVerified());
        boolean hasTotp = userHasTotp(a.getRealm(), a.getUserId());
        return ResponseEntity.ok(Map.of(
                "emailMissing", emailMissing,
                "emailVerified", emailVerified,
                "hasTotp", hasTotp
        ));
    }

    private boolean userHasTotp(String realm, String userId) {
        try {
            var creds = keycloak.realms().realm(realm).users().get(userId).credentials();
            if (creds == null) return false;
            return creds.stream().anyMatch(c -> "otp".equalsIgnoreCase(c.getType()));
        } catch (Exception e) {
            return false;
        }
    }

    private String getUserPhoneE164(String realm, String userId) {
        var rep = keycloak.realms().realm(realm).users().get(userId).toRepresentation();
        if (rep.getAttributes() == null) return null;
        var vals = rep.getAttributes().get("phone_number");
        if (vals == null || vals.isEmpty()) return null;
        var s = vals.get(0);
        return (s != null && s.trim().startsWith("+")) ? s.trim() : null;
    }

}
