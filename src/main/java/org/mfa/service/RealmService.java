package org.mfa.service;

import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.mfa.dto.CreateRealmRequest;
import org.mfa.dto.RealmSummaryDto;
import org.mfa.dto.ThemeSettingsDto;
import org.mfa.dto.ThemeUpdateRequest;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RealmService {

    private final Keycloak keycloak;

    private static final String MFA_CLIENT_ID = "mfa-client";
    private static final String MFA_CLIENT_SECRET = "mfa-client-secret";

    public void createRealm(CreateRealmRequest req) {
        RealmRepresentation rep = new RealmRepresentation();
        rep.setRealm(req.getRealmName());
        rep.setDisplayName(req.getDisplayName());
        rep.setEnabled(true);

        keycloak.realms().create(rep);

        setupMfaClientForRealm(req.getRealmName()); // Critical: call setup explicitly
    }

    public List<RealmSummaryDto> listRealms() {
        return keycloak.realms().findAll().stream()
                .map(r -> new RealmSummaryDto(
                        r.getId(),
                        (r.getDisplayName() != null && !r.getDisplayName().isBlank())
                                ? r.getDisplayName()
                                : r.getRealm()
                ))
                .toList();
    }

    public void deleteRealm(String realmName) {
        keycloak.realm(realmName).remove();
    }

    private void setupMfaClientForRealm(String realmName) {
        ClientRepresentation clientRep = new ClientRepresentation();
        clientRep.setClientId(MFA_CLIENT_ID);
        clientRep.setName("MFA Service Client");
        clientRep.setSecret(MFA_CLIENT_SECRET);
        clientRep.setServiceAccountsEnabled(true);
        clientRep.setPublicClient(false);
        clientRep.setProtocol("openid-connect");
        clientRep.setDirectAccessGrantsEnabled(false);
        clientRep.setEnabled(true);

        Response resp = keycloak.realm(realmName).clients().create(clientRep);
        try {
            if (resp.getStatus() >= 200 && resp.getStatus() < 300) {
                String clientUuid = getCreatedIdFromResponse(resp);
                if (clientUuid != null) {
                    String svcAcctUserId = keycloak.realm(realmName)
                            .clients().get(clientUuid)
                            .getServiceAccountUser().getId();

                    keycloak.realm(realmName).users().get(svcAcctUserId).roles()
                            .clientLevel(getRealmManagementClientId(realmName))
                            .add(Collections.singletonList(getRealmAdminRoleRep(realmName)));
                }
            } else {
                throw new RuntimeException("Failed to create MFA client in realm " + realmName);
            }
        } finally {
            resp.close();
        }
    }

    private String getCreatedIdFromResponse(Response response) {
        String location = response.getHeaderString("Location");
        if (location == null) return null;
        int idx = location.lastIndexOf('/');
        return (idx != -1) ? location.substring(idx + 1) : null;
    }

    private String getRealmManagementClientId(String realmName) {
        return keycloak.realm(realmName)
                .clients()
                .findByClientId("realm-management")
                .get(0)
                .getId();
    }

    private RoleRepresentation getRealmAdminRoleRep(String realmName) {
        RealmResource realm = keycloak.realm(realmName);
        ClientRepresentation client = realm.clients()
                .findByClientId("realm-management")
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("realm-management client not found"));

        return realm.clients()
                .get(client.getId())
                .roles()
                .get("realm-admin")
                .toRepresentation();
    }

    public ThemeSettingsDto getTheme(String realmName) {
        var rep = keycloak.realm(realmName).toRepresentation();
        return new ThemeSettingsDto(
                rep.getLoginTheme(),
                rep.getAccountTheme(),
                rep.getEmailTheme(),
                rep.getAdminTheme()
        );
    }

    public ThemeSettingsDto updateTheme(String realmName, ThemeUpdateRequest req) {
        var realm = keycloak.realm(realmName);
        var rep = realm.toRepresentation();

        if (req.getLoginTheme() != null) rep.setLoginTheme(normalizeTheme(req.getLoginTheme()));
        if (req.getAccountTheme() != null) rep.setAccountTheme(normalizeTheme(req.getAccountTheme()));
        if (req.getEmailTheme() != null) rep.setEmailTheme(normalizeTheme(req.getEmailTheme()));
        if (req.getAdminTheme() != null) rep.setAdminTheme(normalizeTheme(req.getAdminTheme()));

        realm.update(rep);

        var after = realm.toRepresentation();
        return new ThemeSettingsDto(
                after.getLoginTheme(),
                after.getAccountTheme(),
                after.getEmailTheme(),
                after.getAdminTheme()
        );
    }

    private String normalizeTheme(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }
}
