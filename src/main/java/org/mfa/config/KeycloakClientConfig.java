package org.mfa.config;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class KeycloakClientConfig {

    @Value("${keycloak.server-url}")
    private String serverUrl;           // e.g., "http://localhost:8080"
    @Value("${keycloak.realm}")
    private String realm;               // e.g., "master" for admin user realm
    @Value("${keycloak.client-id}")
    private String clientId;            // e.g., "admin-cli" or other client with admin access
//    @Value("${keycloak.client-secret:}")
//    private String clientSecret;        // optional: required if client is confidential
    @Value("${keycloak.username}")
    private String adminUsername;       // e.g., "admin"
    @Value("${keycloak.password}")
    private String adminPassword;

    @Bean
    public Keycloak keycloakAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)                      // Keycloak base URL (no "/auth" in new versions)
                .realm(realm)                              // Realm to log into (typically "master" for realm creation)
                .grantType(OAuth2Constants.PASSWORD)       // Using direct grant with username/password:contentReference[oaicite:2]{index=2}
                .clientId(clientId)
//                .clientSecret(clientSecret)                // (Optional, omit if not needed)
                .username(adminUsername)
                .password(adminPassword)
                .build();
    }

}