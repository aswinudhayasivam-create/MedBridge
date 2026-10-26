package com.medbridge.controller;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/roles")
public class RoleDemoController {
    @GetMapping("/donor") @PreAuthorize("hasRole('DONOR')")
    public Map<String,String> donor() { return Map.of("access","DONOR"); }
    @GetMapping("/ngo") @PreAuthorize("hasRole('NGO')")
    public Map<String,String> ngo() { return Map.of("access","NGO"); }
    @GetMapping("/admin") @PreAuthorize("hasRole('ADMIN')")
    public Map<String,String> admin() { return Map.of("access","ADMIN"); }
}
