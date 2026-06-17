package com.gechuang.stationery.recharge;

import java.math.BigDecimal;

public record RechargeTierRule(Integer tierNo, BigDecimal rechargeAmount, BigDecimal giftAmount) {
}
