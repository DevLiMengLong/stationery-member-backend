package com.gechuang.stationery.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gechuang.stationery.mainflow.MainFlowMapper;
import org.junit.jupiter.api.Test;

class SmsQuotaServiceTest {

    private final MainFlowMapper mapper = mock(MainFlowMapper.class);
    private final SmsQuotaService service = new SmsQuotaService(mapper);

    @Test
    void reserveShouldSucceedOnlyWhenOneSmsWasAtomicallyRemoved() {
        when(mapper.tryConsumeSmsQuota(5L)).thenReturn(1);
        when(mapper.tryConsumeSmsQuota(6L)).thenReturn(0);

        assertThat(service.reserve(5L)).isTrue();
        assertThat(service.reserve(6L)).isFalse();
    }

    @Test
    void releaseShouldRestoreOneReservedSmsForTheSameStore() {
        service.release(5L);

        verify(mapper).restoreSmsQuota(5L);
    }
}
