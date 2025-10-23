package org.mfa.service;

import org.mfa.dto.ExportUserDto;
import org.mfa.dto.UserDto;
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

    public UserDto createUser(String realm, UserDto userDto) {
        UsersResource usersResource = keycloak.realm(realm).users();

        UserRepresentation userRep = new UserRepresentation();
        userRep.setUsername(userDto.getUsername());
        userRep.setEmail(userDto.getEmail());
        userRep.setFirstName(userDto.getFirstName());
        userRep.setLastName(userDto.getLastName());
        userRep.setEnabled(true);
        if (userDto.getEmail() != null) userRep.setEmailVerified(false);

        if (userDto.getPhoneNumber() != null && !userDto.getPhoneNumber().isBlank()) {
            userRep.setAttributes(Map.of("phone_number", List.of(userDto.getPhoneNumber().trim())));
        }

        Response response = usersResource.create(userRep);
        if (response.getStatus() >= 200 && response.getStatus() < 300) {
            String userId = getCreatedIdFromResponse(response);
            response.close();
            if (userDto.getPassword() != null && !userDto.getPassword().isEmpty()) {
                CredentialRepresentation passwordCred = new CredentialRepresentation();
                passwordCred.setType(CredentialRepresentation.PASSWORD);
                passwordCred.setTemporary(false);
                passwordCred.setValue(userDto.getPassword());
                usersResource.get(userId).resetPassword(passwordCred);
            } else {
                usersResource.get(userId).executeActionsEmail(Collections.singletonList("UPDATE_PASSWORD"));
            }
            userDto.setId(userId);
            userDto.setPassword(null);
            userDto.setEmailVerified(false);
            return userDto;
        } else {
            response.close();
            throw new RuntimeException("Failed to create user: HTTP " + response.getStatus());
        }
    }

    public List<UserDto> listUsers(String realm) {
        UsersResource usersResource = keycloak.realm(realm).users();
        return usersResource.list().stream().map(this::toUserDto).toList();
    }

    public UserDto updateUser(String realm, String userId, UserDto userDto) {
        var users = keycloak.realm(realm).users();
        var ur = users.get(userId);
        var rep = ur.toRepresentation();

        if (userDto.getUsername() != null) rep.setUsername(userDto.getUsername());
        if (userDto.getEmail() != null) rep.setEmail(userDto.getEmail());
        if (userDto.getFirstName() != null) rep.setFirstName(userDto.getFirstName());
        if (userDto.getLastName() != null) rep.setLastName(userDto.getLastName());

        Map<String, List<String>> attrs = rep.getAttributes();
        if (attrs == null) attrs = new java.util.HashMap<>();
        if (userDto.getPhoneNumber() != null) {
            String v = userDto.getPhoneNumber().trim();
            if (v.isEmpty()) attrs.remove("phone_number");
            else attrs.put("phone_number", List.of(v));
        }
        rep.setAttributes(attrs);

        ur.update(rep);

        if (userDto.getPassword() != null && !userDto.getPassword().isBlank()) {
            CredentialRepresentation cred = new CredentialRepresentation();
            cred.setType(CredentialRepresentation.PASSWORD);
            cred.setTemporary(false);
            cred.setValue(userDto.getPassword());
            ur.resetPassword(cred);
        }

        return toUserDto(ur.toRepresentation());
    }

    public void deleteUser(String realm, String userId) {
        keycloak.realms().realm(realm).users().delete(userId);
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

    private UserDto toUserDto(UserRepresentation u) {
        UserDto dto = new UserDto();
        dto.setId(u.getId());
        dto.setUsername(u.getUsername());
        dto.setEmail(u.getEmail());
        dto.setFirstName(u.getFirstName());
        dto.setLastName(u.getLastName());
        dto.setEmailVerified(Boolean.TRUE.equals(u.isEmailVerified()));

        Map<String, List<String>> attrs = u.getAttributes();
        if (attrs != null && attrs.get("phone_number") != null && !attrs.get("phone_number").isEmpty()) {
            dto.setPhoneNumber(attrs.get("phone_number").get(0));
        }
        return dto;
    }
}
