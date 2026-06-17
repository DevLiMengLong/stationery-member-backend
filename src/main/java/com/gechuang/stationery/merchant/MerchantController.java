package com.gechuang.stationery.merchant;

import com.gechuang.stationery.common.PageResult;
import com.gechuang.stationery.common.RestResponse;
import com.gechuang.stationery.mainflow.MainFlowService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant")
public class MerchantController {

    private final MainFlowService mainFlowService;

    public MerchantController(MainFlowService mainFlowService) {
        this.mainFlowService = mainFlowService;
    }

    @GetMapping("/dashboard")
    public RestResponse<Map<String, Object>> dashboard(HttpServletRequest request) {
        Map<String, Object> store = mainFlowService.requireMerchant(request);
        return RestResponse.success(mainFlowService.merchantDashboard(store));
    }

    @GetMapping("/profile")
    public RestResponse<Map<String, Object>> profile(HttpServletRequest request) {
        return RestResponse.success(mainFlowService.requireMerchant(request));
    }

    @PutMapping("/profile")
    public RestResponse<Map<String, Object>> updateProfile(@RequestBody Map<String, Object> body,
                                                           HttpServletRequest request) {
        Map<String, Object> store = mainFlowService.requireMerchant(request);
        return RestResponse.success(mainFlowService.updateMerchantProfile(store, body));
    }

    @PutMapping("/password")
    public RestResponse<Void> updatePassword(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        mainFlowService.updateMerchantPassword(mainFlowService.requireMerchant(request), body);
        return RestResponse.success();
    }

    @GetMapping("/recharge-tiers")
    public RestResponse<List<Map<String, Object>>> rechargeTiers(HttpServletRequest request) {
        Map<String, Object> store = mainFlowService.requireMerchant(request);
        return RestResponse.success(mainFlowService.getRechargeTiers(longValue(store.get("id"))));
    }

    @PutMapping("/recharge-tiers/{tierNo}")
    public RestResponse<Map<String, Object>> updateRechargeTier(@PathVariable int tierNo,
                                                                @RequestBody Map<String, Object> body,
                                                                HttpServletRequest request) {
        Map<String, Object> store = mainFlowService.requireMerchant(request);
        return RestResponse.success(mainFlowService.updateRechargeTier(longValue(store.get("id")), tierNo, body));
    }

    @GetMapping("/campaigns")
    public RestResponse<List<Map<String, Object>>> campaigns(HttpServletRequest request) {
        Map<String, Object> store = mainFlowService.requireMerchant(request);
        return RestResponse.success(mainFlowService.campaigns(longValue(store.get("id"))));
    }

    @GetMapping("/members")
    public RestResponse<PageResult<Map<String, Object>>> members(@RequestParam(required = false) String keyword,
                                                                 @RequestParam(defaultValue = "1") int pageNo,
                                                                 @RequestParam(defaultValue = "20") int pageSize,
                                                                 HttpServletRequest request) {
        Map<String, Object> store = mainFlowService.requireMerchant(request);
        return RestResponse.success(mainFlowService.merchantMembers(longValue(store.get("id")), keyword, pageNo, pageSize));
    }

    @GetMapping("/members/lookup")
    public RestResponse<Map<String, Object>> lookupMember(@RequestParam String keyword, HttpServletRequest request) {
        Map<String, Object> store = mainFlowService.requireMerchant(request);
        return RestResponse.success(mainFlowService.lookupMember(longValue(store.get("id")), keyword));
    }

    @PostMapping("/members")
    public RestResponse<Map<String, Object>> createMember(@RequestBody Map<String, Object> body,
                                                          HttpServletRequest request) {
        Map<String, Object> store = mainFlowService.requireMerchant(request);
        return RestResponse.success(mainFlowService.createMember(longValue(store.get("id")), body));
    }

    @GetMapping("/members/{id}")
    public RestResponse<Map<String, Object>> memberDetail(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> store = mainFlowService.requireMerchant(request);
        return RestResponse.success(mainFlowService.memberDetail(longValue(store.get("id")), id));
    }

    @PutMapping("/members/{id}")
    public RestResponse<Map<String, Object>> updateMember(@PathVariable Long id,
                                                          @RequestBody Map<String, Object> body,
                                                          HttpServletRequest request) {
        Map<String, Object> store = mainFlowService.requireMerchant(request);
        return RestResponse.success(mainFlowService.updateMember(longValue(store.get("id")), id, body));
    }

    @DeleteMapping("/members/{id}")
    public RestResponse<Void> deleteMember(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> store = mainFlowService.requireMerchant(request);
        mainFlowService.softDeleteMember(longValue(store.get("id")), id);
        return RestResponse.success();
    }

    @GetMapping("/members/{id}/transactions")
    public RestResponse<PageResult<Map<String, Object>>> memberTransactions(@PathVariable Long id,
                                                                           @RequestParam(required = false) String type,
                                                                           @RequestParam(defaultValue = "1") int pageNo,
                                                                           @RequestParam(defaultValue = "20") int pageSize,
                                                                           HttpServletRequest request) {
        Map<String, Object> store = mainFlowService.requireMerchant(request);
        return RestResponse.success(mainFlowService.memberTransactions(longValue(store.get("id")), id, type, pageNo, pageSize));
    }

    @PostMapping("/transactions/recharge")
    public RestResponse<Map<String, Object>> recharge(@RequestBody Map<String, Object> body,
                                                      HttpServletRequest request) {
        Map<String, Object> store = mainFlowService.requireMerchant(request);
        return RestResponse.success(mainFlowService.recharge(store, body));
    }

    @PostMapping("/transactions/consume")
    public RestResponse<Map<String, Object>> consume(@RequestBody Map<String, Object> body,
                                                     HttpServletRequest request) {
        Map<String, Object> store = mainFlowService.requireMerchant(request);
        return RestResponse.success(mainFlowService.consume(store, body));
    }

    @GetMapping("/transactions/recent")
    public RestResponse<List<Map<String, Object>>> recentTransactions(@RequestParam(required = false) String type,
                                                                      @RequestParam(defaultValue = "5") int limit,
                                                                      HttpServletRequest request) {
        Map<String, Object> store = mainFlowService.requireMerchant(request);
        return RestResponse.success(mainFlowService.recentTransactions(longValue(store.get("id")), type, limit));
    }

    @PostMapping("/transactions/{id}/reverse")
    public RestResponse<Map<String, Object>> reverse(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> store = mainFlowService.requireMerchant(request);
        Long storeId = longValue(store.get("id"));
        return RestResponse.success(mainFlowService.reverse(storeId, id, "MERCHANT", storeId));
    }

    @PostMapping("/transactions/{id}/refund")
    public RestResponse<Map<String, Object>> refund(@PathVariable Long id,
                                                    @RequestBody Map<String, Object> body,
                                                    HttpServletRequest request) {
        Map<String, Object> store = mainFlowService.requireMerchant(request);
        Long storeId = longValue(store.get("id"));
        return RestResponse.success(mainFlowService.refund(storeId, id, amount(body.get("amount")), "MERCHANT", storeId));
    }

    private BigDecimal amount(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }

    private Long longValue(Object value) {
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }
}
