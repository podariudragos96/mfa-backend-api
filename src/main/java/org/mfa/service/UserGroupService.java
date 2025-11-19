package org.mfa.service;

import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.mfa.dto.UserGroupApiV1;
import org.mfa.dto.UserGroupCreateRequestApiV1;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserGroupService {

    private final Keycloak keycloak;

    private static final String MFA_CLIENT_ID = "mfa-client";
    private static final String MFA_CLIENT_SECRET = "mfa-client-secret";

    public void createRealm(UserGroupCreateRequestApiV1 req) {
        RealmRepresentation rep = new RealmRepresentation();
        rep.setRealm(req.getName());
        rep.setDisplayName(req.getDisplayName());
        rep.setEnabled(true);

        keycloak.realms().create(rep);

        setupMfaClientForRealm(req.getName());
    }

    public List<UserGroupApiV1> listUserGroups() {
        return keycloak.realms().findAll().stream()
                .map(r -> new UserGroupApiV1(
                        r.getId(),
                        r.getRealm(),
                        r.getDisplayName()))
                .toList();
    }

    public UserGroupApiV1 getUserGroupById(String userGroupId) {
        RealmRepresentation realm = keycloak.realms().findAll().stream()
                .filter(r -> r.getId().equalsIgnoreCase(userGroupId))
                .findFirst()
                .orElseThrow(() ->
                        new RuntimeException("User group (realm) with id " + userGroupId + " not found"));

        return new UserGroupApiV1(
                realm.getId(),
                realm.getRealm(),
                realm.getDisplayName()
        );
    }

    public UserGroupApiV1 patchUserGroupById(String userGroupId, UserGroupApiV1 userGroupApiV1) {
        RealmRepresentation existingRealm = keycloak.realms().findAll().stream()
                .filter(r -> r.getId().equalsIgnoreCase(userGroupId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Realm with id " + userGroupId + " not found"));

        RealmResource realmResource = keycloak.realm(existingRealm.getRealm());


        if (userGroupApiV1.getName() != null && !userGroupApiV1.getName().isBlank()) {
            existingRealm.setRealm(userGroupApiV1.getName());
        }

        if (userGroupApiV1.getDisplayName() != null && !userGroupApiV1.getDisplayName().isBlank()) {
            existingRealm.setDisplayName(userGroupApiV1.getDisplayName());
        }

        realmResource.update(existingRealm);

        RealmRepresentation updated = keycloak.realm(existingRealm.getRealm()).toRepresentation();

        return new UserGroupApiV1(
                updated.getId(),
                updated.getRealm(),
                updated.getDisplayName()
        );
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

    private String normalizeTheme(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }
}
