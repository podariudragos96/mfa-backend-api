package org.mfa.controller;

import lombok.RequiredArgsConstructor;
import org.mfa.dto.ExportUserDto;
import org.mfa.dto.UserApiV1;
import org.mfa.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/_{userGroupId}")
    public ResponseEntity<List<UserApiV1>> listUsers(@PathVariable String userGroupId) {
        return ResponseEntity.ok(userService.listUsers(userGroupId));
    }

    @PostMapping("/_{userGroupId}")
    public ResponseEntity<UserApiV1> createUser(@PathVariable String userGroupId, @RequestBody UserApiV1 userApiV1) {
        return ResponseEntity.status(201).body(userService.createUser(userGroupId, userApiV1));
    }

    @GetMapping("/_{userGroupId}/_{userId}")
    public ResponseEntity<UserApiV1> getUserById(@PathVariable String userGroupId, @PathVariable String userId) {
        return ResponseEntity.ok(userService.getUser(userGroupId, userId));
    }

    @PatchMapping("/_{userGroupId}/_{userId}")
    public ResponseEntity<UserApiV1> updateUser(
            @PathVariable String userGroupId,
            @PathVariable String userId,
            @RequestBody UserApiV1 userApiV1) {
        return ResponseEntity.ok(userService.updateUser(userGroupId, userId, userApiV1));
    }

    @DeleteMapping("/_{userGroupId}/_{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String userGroupId, @PathVariable String userId) {
        userService.deleteUser(userGroupId, userId);
        return ResponseEntity.noContent().build();
    }



    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<Void> triggerResetPassword(@PathVariable String realm, @PathVariable String userId) {
        userService.sendResetPasswordEmail(realm, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{userId}/verify-email")
    public ResponseEntity<Void> triggerEmailVerification(@PathVariable String realm, @PathVariable String userId) {
        userService.sendVerifyEmail(realm, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/export")
    public ResponseEntity<List<ExportUserDto>> exportUsers(@PathVariable String realm) {
        return ResponseEntity.ok(userService.exportUsers(realm));
    }
}