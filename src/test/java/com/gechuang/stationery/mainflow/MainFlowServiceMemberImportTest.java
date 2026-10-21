package com.gechuang.stationery.mainflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gechuang.stationery.common.BusinessException;
import com.gechuang.stationery.notification.MemberNotificationService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MainFlowServiceMemberImportTest {

    @Test
    void importMembersShouldAssignOpeningBalancesToTheMatchingMembers() {
        MainFlowMapper mapper = mock(MainFlowMapper.class);
        MainFlowService service = new MainFlowService(mapper, mock(MemberNotificationService.class));
        Long storeId = 7L;
        when(mapper.selectStoreById(storeId)).thenReturn(Map.of("id", storeId));
        when(mapper.selectMemberIdsByMobiles(storeId, List.of("13800000001", "13800000002")))
                .thenReturn(List.of(
                        Map.of("id", 102L, "mobile", "13800000002"),
                        Map.of("id", 101L, "mobile", "13800000001")));
        when(mapper.insertWallets(anyList())).thenReturn(2);

        String csv = "手机号,姓名,性别,年龄,总余额,充值余额,赠送余额\n"
                + "13800000001,张一,男,20,100.25,80.20,20.05\n"
                + "13800000002,张二,女,21,,,\n";

        MemberImportResult result = service.importAdminMembers(storeId, csv.getBytes(StandardCharsets.UTF_8));

        assertEquals(2, result.importedCount());
        assertEquals(0, result.failedCount());
        verify(mapper).insertWallets(org.mockito.ArgumentMatchers.argThat(wallets ->
                wallets.size() == 2
                        && hasWallet(wallets, 101L, "80.20", "20.05")
                        && hasWallet(wallets, 102L, "0.00", "0.00")));
    }

    @Test
    void importMembersShouldRejectInvalidOrInconsistentAmountsPerRow() {
        MainFlowMapper mapper = mock(MainFlowMapper.class);
        MainFlowService service = new MainFlowService(mapper, mock(MemberNotificationService.class));
        when(mapper.selectStoreById(7L)).thenReturn(Map.of("id", 7L));
        String csv = "手机号,姓名,性别,年龄,总余额,充值余额,赠送余额\n"
                + "13800000001,张一,男,20,100.00,70.00,20.00\n"
                + "13800000002,张二,女,21,10.00,-1.00,11.00\n"
                + "13800000003,张三,男,22,0.001,0,0\n"
                + "13800000004,张四,女,23,10000000000.00,0,0\n";

        MemberImportResult result = service.importAdminMembers(7L, csv.getBytes(StandardCharsets.UTF_8));

        assertEquals(0, result.importedCount());
        assertEquals(4, result.failedCount());
        assertEquals(List.of(2, 3, 4, 5), result.errors().stream().map(MemberImportResult.RowError::rowNumber).toList());
        assertTrue(result.errors().get(0).message().contains("总余额"));
        assertTrue(result.errors().get(1).message().contains("充值余额"));
        verify(mapper, org.mockito.Mockito.never()).insertWallets(anyList());
    }

    private static boolean hasWallet(List<?> wallets, Long memberId, String rechargeBalance, String giftBalance) {
        return wallets.stream().anyMatch(value -> value instanceof Map<?, ?> wallet
                && memberId.equals(wallet.get("memberId"))
                && new BigDecimal(rechargeBalance).equals(wallet.get("rechargeBalance"))
                && new BigDecimal(giftBalance).equals(wallet.get("giftBalance")));
    }

    @Test
    void importMembersShouldCreateValidRowsAndReportInvalidOrDuplicateRowsWithoutNotifyingMembers() {
        MainFlowMapper mapper = mock(MainFlowMapper.class);
        MemberNotificationService notificationService = mock(MemberNotificationService.class);
        MainFlowService service = new MainFlowService(mapper, notificationService);
        Long storeId = 7L;
        when(mapper.selectStoreById(storeId)).thenReturn(Map.of("id", storeId));
        when(mapper.selectVisibleMemberMobiles(storeId, List.of(
                "13800000001", "13800000002", "13800000003")))
                .thenReturn(List.of("13800000003"));
        when(mapper.selectMemberIdsByMobiles(storeId, List.of("13800000001", "13800000002")))
                .thenReturn(List.of(
                        Map.of("id", 101L, "mobile", "13800000001"),
                        Map.of("id", 102L, "mobile", "13800000002")));
        when(mapper.insertWallets(anyList())).thenReturn(2);

        String csv = "\uFEFF手机号,姓名,性别,年龄\r\n"
                + "13800000001,\"王,\"\"小明\"\"\",男,18\r\n"
                + "13800000002,李小红,FEMALE,21\r\n"
                + "not-a-mobile,无效手机号,女,23\r\n"
                + "13800000003,已有会员,女,20\r\n"
                + "13800000001,文件内重复,男,22\r\n";

        MemberImportResult result = service.importAdminMembers(storeId, csv.getBytes(StandardCharsets.UTF_8));

        assertEquals(5, result.totalRows());
        assertEquals(2, result.importedCount());
        assertEquals(3, result.failedCount());
        assertEquals(4, result.errors().get(0).rowNumber());
        assertEquals("手机号格式不正确", result.errors().get(0).message());
        assertEquals(5, result.errors().get(1).rowNumber());
        assertEquals("手机号已存在", result.errors().get(1).message());
        assertEquals(6, result.errors().get(2).rowNumber());
        assertFalse(result.errorsTruncated());
        verify(mapper).insertImportedMembers(org.mockito.ArgumentMatchers.argThat(rows ->
                rows.size() == 2
                        && "王,\"小明\"".equals(rows.get(0).get("name"))
                        && "MALE".equals(rows.get(0).get("gender"))
                        && "李小红".equals(rows.get(1).get("name"))
                        && "FEMALE".equals(rows.get(1).get("gender"))));
        verify(mapper).insertWallets(org.mockito.ArgumentMatchers.argThat(wallets ->
                wallets.size() == 2
                        && hasWallet(wallets, 101L, "0.00", "0.00")
                        && hasWallet(wallets, 102L, "0.00", "0.00")));
        verifyNoInteractions(notificationService);
    }

    @Test
    void importMembersShouldCapReturnedRowErrorsAndSkipEmptyMobileLookup() {
        MainFlowMapper mapper = mock(MainFlowMapper.class);
        MainFlowService service = new MainFlowService(mapper, mock(MemberNotificationService.class));
        Long storeId = 7L;
        when(mapper.selectStoreById(storeId)).thenReturn(Map.of("id", storeId));
        StringBuilder csv = new StringBuilder("手机号,姓名,性别,年龄\n");
        for (int row = 0; row < 101; row++) {
            csv.append("bad-mobile,会员,女,20\n");
        }

        MemberImportResult result = service.importAdminMembers(storeId, csv.toString().getBytes(StandardCharsets.UTF_8));

        assertEquals(101, result.totalRows());
        assertEquals(0, result.importedCount());
        assertEquals(101, result.failedCount());
        assertEquals(100, result.errors().size());
        assertEquals(101, result.errors().get(99).rowNumber());
        assertTrue(result.errorsTruncated());
        verify(mapper, org.mockito.Mockito.never()).selectVisibleMemberMobiles(org.mockito.ArgumentMatchers.any(), anyList());
    }

    @Test
    void importMembersShouldRejectFilesWithUnexpectedHeaders() {
        MainFlowMapper mapper = mock(MainFlowMapper.class);
        MainFlowService service = new MainFlowService(mapper, mock(MemberNotificationService.class));
        when(mapper.selectStoreById(7L)).thenReturn(Map.of("id", 7L));

        assertThrows(BusinessException.class, () -> service.importAdminMembers(7L,
                "手机号,昵称,性别,年龄\n".getBytes(StandardCharsets.UTF_8)));
    }
}
