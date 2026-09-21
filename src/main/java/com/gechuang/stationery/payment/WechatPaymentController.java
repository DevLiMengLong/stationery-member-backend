package com.gechuang.stationery.payment;

import com.gechuang.stationery.common.RestResponse;
import com.gechuang.stationery.mainflow.MainFlowService;
import com.gechuang.stationery.payment.gateway.WechatPaymentGateway;
import com.wechat.pay.java.core.exception.WechatPayException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WechatPaymentController {

    private final MainFlowService mainFlowService;
    private final WechatPaymentOrderService orderService;

    public WechatPaymentController(MainFlowService mainFlowService, WechatPaymentOrderService orderService) {
        this.mainFlowService = mainFlowService;
        this.orderService = orderService;
    }

    @PostMapping("/api/mini/payments/native-orders")
    public RestResponse<Map<String, Object>> createNativeOrder(
            @Valid @RequestBody NativeOrderRequest body, HttpServletRequest request) {
        Map<String, Object> store = mainFlowService.requireMerchant(request);
        return RestResponse.success(orderService.createNativeOrder(
                store, body.memberId(), body.amount(), body.idempotencyKey(), request.getRemoteAddr()));
    }

    @GetMapping("/api/mini/payments/native-orders/{outTradeNo}")
    public RestResponse<Map<String, Object>> queryNativeOrder(@PathVariable String outTradeNo,
                                                              HttpServletRequest request) {
        Map<String, Object> store = mainFlowService.requireMerchant(request);
        return RestResponse.success(orderService.getOrder(longValue(store.get("id")), outTradeNo));
    }

    @PostMapping("/api/payments/wechat/notify")
    public ResponseEntity<Void> notifyPayment(
            @RequestHeader("Wechatpay-Serial") String serialNumber,
            @RequestHeader("Wechatpay-Signature") String signature,
            @RequestHeader("Wechatpay-Timestamp") String timestamp,
            @RequestHeader("Wechatpay-Nonce") String nonce,
            @RequestBody String rawBody) {
        try {
            orderService.handleNotification(new WechatPaymentGateway.WechatNotification(
                    serialNumber, signature, timestamp, nonce, rawBody));
            return ResponseEntity.noContent().build();
        } catch (WechatPayException | InvalidWechatPaymentNotificationException exception) {
            return ResponseEntity.badRequest().build();
        }
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
    }

    public record NativeOrderRequest(@NotNull Long memberId,
                                     @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
                                     @NotBlank String idempotencyKey) {
    }
}
