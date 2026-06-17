package com.gechuang.stationery.transaction;

import java.math.BigDecimal;

public final class WalletCalculator {

    private WalletCalculator() {
    }

    public static WalletSnapshot consume(WalletSnapshot before, BigDecimal amount) {
        return deduct(before, amount);
    }

    public static WalletSnapshot deduct(WalletSnapshot before, BigDecimal amount) {
        if (before == null) {
            throw new IllegalArgumentException("Wallet does not exist");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (before.totalBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }

        BigDecimal rechargeDeduction = before.rechargeBalance().min(amount);
        BigDecimal remaining = amount.subtract(rechargeDeduction);
        BigDecimal giftDeduction = before.giftBalance().min(remaining);
        return new WalletSnapshot(
                before.rechargeBalance().subtract(rechargeDeduction).setScale(2),
                before.giftBalance().subtract(giftDeduction).setScale(2)
        );
    }
}
