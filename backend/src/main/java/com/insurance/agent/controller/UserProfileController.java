package com.insurance.agent.controller;

import com.insurance.agent.dto.ProfileDto;
import com.insurance.agent.service.AuthService;
import com.insurance.agent.service.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class UserProfileController {

    private final UserProfileService service;
    private final AuthService authService;

    public UserProfileController(UserProfileService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    /** 读取个人信息（含平台绑定） */
    @GetMapping
    public ResponseEntity<ProfileDto> get(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return ResponseEntity.ok(service.getProfile(resolveUserId(auth)));
    }

    /** 保存个人信息 */
    @PutMapping
    public ResponseEntity<ProfileDto> save(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody ProfileDto dto) {
        return ResponseEntity.ok(service.saveProfile(resolveUserId(auth), dto));
    }

    /** 绑定平台账号 */
    @PostMapping("/platform")
    public ResponseEntity<Void> bind(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody ProfileDto.PlatformBinding req) {
        service.bindPlatform(resolveUserId(auth), req);
        return ResponseEntity.ok().build();
    }

    /** 解绑平台账号 */
    @DeleteMapping("/platform/{platform}")
    public ResponseEntity<Void> unbind(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable String platform) {
        service.unbindPlatform(resolveUserId(auth), platform);
        return ResponseEntity.ok().build();
    }

    private long resolveUserId(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new IllegalArgumentException("未登录");
        }
        return authService.userIdByToken(auth.substring(7));
    }
}
