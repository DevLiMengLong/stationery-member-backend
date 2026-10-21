package com.gechuang.stationery.mainflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gechuang.stationery.common.BusinessException;
import com.gechuang.stationery.common.ErrorCode;
import com.gechuang.stationery.common.PageResult;
import com.gechuang.stationery.notification.MemberNotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdminTransactionQueryTest {

    private final MainFlowMapper mapper = mock(MainFlowMapper.class);
    private final MainFlowService service = new MainFlowService(mapper, mock(MemberNotificationService.class));

    @Test
    void filtersOriginalConsumptionByMemberStoreInclusiveDaysAndSingleAmount() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 10, 23, 59, 59);
        BigDecimal min = new BigDecimal("10.00");
        BigDecimal max = new BigDecimal("80.00");
        Map<String, Object> transaction = Map.of("serialNo", "CP001", "amount", "50.00");
        when(mapper.countAdminTransactions("CONSUMPTION", "138", "李", 5L, start, end, min, max))
                .thenReturn(1L);
        when(mapper.selectAdminTransactions("CONSUMPTION", "138", "李", 5L, start, end, min, max, 0, 20))
                .thenReturn(List.of(transaction));

        PageResult<Map<String, Object>> page = service.adminTransactions("CONSUMPTION", " 138 ", " 李 ", 5L,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10), min, max, 1, 20);

        assertThat(page.getRecords()).containsExactly(transaction);
        assertThat(page.getTotal()).isEqualTo(1);
        verify(mapper).countAdminTransactions("CONSUMPTION", "138", "李", 5L, start, end, min, max);
        verify(mapper).selectAdminTransactions("CONSUMPTION", "138", "李", 5L, start, end, min, max, 0, 20);
    }

    @Test
    void rejectsNonOriginalTransactionTypesBeforeQuerying() {
        assertThatThrownBy(() -> service.adminTransactions("CONSUMPTION_REFUND", null, null, null,
                null, null, null, null, 1, 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_422));
        verifyNoInteractions(mapper);
    }

    @Test
    void rejectsReversedDateAndAmountRanges() {
        assertThatThrownBy(() -> service.adminTransactions("RECHARGE", null, null, null,
                LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 10), null, null, 1, 20))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.adminTransactions("RECHARGE", null, null, null,
                null, null, new BigDecimal("10.01"), new BigDecimal("10.00"), 1, 20))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.adminTransactions("RECHARGE", null, null, null,
                null, null, new BigDecimal("-0.01"), null, 1, 20))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(mapper);
    }
}
