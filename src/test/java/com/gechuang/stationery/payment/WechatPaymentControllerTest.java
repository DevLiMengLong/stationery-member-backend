package com.gechuang.stationery.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gechuang.stationery.common.RestResponse;
import com.gechuang.stationery.mainflow.MainFlowService;
import com.wechat.pay.java.core.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class WechatPaymentControllerTest {

    private final MainFlowService mainFlowService = mock(MainFlowService.class);
    private final WechatPaymentOrderService orderService = mock(WechatPaymentOrderService.class);
    private final WechatPaymentController controller = new WechatPaymentController(mainFlowService, orderService);

    @Test
    void createsNativeOrderForAuthenticatedMerchant() {
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        Map<String, Object> store = Map.of("id", 1L, "shopName", "测试门店");
        when(mainFlowService.requireMerchant(servletRequest)).thenReturn(store);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(orderService.createNativeOrder(store, 99L, new BigDecimal("100.00"),
                "request-1", "127.0.0.1"))
                .thenReturn(Map.of("outTradeNo", "ORDER-1", "codeUrl", "weixin://wxpay/example"));

        RestResponse<Map<String, Object>> response = controller.createNativeOrder(
                new WechatPaymentController.NativeOrderRequest(
                        99L, new BigDecimal("100.00"), "request-1"), servletRequest);

        assertThat(response.getData().get("codeUrl")).isEqualTo("weixin://wxpay/example");
    }

    @Test
    void verifiedCallbackReturnsNoContentAndPassesRawRequestData() {
        ResponseEntity<Void> response = controller.notifyPayment(
                "PUB_KEY_ID", "signature", "1700000000", "nonce", "{\"resource\":{}}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(orderService).handleNotification(any());
    }

    @Test
    void invalidSignatureReturnsBadRequestButPersistenceFailureRemainsRetriable() {
        when(orderService.handleNotification(any()))
                .thenThrow(new ValidationException("invalid signature"));

        ResponseEntity<Void> invalid = controller.notifyPayment(
                "PUB_KEY_ID", "signature", "1700000000", "nonce", "{}");

        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        doThrow(new IllegalStateException("database unavailable"))
                .when(orderService).handleNotification(any());
        assertThatThrownBy(() -> controller.notifyPayment(
                "PUB_KEY_ID", "signature", "1700000000", "nonce", "{}"))
                .isInstanceOf(IllegalStateException.class);
    }
}
