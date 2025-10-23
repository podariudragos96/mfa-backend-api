package org.mfa.controller;

import org.mfa.dto.ExportUserDto;
import org.mfa.dto.UserDto;
import org.mfa.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/realms/{realm}/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserDto> createUser(@PathVariable String realm, @RequestBody UserDto userDto) {
        UserDto created = userService.createUser(realm, userDto);
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping
    public ResponseEntity<List<UserDto>> listUsers(@PathVariable String realm) {
        List<UserDto> users = userService.listUsers(realm);
        return ResponseEntity.ok(users);
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
        UserDto updated = userService.updateUser(realm, userId, userDto);
        return ResponseEntity.ok(updated);
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
