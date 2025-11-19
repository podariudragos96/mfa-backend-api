package org.mfa.controller;

import lombok.RequiredArgsConstructor;
import org.mfa.dto.UserGroupApiV1;
import org.mfa.dto.UserGroupCreateRequestApiV1;
import org.mfa.service.UserGroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/userGroups")
@RequiredArgsConstructor
public class UserGroupController {

    private final UserGroupService userGroupService;

    @PostMapping
    public ResponseEntity<Void> createUserGroups(@RequestBody UserGroupCreateRequestApiV1 req) {
        userGroupService.createRealm(req);
        return ResponseEntity.status(201).build();
    }

    @GetMapping
    public ResponseEntity<List<UserGroupApiV1>> listUserGroups() {
        return ResponseEntity.ok(userGroupService.listUserGroups());
    }

    @GetMapping("/_{userGroupId}")
    public ResponseEntity<UserGroupApiV1> getUserGroup(@PathVariable String userGroupId) {
        return ResponseEntity.ok(userGroupService.getUserGroupById(userGroupId));
    }

    @PatchMapping("/_{userGroupId}")
    public ResponseEntity<UserGroupApiV1> patchUserGroup(@PathVariable String userGroupId, @RequestBody UserGroupApiV1 userGroupApiV1) {
        return ResponseEntity.ok(userGroupService.patchUserGroupById(userGroupId, userGroupApiV1));
    }

    @DeleteMapping("/_{userGroupId}")
    public ResponseEntity<Void> deleteRealm(@PathVariable String realmName) {
        userGroupService.deleteRealm(realmName);
        return ResponseEntity.noContent().build();
    }
}
