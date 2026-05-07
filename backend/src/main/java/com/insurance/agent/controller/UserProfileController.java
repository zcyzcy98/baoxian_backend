package com.insurance.agent.controller;

import com.insurance.agent.dto.ProfileDto;
import com.insurance.agent.service.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class UserProfileController {

    private final UserProfileService service;

    public UserProfileController(UserProfileService service) {
        this.service = service;
    }

    /** 读取个人信息（含平台绑定） */
    @GetMapping
    public ResponseEntity<ProfileDto> get() {
        return ResponseEntity.ok(service.getProfile());
    }

    /** 保存个人信息 */
    @PutMapping
    public ResponseEntity<ProfileDto> save(@RequestBody ProfileDto dto) {
        return ResponseEntity.ok(service.saveProfile(dto));
    }

    /** 绑定平台账号 */
    @PostMapping("/platform")
    public ResponseEntity<Void> bind(@RequestBody ProfileDto.PlatformBinding req) {
        service.bindPlatform(req);
        return ResponseEntity.ok().build();
    }

    /** 解绑平台账号 */
    @DeleteMapping("/platform/{platform}")
    public ResponseEntity<Void> unbind(@PathVariable String platform) {
        service.unbindPlatform(platform);
        return ResponseEntity.ok().build();
    }
}
