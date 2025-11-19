package org.mfa.service;

import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.RealmRepresentation;
import org.mfa.dto.ExportUserDto;
import org.mfa.dto.UserApiV1;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import jakarta.ws.rs.core.Response;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private final Keycloak keycloak;

    public UserService(Keycloak keycloak) {
        this.keycloak = keycloak;
    }

    public UserApiV1 createUser(String realm, UserApiV1 userApiV1) {
        UsersResource usersResource = keycloak.realm(realm).users();

        UserRepresentation userRep = new UserRepresentation();
        userRep.setUsername(userApiV1.getUsername());
        userRep.setEmail(userApiV1.getEmail());
        userRep.setFirstName(userApiV1.getFirstName());
        userRep.setLastName(userApiV1.getLastName());
        userRep.setEnabled(true);
        if (userApiV1.getEmail() != null) userRep.setEmailVerified(false);

        if (userApiV1.getPhoneNumber() != null && !userApiV1.getPhoneNumber().isBlank()) {
            userRep.setAttributes(Map.of("phone_number", List.of(userApiV1.getPhoneNumber().trim())));
        }

        Response response = usersResource.create(userRep);
        if (response.getStatus() >= 200 && response.getStatus() < 300) {
            String userId = getCreatedIdFromResponse(response);
            response.close();
            if (userApiV1.getPassword() != null && !userApiV1.getPassword().isEmpty()) {
                CredentialRepresentation passwordCred = new CredentialRepresentation();
                passwordCred.setType(CredentialRepresentation.PASSWORD);
                passwordCred.setTemporary(false);
                passwordCred.setValue(userApiV1.getPassword());
                usersResource.get(userId).resetPassword(passwordCred);
            } else {
                usersResource.get(userId).executeActionsEmail(Collections.singletonList("UPDATE_PASSWORD"));
            }
            userApiV1.setId(userId);
            userApiV1.setPassword(null);
            userApiV1.setEmailVerified(false);
            return userApiV1;
        } else {
            response.close();
            throw new RuntimeException("Failed to create user: HTTP " + response.getStatus());
        }
    }

    public List<UserApiV1> listUsers(String userGroupId) {
        String userGroupName = keycloak.realms()
                .findAll()
                .stream()
                .filter(r -> r.getId().equalsIgnoreCase(userGroupId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("User Group with given id doesn't exist"))
                .getRealm();
        UsersResource usersResource = keycloak.realm(userGroupName).users();
        return usersResource.list().stream().map(this::toUserApiV1).toList();
    }

    public UserApiV1 getUser(String userGroupId, String userId) {
        return listUsers(userGroupId)
                .stream()
                .filter(u -> u.getId().equalsIgnoreCase(userId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("User with given id doesn't exist"));
    }

    public UserApiV1 updateUser(String userGroupId, String userId, UserApiV1 userApiV1) {
        RealmRepresentation realmRep = keycloak.realms().findAll().stream()
                .filter(r -> r.getId().equalsIgnoreCase(userGroupId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Realm (user group) with id " + userGroupId + " not found"));

        RealmResource realmResource = keycloak.realm(realmRep.getRealm());

        var users = realmResource.users();
        var ur = users.get(userId);
        UserRepresentation rep = ur.toRepresentation();

        if (userApiV1.getUsername() != null) rep.setUsername(userApiV1.getUsername());
        if (userApiV1.getEmail() != null) rep.setEmail(userApiV1.getEmail());
        if (userApiV1.getFirstName() != null) rep.setFirstName(userApiV1.getFirstName());
        if (userApiV1.getLastName() != null) rep.setLastName(userApiV1.getLastName());

        Map<String, List<String>> attrs = rep.getAttributes();
        if (attrs == null) attrs = new java.util.HashMap<>();
        if (userApiV1.getPhoneNumber() != null) {
            String v = userApiV1.getPhoneNumber().trim();
            if (v.isEmpty()) {
                attrs.remove("phone_number");
            } else {
                attrs.put("phone_number", List.of(v));
            }
        }
        rep.setAttributes(attrs);

        ur.update(rep);

        if (userApiV1.getPassword() != null && !userApiV1.getPassword().isBlank()) {
            CredentialRepresentation cred = new CredentialRepresentation();
            cred.setType(CredentialRepresentation.PASSWORD);
            cred.setTemporary(false);
            cred.setValue(userApiV1.getPassword());
            ur.resetPassword(cred);
        }

        return toUserApiV1(ur.toRepresentation());
    }

    public void deleteUser(String userGroupId, String userId) {
        RealmRepresentation realmRep = keycloak.realms().findAll().stream()
                .filter(r -> r.getId().equalsIgnoreCase(userGroupId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "Realm (user group) with id " + userGroupId + " not found"));

        RealmResource realmResource = keycloak.realm(realmRep.getRealm());

        realmResource.users().delete(userId).close();
    }


    public void sendResetPasswordEmail(String realm, String userId) {
        keycloak.realms().realm(realm)
                .users().get(userId)
                .executeActionsEmail(Collections.singletonList("UPDATE_PASSWORD"));
    }

    public void sendVerifyEmail(String realm, String userId) {
        keycloak.realms().realm(realm)
                .users().get(userId)
                .executeActionsEmail(Collections.singletonList("VERIFY_EMAIL"));
    }

    public List<ExportUserDto> exportUsers(String realm) {
        var usersResource = keycloak.realm(realm).users();
        return usersResource.list().stream().map(u -> {
            String fn = u.getFirstName() == null ? "" : u.getFirstName().trim();
            String ln = u.getLastName() == null ? "" : u.getLastName().trim();
            String displayName = (fn + " " + ln).trim();
            if (displayName.isEmpty()) displayName = u.getUsername();
            return new ExportUserDto(u.getUsername(), displayName, null); // Passwords never exported
        }).toList();
    }

    private String getCreatedIdFromResponse(Response response) {
        String location = response.getHeaderString("Location");
        if (location != null) {
            int idx = location.lastIndexOf('/');
            if (idx != -1) {
                return location.substring(idx + 1);
            }
        }
        return null;
    }

    private UserApiV1 toUserApiV1(UserRepresentation u) {
        UserApiV1 userApiV1 = new UserApiV1();
        userApiV1.setId(u.getId());
        userApiV1.setUsername(u.getUsername());
        userApiV1.setEmail(u.getEmail());
        userApiV1.setFirstName(u.getFirstName());
        userApiV1.setLastName(u.getLastName());
        userApiV1.setEmailVerified(Boolean.TRUE.equals(u.isEmailVerified()));

        Map<String, List<String>> attrs = u.getAttributes();
        if (attrs != null && attrs.get("phone_number") != null && !attrs.get("phone_number").isEmpty()) {
            userApiV1.setPhoneNumber(attrs.get("phone_number").get(0));
        }
        return userApiV1;
    }
}
