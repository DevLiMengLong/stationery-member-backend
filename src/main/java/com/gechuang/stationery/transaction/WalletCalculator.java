package com.gechuang.stationery.transaction;

import java.math.BigDecimal;

public final class WalletCalculator {

    private WalletCalculator() {
    }

    public static WalletSnapshot consume(WalletSnapshot before, BigDecimal amount) {
        if (before == null) {
            throw new IllegalArgumentException("Wallet does not exist");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (before.totalBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }

        BigDecimal giftDeduction = before.giftBalance().min(amount);
        BigDecimal remaining = amount.subtract(giftDeduction);
        BigDecimal rechargeDeduction = before.rechargeBalance().min(remaining);
        return new WalletSnapshot(
                before.rechargeBalance().subtract(rechargeDeduction).setScale(2),
                before.giftBalance().subtract(giftDeduction).setScale(2)
        );
    }
}
