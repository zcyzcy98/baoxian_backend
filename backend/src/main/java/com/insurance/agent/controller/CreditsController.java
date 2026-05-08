package com.insurance.agent.controller;

import com.insurance.agent.service.CreditsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/credits")
public class CreditsController {

    private final CreditsService creditsService;

    public CreditsController(CreditsService creditsService) {
        this.creditsService = creditsService;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return creditsService.getSummary();
    }

    @GetMapping("/records")
    public List<Map<String, Object>> records(
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return creditsService.getRecords(filter, page, size);
    }
}
