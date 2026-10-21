package com.gechuang.stationery.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gechuang.stationery.mainflow.MainFlowService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdminControllerMemberCsvTest {

    @Test
    void importTemplateShouldExposeOpeningBalanceColumns() {
        AdminController controller = new AdminController(mock(MainFlowService.class));

        byte[] body = controller.memberImportTemplate(mock(HttpServletRequest.class)).getBody();

        assertEquals("\uFEFF手机号,姓名,性别,年龄,总余额,充值余额,赠送余额\r\n",
                new String(body, StandardCharsets.UTF_8));
    }

    @Test
    void exportShouldUseCurrentBalancesForRechargeAndGiftAmounts() {
        MainFlowService service = mock(MainFlowService.class);
        AdminController controller = new AdminController(service);
        when(service.adminMembersForExport(null, null, null, null, null, null, null)).thenReturn(List.of(Map.of(
                "mobile", "13800000001",
                "name", "张一",
                "storeName", "测试门店",
                "totalBalance", "130.00",
                "rechargeBalance", "100.00",
                "giftBalance", "30.00",
                "accumulatedRecharge", "500.00",
                "accumulatedConsumption", "370.00",
                "joinedAt", "2026-10-21 10:00:00")));

        byte[] body = controller.exportMembers(null, null, null, null, null, null, null,
                mock(HttpServletRequest.class)).getBody();

        assertEquals("\uFEFF手机号,姓名,所属门店,待消费金额,充值金额,赠送金额,累计充值,累计消费,加入时间\n"
                        + "13800000001,张一,测试门店,130.00,100.00,30.00,500.00,370.00,2026-10-21 10:00:00\n",
                new String(body, StandardCharsets.UTF_8));
    }
}
