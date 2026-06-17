package com.gechuang.stationery.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gechuang.stationery.recharge.RechargeTierRule;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransactionRuleTest {

    @Test
    void giftAmountShouldMatchHighestTierNotGreaterThanRechargeAmount() {
        List<RechargeTierRule> tiers = List.of(
                new RechargeTierRule(1, new BigDecimal("100.00"), new BigDecimal("10.00")),
                new RechargeTierRule(2, new BigDecimal("200.00"), new BigDecimal("20.00")),
                new RechargeTierRule(3, new BigDecimal("300.00"), new BigDecimal("35.00")),
                new RechargeTierRule(4, new BigDecimal("500.00"), new BigDecimal("100.00"))
        );

        assertThat(RechargeGiftCalculator.calculate(new BigDecimal("250.00"), tiers))
                .isEqualByComparingTo("20.00");
        assertThat(RechargeGiftCalculator.calculate(new BigDecimal("80.00"), tiers))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void consumptionShouldDeductRechargeBalanceBeforeGiftBalance() {
        WalletSnapshot before = new WalletSnapshot(new BigDecimal("100.00"), new BigDecimal("10.00"));

        WalletSnapshot after = WalletCalculator.consume(before, new BigDecimal("30.00"));

        assertThat(after.rechargeBalance()).isEqualByComparingTo("70.00");
        assertThat(after.giftBalance()).isEqualByComparingTo("10.00");
        assertThat(after.totalBalance()).isEqualByComparingTo("80.00");
    }

    @Test
    void deductionShouldDeductRechargeBalanceBeforeGiftBalance() {
        WalletSnapshot before = new WalletSnapshot(new BigDecimal("20.00"), new BigDecimal("30.00"));

        WalletSnapshot after = WalletCalculator.deduct(before, new BigDecimal("25.00"));

        assertThat(after.rechargeBalance()).isEqualByComparingTo("0.00");
        assertThat(after.giftBalance()).isEqualByComparingTo("25.00");
        assertThat(after.totalBalance()).isEqualByComparingTo("25.00");
    }

    @Test
    void consumptionShouldRejectAmountGreaterThanAvailableBalance() {
        WalletSnapshot before = new WalletSnapshot(BigDecimal.ZERO, BigDecimal.ZERO);

        assertThatThrownBy(() -> WalletCalculator.consume(before, new BigDecimal("1.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient balance");
    }
}
