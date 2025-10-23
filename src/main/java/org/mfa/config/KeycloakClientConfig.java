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
    private String serverUrl;
    @Value("${keycloak.realm}")
    private String realm;
    @Value("${keycloak.client-id}")
    private String clientId;
//    @Value("${keycloak.client-secret:}")
//    private String clientSecret;
    @Value("${keycloak.username}")
    private String adminUsername;
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