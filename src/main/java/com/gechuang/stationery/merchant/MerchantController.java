package com.gechuang.stationery.merchant;

import com.gechuang.stationery.common.PageResult;
import com.gechuang.stationery.common.RestResponse;
import com.gechuang.stationery.demo.DemoDataStore;
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

    private final DemoDataStore dataStore;

    public MerchantController(DemoDataStore dataStore) {
        this.dataStore = dataStore;
    }

    @GetMapping("/dashboard")
    public RestResponse<Map<String, Object>> dashboard(HttpServletRequest request) {
        DemoDataStore.StoreAccount store = dataStore.requireMerchant(request);
        return RestResponse.success(dataStore.merchantDashboard(store));
    }

    @GetMapping("/profile")
    public RestResponse<Map<String, Object>> profile(HttpServletRequest request) {
        return RestResponse.success(dataStore.currentStoreMap(dataStore.requireMerchant(request)));
    }

    @PutMapping("/profile")
    public RestResponse<Map<String, Object>> updateProfile(@RequestBody Map<String, Object> body,
                                                           HttpServletRequest request) {
        DemoDataStore.StoreAccount store = dataStore.requireMerchant(request);
        return RestResponse.success(dataStore.updateMerchantProfile(store, body));
    }

    @PutMapping("/password")
    public RestResponse<Void> updatePassword(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        dataStore.updateMerchantPassword(dataStore.requireMerchant(request), body);
        return RestResponse.success();
    }

    @GetMapping("/recharge-tiers")
    public RestResponse<List<Map<String, Object>>> rechargeTiers(HttpServletRequest request) {
        DemoDataStore.StoreAccount store = dataStore.requireMerchant(request);
        return RestResponse.success(dataStore.getRechargeTiers(store.getId()));
    }

    @PutMapping("/recharge-tiers/{tierNo}")
    public RestResponse<Map<String, Object>> updateRechargeTier(@PathVariable int tierNo,
                                                                @RequestBody Map<String, Object> body,
                                                                HttpServletRequest request) {
        DemoDataStore.StoreAccount store = dataStore.requireMerchant(request);
        return RestResponse.success(dataStore.updateRechargeTier(store, tierNo, body));
    }

    @GetMapping("/campaigns")
    public RestResponse<List<Map<String, Object>>> campaigns(HttpServletRequest request) {
        DemoDataStore.StoreAccount store = dataStore.requireMerchant(request);
        return RestResponse.success(dataStore.campaigns(store.getId()));
    }

    @GetMapping("/members")
    public RestResponse<PageResult<Map<String, Object>>> members(@RequestParam(required = false) String keyword,
                                                                 @RequestParam(defaultValue = "1") int pageNo,
                                                                 @RequestParam(defaultValue = "20") int pageSize,
                                                                 HttpServletRequest request) {
        DemoDataStore.StoreAccount store = dataStore.requireMerchant(request);
        return RestResponse.success(dataStore.merchantMembers(store, keyword, pageNo, pageSize));
    }

    @GetMapping("/members/lookup")
    public RestResponse<Map<String, Object>> lookupMember(@RequestParam String keyword, HttpServletRequest request) {
        DemoDataStore.StoreAccount store = dataStore.requireMerchant(request);
        return RestResponse.success(dataStore.lookupMember(store, keyword));
    }

    @PostMapping("/members")
    public RestResponse<Map<String, Object>> createMember(@RequestBody Map<String, Object> body,
                                                          HttpServletRequest request) {
        DemoDataStore.StoreAccount store = dataStore.requireMerchant(request);
        return RestResponse.success(dataStore.createMember(store, body));
    }

    @GetMapping("/members/{id}")
    public RestResponse<Map<String, Object>> memberDetail(@PathVariable Long id, HttpServletRequest request) {
        DemoDataStore.StoreAccount store = dataStore.requireMerchant(request);
        return RestResponse.success(dataStore.memberDetail(store.getId(), id, false));
    }

    @PutMapping("/members/{id}")
    public RestResponse<Map<String, Object>> updateMember(@PathVariable Long id,
                                                          @RequestBody Map<String, Object> body,
                                                          HttpServletRequest request) {
        DemoDataStore.StoreAccount store = dataStore.requireMerchant(request);
        return RestResponse.success(dataStore.updateMember(store.getId(), id, body, false));
    }

    @DeleteMapping("/members/{id}")
    public RestResponse<Void> deleteMember(@PathVariable Long id, HttpServletRequest request) {
        DemoDataStore.StoreAccount store = dataStore.requireMerchant(request);
        dataStore.softDeleteMember(store.getId(), id, false);
        return RestResponse.success();
    }

    @GetMapping("/members/{id}/transactions")
    public RestResponse<PageResult<Map<String, Object>>> memberTransactions(@PathVariable Long id,
                                                                           @RequestParam(required = false) String type,
                                                                           @RequestParam(defaultValue = "1") int pageNo,
                                                                           @RequestParam(defaultValue = "20") int pageSize,
                                                                           HttpServletRequest request) {
        DemoDataStore.StoreAccount store = dataStore.requireMerchant(request);
        return RestResponse.success(dataStore.memberTransactions(store.getId(), id, type, pageNo, pageSize));
    }

    @PostMapping("/transactions/recharge")
    public RestResponse<Map<String, Object>> recharge(@RequestBody Map<String, Object> body,
                                                      HttpServletRequest request) {
        DemoDataStore.StoreAccount store = dataStore.requireMerchant(request);
        return RestResponse.success(dataStore.recharge(store, body));
    }

    @PostMapping("/transactions/consume")
    public RestResponse<Map<String, Object>> consume(@RequestBody Map<String, Object> body,
                                                     HttpServletRequest request) {
        DemoDataStore.StoreAccount store = dataStore.requireMerchant(request);
        return RestResponse.success(dataStore.consume(store, body));
    }

    @GetMapping("/transactions/recent")
    public RestResponse<List<Map<String, Object>>> recentTransactions(@RequestParam(required = false) String type,
                                                                      @RequestParam(defaultValue = "5") int limit,
                                                                      HttpServletRequest request) {
        DemoDataStore.StoreAccount store = dataStore.requireMerchant(request);
        return RestResponse.success(dataStore.recentTransactions(store, type, limit));
    }

    @PostMapping("/transactions/{id}/reverse")
    public RestResponse<Map<String, Object>> reverse(@PathVariable Long id, HttpServletRequest request) {
        DemoDataStore.StoreAccount store = dataStore.requireMerchant(request);
        return RestResponse.success(dataStore.reverse(store.getId(), id, false, "MERCHANT", store.getId()));
    }

    @PostMapping("/transactions/{id}/refund")
    public RestResponse<Map<String, Object>> refund(@PathVariable Long id,
                                                    @RequestBody Map<String, Object> body,
                                                    HttpServletRequest request) {
        DemoDataStore.StoreAccount store = dataStore.requireMerchant(request);
        return RestResponse.success(dataStore.refund(store.getId(), id, amount(body.get("amount")), "MERCHANT", store.getId()));
    }

    private BigDecimal amount(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }
}
