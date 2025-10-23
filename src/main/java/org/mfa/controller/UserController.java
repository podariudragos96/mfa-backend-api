package org.mfa.controller;

import lombok.RequiredArgsConstructor;
import org.mfa.dto.ExportUserDto;
import org.mfa.dto.UserDto;
import org.mfa.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/realms/{realm}/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserDto> createUser(@PathVariable String realm, @RequestBody UserDto userDto) {
        return ResponseEntity.status(201).body(userService.createUser(realm, userDto));
    }

    @GetMapping
    public ResponseEntity<List<UserDto>> listUsers(@PathVariable String realm) {
        return ResponseEntity.ok(userService.listUsers(realm));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String realm, @PathVariable String userId) {
        userService.deleteUser(realm, userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{userId}")
    public ResponseEntity<UserDto> updateUser(
            @PathVariable String realm,
            @PathVariable String userId,
            @RequestBody UserDto userDto) {
        return ResponseEntity.ok(userService.updateUser(realm, userId, userDto));
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