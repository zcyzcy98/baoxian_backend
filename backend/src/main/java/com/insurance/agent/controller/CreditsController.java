package com.insurance.agent.controller;

import com.insurance.agent.service.AuthService;
import com.insurance.agent.service.CreditsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/credits")
public class CreditsController {

    private final CreditsService creditsService;
    private final AuthService authService;

    public CreditsController(CreditsService creditsService, AuthService authService) {
        this.creditsService = creditsService;
        this.authService = authService;
    }

    @GetMapping("/balance")
    public Map<String, Object> balance(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        long uid = resolveUserId(auth);
        int bal = creditsService.getBalance(uid);
        return Map.of("balance", bal, "total", 8000);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return creditsService.getSummary(resolveUserId(auth));
    }

    @GetMapping("/records")
    public List<Map<String, Object>> records(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return creditsService.getRecords(resolveUserId(auth), filter, page, size);
    }

    private long resolveUserId(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) throw new IllegalArgumentException("未登录");
        return authService.userIdByToken(auth.substring(7));
    }
}
