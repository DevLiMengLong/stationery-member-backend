package com.gechuang.stationery.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MiniprogramPaymentSchemaTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/20260921_01_miniprogram_payment.sql");
    private static final Path FRESH_SCHEMA = Path.of("src/main/resources/db/init/01-schema.sql");

    @Test
    void incrementalMigrationDefinesSharedMiniprogramAndPaymentOwnershipConstraints() throws Exception {
        assertThat(MIGRATION).exists();

        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS store_wechat_binding")
                .contains("CREATE TABLE IF NOT EXISTS store_wechat_pay_config")
                .contains("CREATE TABLE IF NOT EXISTS wechat_payment_order")
                .contains("UNIQUE KEY uk_store_wechat_binding_openid (openid)")
                .contains("UNIQUE KEY uk_store_wechat_pay_config_store (store_id)")
                .contains("UNIQUE KEY uk_wechat_payment_order_out_trade_no (out_trade_no)")
                .contains("UNIQUE KEY uk_wechat_payment_order_wallet_transaction (wallet_transaction_id)")
                .contains("UNIQUE KEY uk_wechat_payment_order_idempotency (idempotency_key)")
                .contains("recharge_amount DECIMAL(12,2) NOT NULL")
                .contains("gift_amount DECIMAL(12,2) NOT NULL")
                .contains("CONSTRAINT fk_wechat_payment_order_store")
                .contains("CONSTRAINT fk_wechat_payment_order_member")
                .contains("CONSTRAINT fk_wechat_payment_order_wallet_transaction");
    }

    @Test
    void freshInstallSchemaContainsTheSamePaymentTables() throws Exception {
        String sql = Files.readString(FRESH_SCHEMA);

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS store_wechat_binding")
                .contains("CREATE TABLE IF NOT EXISTS store_wechat_pay_config")
                .contains("CREATE TABLE IF NOT EXISTS wechat_payment_order");
    }
}
