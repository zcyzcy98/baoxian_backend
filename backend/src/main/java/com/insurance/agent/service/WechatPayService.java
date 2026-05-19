package com.insurance.agent.service;

import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.payments.model.Transaction;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WechatPayService {

    private static final Logger log = LoggerFactory.getLogger(WechatPayService.class);

    private final DataSource dataSource;

    public WechatPayService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record PackageInfo(String id, String name, int credits, int priceFen, int saveFen) {}

    public record OrderInfo(String outTradeNo, String phone, String product,
                            int amountFen, long createdAt, String status) {}

    // ─── 配置 ────────────────────────────────────────────────────

    @Value("${wxpay.mch-id:}")
    private String mchId;

    @Value("${wxpay.app-id:}")
    private String appId;

    @Value("${wxpay.api-v3-key:}")
    private String apiV3Key;

    @Value("${wxpay.private-key-path:}")
    private String privateKeyPath;

    @Value("${wxpay.mch-serial-no:}")
    private String mchSerialNo;

    @Value("${wxpay.public-key-path:}")
    private String publicKeyPath;

    @Value("${wxpay.public-key-id:}")
    private String publicKeyId;

    @Value("${wxpay.notify-url:}")
    private String notifyUrl;

    @Value("${wxpay.test-mode:false}")
    private boolean testMode;

    private RSAPublicKeyConfig payConfig;

    @PostConstruct
    public void init() {
        if (!isConfigured()) {
            log.warn("[WechatPay] 未配置微信支付凭证，支付功能不可用。");
            return;
        }
        try {
            String privateKey = Files.readString(Paths.get(privateKeyPath));
            String publicKey = Files.readString(Paths.get(publicKeyPath));
            payConfig = new RSAPublicKeyConfig.Builder()
                    .merchantId(mchId)
                    .privateKey(privateKey)
                    .merchantSerialNumber(mchSerialNo)
                    .apiV3Key(apiV3Key)
                    .publicKey(publicKey)
                    .publicKeyId(publicKeyId)
                    .build();
            log.info("[WechatPay] 微信支付初始化成功，mchId={}", mchId);
        } catch (Exception e) {
            log.error("[WechatPay] 初始化失败: {}", e.getMessage(), e);
        }
    }

    public boolean isConfigured() {
        return !isBlank(mchId) && !isBlank(appId) && !isBlank(apiV3Key)
                && !isBlank(privateKeyPath) && !isBlank(mchSerialNo)
                && !isBlank(publicKeyPath) && !isBlank(publicKeyId) && !isBlank(notifyUrl);
    }

    // ─── 套餐 ────────────────────────────────────────────────────

    public List<PackageInfo> listPackages() {
        List<PackageInfo> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                "SELECT id, name, credits, price_fen, save_fen FROM credit_packages WHERE enabled = TRUE ORDER BY sort_order")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new PackageInfo(
                        rs.getString("id"), rs.getString("name"),
                        rs.getInt("credits"), rs.getInt("price_fen"), rs.getInt("save_fen")));
                }
            }
        } catch (Exception e) {
            log.error("[WechatPay] 加载套餐失败: {}", e.getMessage(), e);
        }
        return result;
    }

    private PackageInfo findPackage(String id) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                "SELECT id, name, credits, price_fen, save_fen FROM credit_packages WHERE id = ? AND enabled = TRUE")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PackageInfo(
                        rs.getString("id"), rs.getString("name"),
                        rs.getInt("credits"), rs.getInt("price_fen"), rs.getInt("save_fen"));
                }
            }
        } catch (Exception e) {
            log.error("[WechatPay] 查询套餐失败 id={}: {}", id, e.getMessage(), e);
        }
        return null;
    }

    // ─── 创建订单 ─────────────────────────────────────────────────

    public Map<String, String> createMembershipOrder(String phone) {
        requireConfigured();
        String outTradeNo = "MEM" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        int amountFen = testMode ? 1 : 149900;

        PrepayRequest request = new PrepayRequest();
        request.setAppid(appId);
        request.setMchid(mchId);
        request.setDescription("承知年度会员");
        request.setOutTradeNo(outTradeNo);
        request.setNotifyUrl(notifyUrl);

        Amount amount = new Amount();
        amount.setTotal(amountFen);
        amount.setCurrency("CNY");
        request.setAmount(amount);

        NativePayService service = new NativePayService.Builder().config(payConfig).build();
        PrepayResponse response = service.prepay(request);
        String codeUrl = response.getCodeUrl();

        insertOrder(outTradeNo, phone, "membership", amountFen);
        log.info("[WechatPay] 会员下单成功 outTradeNo={} phone={} amountFen={}", outTradeNo, phone, amountFen);
        return Map.of("outTradeNo", outTradeNo, "codeUrl", codeUrl, "amountFen", String.valueOf(amountFen));
    }

    public Map<String, String> createOrder(String phone, String product) {
        requireConfigured();
        PackageInfo pkg = findPackage(product);
        if (pkg == null) throw new IllegalArgumentException("未知商品: " + product);

        String outTradeNo = "PAY" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        int payFen = testMode ? 1 : pkg.priceFen();

        PrepayRequest request = new PrepayRequest();
        request.setAppid(appId);
        request.setMchid(mchId);
        request.setDescription(pkg.name());
        request.setOutTradeNo(outTradeNo);
        request.setNotifyUrl(notifyUrl);

        Amount amount = new Amount();
        amount.setTotal(payFen);
        amount.setCurrency("CNY");
        request.setAmount(amount);

        NativePayService service = new NativePayService.Builder().config(payConfig).build();
        PrepayResponse response = service.prepay(request);
        String codeUrl = response.getCodeUrl();

        insertOrder(outTradeNo, phone, product, pkg.priceFen());
        log.info("[WechatPay] 下单成功 outTradeNo={} phone={} product={}", outTradeNo, phone, product);
        return Map.of("outTradeNo", outTradeNo, "codeUrl", codeUrl);
    }

    // ─── 处理微信回调 ─────────────────────────────────────────────

    public OrderInfo handleNotify(String body,
                                  String timestamp, String nonce,
                                  String signature, String serialNo) {
        requireConfigured();
        try {
            NotificationParser parser = new NotificationParser((NotificationConfig) payConfig);

            RequestParam requestParam = new RequestParam.Builder()
                    .serialNumber(serialNo)
                    .nonce(nonce)
                    .signature(signature)
                    .timestamp(timestamp)
                    .body(body)
                    .build();

            Transaction transaction = parser.parse(requestParam, Transaction.class);
            String outTradeNo = transaction.getOutTradeNo();
            String tradeState = transaction.getTradeState().name();

            log.info("[WechatPay] 回调 outTradeNo={} tradeState={}", outTradeNo, tradeState);

            if ("SUCCESS".equals(tradeState)) {
                OrderInfo order = getOrder(outTradeNo);
                if (order != null && "PENDING".equals(order.status())) {
                    markPaid(outTradeNo);
                    return getOrder(outTradeNo);
                }
            }
        } catch (Exception e) {
            log.error("[WechatPay] 回调处理失败: {}", e.getMessage(), e);
        }
        return null;
    }

    // ─── 查询订单 ─────────────────────────────────────────────────

    public OrderInfo getOrder(String outTradeNo) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                "SELECT out_trade_no, phone, product, amount_fen, created_at, status FROM orders WHERE out_trade_no = ?")) {
            ps.setString(1, outTradeNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new OrderInfo(
                        rs.getString("out_trade_no"),
                        rs.getString("phone"),
                        rs.getString("product"),
                        rs.getInt("amount_fen"),
                        rs.getTimestamp("created_at").getTime(),
                        rs.getString("status")
                    );
                }
            }
        } catch (Exception e) {
            log.error("[WechatPay] 查询订单失败 outTradeNo={}: {}", outTradeNo, e.getMessage(), e);
        }
        return null;
    }

    public int getCredits(String product) {
        PackageInfo pkg = findPackage(product);
        return pkg == null ? 0 : pkg.credits();
    }

    // ─── 数据库操作 ───────────────────────────────────────────────

    private void insertOrder(String outTradeNo, String phone, String product, int amountFen) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                "INSERT INTO orders (out_trade_no, phone, product, amount_fen, status) VALUES (?, ?, ?, ?, 'PENDING')")) {
            ps.setString(1, outTradeNo);
            ps.setString(2, phone);
            ps.setString(3, product);
            ps.setInt(4, amountFen);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("[WechatPay] 写入订单失败: {}", e.getMessage(), e);
            throw new RuntimeException("订单创建失败", e);
        }
    }

    private void markPaid(String outTradeNo) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                "UPDATE orders SET status = 'PAID', paid_at = NOW() WHERE out_trade_no = ? AND status = 'PENDING'")) {
            ps.setString(1, outTradeNo);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("[WechatPay] 更新订单状态失败 outTradeNo={}: {}", outTradeNo, e.getMessage(), e);
        }
    }

    // ─── 工具 ────────────────────────────────────────────────────

    private void requireConfigured() {
        if (!isConfigured() || payConfig == null) {
            throw new IllegalStateException("微信支付未配置，请在 application-local.yml 填入 wxpay.* 配置。");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
