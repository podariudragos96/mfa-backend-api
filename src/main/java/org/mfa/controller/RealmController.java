package org.mfa.controller;

import org.mfa.dto.CreateRealmRequest;
import org.mfa.dto.RealmSummaryDto;
import org.mfa.dto.ThemeSettingsDto;
import org.mfa.dto.ThemeUpdateRequest;
import org.mfa.service.RealmService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/realms")
public class RealmController {

    private final RealmService realmService;

    public RealmController(RealmService realmService) {
        this.realmService = realmService;
    }

    @PostMapping
    public ResponseEntity<Void> createRealm(@RequestBody CreateRealmRequest req) {
        realmService.createRealm(req);
        return ResponseEntity.status(HttpStatus.CREATED).build();
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

    @GetMapping("/{realmName}/theme")
    public ResponseEntity<ThemeSettingsDto> getTheme(@PathVariable String realmName) {
        return ResponseEntity.ok(realmService.getTheme(realmName));
    }

    @PutMapping("/{realmName}/theme")
    public ResponseEntity<ThemeSettingsDto> updateTheme(@PathVariable String realmName,
                                                        @RequestBody ThemeUpdateRequest req) {
        return ResponseEntity.ok(realmService.updateTheme(realmName, req));
    }
}
