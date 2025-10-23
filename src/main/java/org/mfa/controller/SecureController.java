package org.mfa.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/secure")
public class SecureController {

    @GetMapping("/ping")
    public ResponseEntity<?> ping() {
        return ResponseEntity.ok().body(java.util.Map.of("message", "You are authenticated! 🎉"));
    }
}
