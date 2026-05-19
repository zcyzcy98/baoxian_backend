package com.insurance.agent.controller;

import com.insurance.agent.service.AuthService;
import com.insurance.agent.service.WechatPayService;
import com.insurance.agent.service.WechatPayService.OrderInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信支付订单接口。
 *
 * POST /api/pay/order    创建订单，返回二维码 URL
 * GET  /api/pay/status/{outTradeNo}  查询订单状态（前端轮询）
 * POST /api/pay/notify   微信支付异步回调（无需鉴权）
 * GET  /api/pay/products 获取商品列表
 */
@RestController
@RequestMapping("/api/pay")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final WechatPayService wechatPayService;
    private final AuthService authService;

    public OrderController(WechatPayService wechatPayService, AuthService authService) {
        this.wechatPayService = wechatPayService;
        this.authService = authService;
    }

    // ─── 商品列表 ─────────────────────────────────────────────────

    @GetMapping("/products")
    public ResponseEntity<?> products() {
        var list = wechatPayService.listPackages().stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.id());
            m.put("name", p.name());
            m.put("credits", p.credits());
            m.put("priceFen", p.priceFen());
            m.put("priceYuan", p.priceFen() / 100.0);
            m.put("saveFen", p.saveFen());
            m.put("saveYuan", p.saveFen() / 100.0);
            return m;
        }).toList();
        return ResponseEntity.ok(Map.of("products", list));
    }

    // ─── 创建会员订单 ─────────────────────────────────────────────

    @PostMapping("/membership-order")
    public ResponseEntity<?> createMembershipOrder(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        String phone = resolvePhone(auth);
        try {
            Map<String, String> result = wechatPayService.createMembershipOrder(phone);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        }
    }

    // ─── 创建订单 ─────────────────────────────────────────────────

    /**
     * 请求体：{ "product": "credits_10" }
     * 响应：{ "outTradeNo": "...", "codeUrl": "weixin://wxpay/..." }
     * 前端用 codeUrl 生成二维码展示给用户扫码。
     */
    @PostMapping("/order")
    public ResponseEntity<?> createOrder(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, String> body) {

        String phone = resolvePhone(auth);
        String product = body.get("product");
        if (product == null || product.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "商品不能为空"));
        }

        try {
            Map<String, String> result = wechatPayService.createOrder(phone, product);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── 查询订单状态（前端轮询）────────────────────────────────────

    /**
     * 响应：{ "status": "PENDING" | "PAID" | "NOT_FOUND" }
     */
    @GetMapping("/status/{outTradeNo}")
    public ResponseEntity<?> queryStatus(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable String outTradeNo) {

        resolvePhone(auth); // 需要登录
        OrderInfo order = wechatPayService.getOrder(outTradeNo);
        if (order == null) {
            return ResponseEntity.ok(Map.of("status", "NOT_FOUND"));
        }
        return ResponseEntity.ok(Map.of(
                "status", order.status(),
                "product", order.product(),
                "amountYuan", order.amountFen() / 100.0
        ));
    }

    // ─── 微信支付回调（公开，无需鉴权）──────────────────────────────

    /**
     * 微信支付成功后会 POST 这个地址，必须是公网 HTTPS。
     * 验签通过 + 支付成功 → 给用户开通访问权限。
     */
    @PostMapping("/notify")
    public ResponseEntity<String> notify(
            @RequestBody String body,
            @RequestHeader("Wechatpay-Timestamp") String timestamp,
            @RequestHeader("Wechatpay-Nonce") String nonce,
            @RequestHeader("Wechatpay-Signature") String signature,
            @RequestHeader("Wechatpay-Serial") String serialNo) {

        log.info("[WechatPay] 收到回调，timestamp={}", timestamp);

        OrderInfo paid = wechatPayService.handleNotify(body, timestamp, nonce, signature, serialNo);
        if (paid != null) {
            if ("membership".equals(paid.product())) {
                authService.grantAccess(paid.phone());
                log.info("[WechatPay] 会员开通成功 phone={}", paid.phone());
            } else {
                int credits = wechatPayService.getCredits(paid.product());
                authService.addCredits(paid.phone(), credits);
                log.info("[WechatPay] 积分到账 phone={} product={} credits={}", paid.phone(), paid.product(), credits);
            }
        }

        // 微信要求：成功处理返回 {"code":"SUCCESS"}，否则会重试
        return ResponseEntity.ok("{\"code\":\"SUCCESS\",\"message\":\"成功\"}");
    }

    // ─── 工具 ─────────────────────────────────────────────────────

    private String resolvePhone(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new IllegalArgumentException("未登录");
        }
        String token = auth.substring(7);
        String phone = authService.phoneByToken(token);
        if (phone == null) throw new IllegalArgumentException("登录已过期");
        return phone;
    }
}
