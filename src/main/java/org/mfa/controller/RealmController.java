package org.mfa.controller;

import lombok.RequiredArgsConstructor;
import org.mfa.dto.CreateRealmRequest;
import org.mfa.dto.RealmSummaryDto;
import org.mfa.service.RealmService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/realms")
@RequiredArgsConstructor
public class RealmController {

    private final RealmService realmService;

    @PostMapping
    public ResponseEntity<Void> createRealm(@RequestBody CreateRealmRequest req) {
        realmService.createRealm(req);
        return ResponseEntity.status(201).build();
    }

    @GetMapping
    public ResponseEntity<List<RealmSummaryDto>> listRealms() {
        return ResponseEntity.ok(realmService.listRealms());
    }

    @DeleteMapping("/{realmName}")
    public ResponseEntity<Void> deleteRealm(@PathVariable String realmName) {
        realmService.deleteRealm(realmName);
        return ResponseEntity.noContent().build();
    }
}
