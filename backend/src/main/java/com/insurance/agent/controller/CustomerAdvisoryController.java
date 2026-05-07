package com.insurance.agent.controller;

import com.insurance.agent.dto.AdvisoryRequest;
import com.insurance.agent.service.CustomerAdvisoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/advisory")
public class CustomerAdvisoryController {

    private final CustomerAdvisoryService service;

    public CustomerAdvisoryController(CustomerAdvisoryService service) {
        this.service = service;
    }

    @GetMapping("/sessions")
    public ResponseEntity<?> listSessions() {
        return ResponseEntity.ok(service.getSessions());
    }

    @PostMapping("/sessions")
    public ResponseEntity<?> createSession(@RequestBody AdvisoryRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "客户名称不能为空"));
        }
        return ResponseEntity.ok(service.createSession(req.getName(), req.getSummary()));
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<?> getSession(@PathVariable long id) {
        return ResponseEntity.ok(service.getSession(id));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<?> deleteSession(@PathVariable long id) {
        service.deleteSession(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/sessions/{id}/analyze")
    public ResponseEntity<?> analyze(@PathVariable long id, @RequestBody AdvisoryRequest req) {
        if (req.getQuestion() == null || req.getQuestion().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "客户问题不能为空"));
        }
        if (req.getCustomerInfo() == null || req.getCustomerInfo().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "客户基本情况不能为空"));
        }
        return ResponseEntity.ok(service.analyze(id, req.getCustomerInfo(), req.getQuestion(), req.getChannel()));
    }
}
