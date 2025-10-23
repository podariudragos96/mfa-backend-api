package org.mfa.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class DirectGrantService {

    private final String keycloakBaseUrl;
    private final String clientId;
    private final String clientSecret;
    private final RestTemplate rest = new RestTemplate();

    public static record DagResult(boolean ok, String error, String errorDescription) {}
    public DagResult validateCredentialsDetailed(String realm, String username, String password,
                                                 String overrideClientId, String overrideClientSecret) {
        return tokenRequestDetailed(realm, username, password, null, overrideClientId, overrideClientSecret);
    }

    private DagResult tokenRequestDetailed(String realm, String username, String password, String totp) {
        String tokenUrl = keycloakBaseUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", clientId);
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }
        form.add("username", username);
        form.add("password", password);
        if (totp != null && !totp.isBlank()) {
            form.add("totp", totp);
        }

        try {
            ResponseEntity<String> resp = rest.postForEntity(tokenUrl, new HttpEntity<>(form, h), String.class);
            return new DagResult(resp.getStatusCode().is2xxSuccessful(), null, null);
        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            // Try to parse {"error":"...","error_description":"..."} minimally
            String err = null, desc = null;
            if (body != null) {
                int ei = body.indexOf("\"error\"");
                int di = body.indexOf("\"error_description\"");
                if (ei != -1) {
                    int c = body.indexOf(':', ei);
                    int q1 = body.indexOf('"', c+1), q2 = q1==-1?-1:body.indexOf('"', q1+1);
                    if (q1 != -1 && q2 != -1) err = body.substring(q1+1, q2);
                }
                if (di != -1) {
                    int c = body.indexOf(':', di);
                    int q1 = body.indexOf('"', c+1), q2 = q1==-1?-1:body.indexOf('"', q1+1);
                    if (q1 != -1 && q2 != -1) desc = body.substring(q1+1, q2);
                }
            }
            return new DagResult(false, err, desc);
        }
    }

    private DagResult tokenRequestDetailed(String realm, String username, String password, String totp,
                                           String clientId, String clientSecret) {
        String tokenUrl = keycloakBaseUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String cid = (clientId != null && !clientId.isBlank()) ? clientId : this.clientId;
        String csec = (clientSecret != null) ? clientSecret : this.clientSecret;

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", cid);
        if (csec != null && !csec.isBlank()) form.add("client_secret", csec);
        form.add("username", username);
        form.add("password", password);
        if (totp != null && !totp.isBlank()) form.add("totp", totp);

        try {
            ResponseEntity<String> resp = rest.postForEntity(tokenUrl, new HttpEntity<>(form, h), String.class);
            return new DagResult(resp.getStatusCode().is2xxSuccessful(), null, null);
        } catch (HttpClientErrorException e) {
            String err = null, desc = null;
            String body = e.getResponseBodyAsString();
            if (body != null) {
                int ei = body.indexOf("\"error\"");
                int di = body.indexOf("\"error_description\"");
                if (ei != -1) {
                    int c = body.indexOf(':', ei);
                    int q1 = body.indexOf('"', c+1), q2 = q1==-1?-1:body.indexOf('"', q1+1);
                    if (q1 != -1 && q2 != -1) err = body.substring(q1+1, q2);
                }
                if (di != -1) {
                    int c = body.indexOf(':', di);
                    int q1 = body.indexOf('"', c+1), q2 = q1==-1?-1:body.indexOf('"', q1+1);
                    if (q1 != -1 && q2 != -1) desc = body.substring(q1+1, q2);
                }
            }
            return new DagResult(false, err, desc);
        }
    }

    public DirectGrantService(
            @Value("${keycloak.server-url}") String serverUrl,
            @Value("${login.client-id}") String clientId,
            @Value("${login.client-secret:}") String clientSecret) {

        this.keycloakBaseUrl = serverUrl.replaceAll("/+$", "");
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public boolean validateCredentials(String realm, String username, String password) {
        return tokenRequest(realm, username, password, null);
    }

    public boolean validateCredentialsWithTotp(String realm, String username, String password, String totp) {
        return tokenRequest(realm, username, password, totp);
    }

    private boolean tokenRequest(String realm, String username, String password, String totp) {
        String tokenUrl = keycloakBaseUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", clientId);
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }
        form.add("username", username);
        form.add("password", password);
        if (totp != null && !totp.isBlank()) {
            form.add("totp", totp);
        }

        try {
            ResponseEntity<String> resp = rest.postForEntity(tokenUrl, new HttpEntity<>(form, h), String.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (HttpClientErrorException e) {
            return false;
        }
    }
}
