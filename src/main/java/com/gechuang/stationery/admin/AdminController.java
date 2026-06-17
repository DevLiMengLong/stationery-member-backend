package com.gechuang.stationery.admin;

import com.gechuang.stationery.common.BusinessException;
import com.gechuang.stationery.common.ErrorCode;
import com.gechuang.stationery.common.PageResult;
import com.gechuang.stationery.common.RestResponse;
import com.gechuang.stationery.mainflow.MainFlowService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final MainFlowService mainFlowService;

    public AdminController(MainFlowService mainFlowService) {
        this.mainFlowService = mainFlowService;
    }

    @GetMapping("/overview")
    public RestResponse<Map<String, Object>> overview(HttpServletRequest request) {
        mainFlowService.requireAdmin(request);
        return RestResponse.success(mainFlowService.adminOverview());
    }

    @GetMapping("/stores")
    public RestResponse<PageResult<Map<String, Object>>> stores(@RequestParam(required = false) String keyword,
                                                                @RequestParam(required = false) String status,
                                                                @RequestParam(defaultValue = "1") int pageNo,
                                                                @RequestParam(defaultValue = "20") int pageSize,
                                                                HttpServletRequest request) {
        mainFlowService.requireAdmin(request);
        return RestResponse.success(mainFlowService.stores(keyword, status, pageNo, pageSize));
    }

    @PostMapping("/stores")
    public RestResponse<Map<String, Object>> createStore(@RequestBody Map<String, Object> body,
                                                         HttpServletRequest request) {
        mainFlowService.requireAdmin(request);
        return RestResponse.success(mainFlowService.createStore(body));
    }

    @PutMapping("/stores/{id}")
    public RestResponse<Map<String, Object>> updateStore(@PathVariable Long id,
                                                         @RequestBody Map<String, Object> body,
                                                         HttpServletRequest request) {
        mainFlowService.requireAdmin(request);
        return RestResponse.success(mainFlowService.updateStore(id, body));
    }

    @PatchMapping("/stores/{id}/status")
    public RestResponse<Map<String, Object>> updateStoreStatus(@PathVariable Long id,
                                                               @RequestBody Map<String, Object> body,
                                                               HttpServletRequest request) {
        mainFlowService.requireAdmin(request);
        return RestResponse.success(mainFlowService.updateStoreStatus(id, text(body.get("status"))));
    }

    @PostMapping("/stores/{id}/reset-password")
    public RestResponse<Void> resetStorePassword(@PathVariable Long id,
                                                 @RequestBody Map<String, Object> body,
                                                 HttpServletRequest request) {
        mainFlowService.requireAdmin(request);
        mainFlowService.resetStorePassword(id, textOrDefault(body.get("newPassword"), "123456"));
        return RestResponse.success();
    }

    @GetMapping("/members")
    public RestResponse<PageResult<Map<String, Object>>> members(@RequestParam(required = false) String mobile,
                                                                 @RequestParam(required = false) String name,
                                                                 @RequestParam(required = false) Long storeId,
                                                                 @RequestParam(required = false) BigDecimal rechargeMin,
                                                                 @RequestParam(required = false) BigDecimal rechargeMax,
                                                                 @RequestParam(required = false) BigDecimal consumptionMin,
                                                                 @RequestParam(required = false) BigDecimal consumptionMax,
                                                                 @RequestParam(defaultValue = "1") int pageNo,
                                                                 @RequestParam(defaultValue = "20") int pageSize,
                                                                 HttpServletRequest request) {
        mainFlowService.requireAdmin(request);
        return RestResponse.success(mainFlowService.adminMembers(mobile, name, storeId, rechargeMin, rechargeMax,
                consumptionMin, consumptionMax, pageNo, pageSize));
    }

    @GetMapping("/members/export")
    public ResponseEntity<byte[]> exportMembers(@RequestParam(required = false) String mobile,
                                                @RequestParam(required = false) String name,
                                                @RequestParam(required = false) Long storeId,
                                                @RequestParam(required = false) BigDecimal rechargeMin,
                                                @RequestParam(required = false) BigDecimal rechargeMax,
                                                @RequestParam(required = false) BigDecimal consumptionMin,
                                                @RequestParam(required = false) BigDecimal consumptionMax,
                                                HttpServletRequest request) {
        mainFlowService.requireAdmin(request);
        List<Map<String, Object>> rows = mainFlowService.adminMembersForExport(mobile, name, storeId,
                rechargeMin, rechargeMax, consumptionMin, consumptionMax);
        if (rows.size() > 5000) {
            throw new BusinessException(ErrorCode.BIZ_409, "导出数据超过 5000 条，请缩小筛选范围");
        }
        byte[] bytes = csv(rows).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("members.csv", StandardCharsets.UTF_8).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(bytes);
    }

    @GetMapping("/members/{id}")
    public RestResponse<Map<String, Object>> memberDetail(@PathVariable Long id, HttpServletRequest request) {
        mainFlowService.requireAdmin(request);
        return RestResponse.success(mainFlowService.memberDetail(null, id));
    }

    @PutMapping("/members/{id}")
    public RestResponse<Map<String, Object>> updateMember(@PathVariable Long id,
                                                          @RequestBody Map<String, Object> body,
                                                          HttpServletRequest request) {
        mainFlowService.requireAdmin(request);
        return RestResponse.success(mainFlowService.updateMember(null, id, body));
    }

    @DeleteMapping("/members/{id}")
    public RestResponse<Void> deleteMember(@PathVariable Long id, HttpServletRequest request) {
        mainFlowService.requireAdmin(request);
        mainFlowService.softDeleteMember(null, id);
        return RestResponse.success();
    }

    @GetMapping("/members/{id}/transactions")
    public RestResponse<PageResult<Map<String, Object>>> memberTransactions(@PathVariable Long id,
                                                                           @RequestParam(required = false) String type,
                                                                           @RequestParam(defaultValue = "1") int pageNo,
                                                                           @RequestParam(defaultValue = "20") int pageSize,
                                                                           HttpServletRequest request) {
        mainFlowService.requireAdmin(request);
        return RestResponse.success(mainFlowService.memberTransactions(null, id, type, pageNo, pageSize));
    }

    @PostMapping("/transactions/{id}/reverse")
    public RestResponse<Map<String, Object>> reverse(@PathVariable Long id,
                                                     @RequestBody(required = false) Map<String, Object> body,
                                                     HttpServletRequest request) {
        Map<String, Object> admin = mainFlowService.requireAdmin(request);
        requireReason(body);
        return RestResponse.success(mainFlowService.reverse(null, id, "ADMIN", longValue(admin.get("id"))));
    }

    @PostMapping("/transactions/{id}/refund")
    public RestResponse<Map<String, Object>> refund(@PathVariable Long id,
                                                    @RequestBody Map<String, Object> body,
                                                    HttpServletRequest request) {
        Map<String, Object> admin = mainFlowService.requireAdmin(request);
        requireReason(body);
        return RestResponse.success(mainFlowService.refund(longValue(body.get("storeId")), id, amount(body.get("amount")),
                "ADMIN", longValue(admin.get("id"))));
    }

    @PostMapping("/members/{id}/corrections")
    public RestResponse<Map<String, Object>> correction(@PathVariable Long id,
                                                        @RequestBody Map<String, Object> body,
                                                        HttpServletRequest request) {
        Map<String, Object> admin = mainFlowService.requireAdmin(request);
        requireReason(body);
        return RestResponse.success(mainFlowService.correction(id, text(body.get("correctionType")),
                amount(body.get("amount")), text(body.get("reason")), longValue(admin.get("id"))));
    }

    @GetMapping("/activity")
    public RestResponse<List<Map<String, Object>>> activity(HttpServletRequest request) {
        mainFlowService.requireAdmin(request);
        return RestResponse.success(mainFlowService.activityRows());
    }

    private void requireReason(Map<String, Object> body) {
        if (body == null || !org.springframework.util.StringUtils.hasText(text(body.get("reason")))) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "请填写操作原因");
        }
    }

    private String csv(List<Map<String, Object>> rows) {
        StringBuilder builder = new StringBuilder("\uFEFF手机号,姓名,所属门店,待消费金额,累计充值,累计消费,加入时间\n");
        for (Map<String, Object> row : rows) {
            builder.append(csvCell(row.get("mobile"))).append(',')
                    .append(csvCell(row.get("name"))).append(',')
                    .append(csvCell(row.get("storeName"))).append(',')
                    .append(csvCell(row.get("totalBalance"))).append(',')
                    .append(csvCell(row.get("accumulatedRecharge"))).append(',')
                    .append(csvCell(row.get("accumulatedConsumption"))).append(',')
                    .append(csvCell(row.get("joinedAt"))).append('\n');
        }
        return builder.toString();
    }

    private String csvCell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private BigDecimal amount(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }

    private Long longValue(Object value) {
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private String textOrDefault(Object value, String fallback) {
        String result = text(value);
        return org.springframework.util.StringUtils.hasText(result) ? result : fallback;
    }
}
