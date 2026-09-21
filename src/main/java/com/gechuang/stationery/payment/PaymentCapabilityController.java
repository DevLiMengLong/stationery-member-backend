package com.gechuang.stationery.payment;

import com.gechuang.stationery.common.RestResponse;
import com.gechuang.stationery.mainflow.MainFlowService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mini/payments")
public class PaymentCapabilityController {

    private final MainFlowService mainFlowService;
    private final StorePaymentCapabilityService capabilityService;

    public PaymentCapabilityController(MainFlowService mainFlowService,
                                       StorePaymentCapabilityService capabilityService) {
        this.mainFlowService = mainFlowService;
        this.capabilityService = capabilityService;
    }

    @GetMapping("/capability")
    public RestResponse<PaymentCapability> capability(HttpServletRequest request) {
        Map<String, Object> store = mainFlowService.requireMerchant(request);
        return RestResponse.success(capabilityService.capability(Long.valueOf(String.valueOf(store.get("id")))));
    }
}
