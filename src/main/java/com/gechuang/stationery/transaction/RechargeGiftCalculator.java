package com.gechuang.stationery.transaction;

import com.gechuang.stationery.recharge.RechargeTierRule;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public final class RechargeGiftCalculator {

    private RechargeGiftCalculator() {
    }

    public static BigDecimal calculate(BigDecimal amount, List<RechargeTierRule> tiers) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || tiers == null || tiers.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        return tiers.stream()
                .filter(tier -> tier.rechargeAmount().compareTo(amount) <= 0)
                .max(Comparator.comparing(RechargeTierRule::rechargeAmount))
                .map(RechargeTierRule::giftAmount)
                .orElse(BigDecimal.ZERO)
                .setScale(2);
    }
}
