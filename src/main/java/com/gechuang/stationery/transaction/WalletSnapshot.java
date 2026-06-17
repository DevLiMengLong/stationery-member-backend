package com.gechuang.stationery.transaction;

import java.math.BigDecimal;

public record WalletSnapshot(BigDecimal rechargeBalance, BigDecimal giftBalance) {

    public BigDecimal totalBalance() {
        return rechargeBalance.add(giftBalance).setScale(2);
    }
}
