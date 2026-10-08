package com.gechuang.stationery.mainflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class MerchantLoginMapperXmlTest {

    @Test
    void merchantPhoneQueriesAndBindingsBuildSqlForManyToManyPhoneAssociations() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/mainflow/MainFlowMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        String namespace = MainFlowMapper.class.getName() + ".";
        Map<String, Object> params = Map.of(
                "mobile", "13800138000",
                "storeId", 17L,
                "now", LocalDateTime.now());

        String lockSql = configuration.getMappedStatement(namespace + "lockStoreLoginMobile")
                .getBoundSql(Map.of("mobile", "13800138000")).getSql();
        String storesSql = configuration.getMappedStatement(namespace + "selectStoresByLoginMobile")
                .getBoundSql(Map.of("mobile", "13800138000", "now", LocalDateTime.now())).getSql();
        String passwordStoresSql = configuration.getMappedStatement(namespace + "selectStoresByLoginMobileAndPassword")
                .getBoundSql(Map.of("mobile", "13800138000", "passwordHash", "hashed-password",
                        "now", LocalDateTime.now())).getSql();
        String selectedStoreSql = configuration.getMappedStatement(namespace + "selectStoreByLoginMobile")
                .getBoundSql(params).getSql();
        String bindingInsertSql = configuration.getMappedStatement(namespace + "insertStoreLoginMobiles")
                .getBoundSql(Map.of("storeId", 17L, "mobiles", List.of("13800138000", "13900139000")))
                .getSql();

        assertThat(lockSql.replaceAll("\\s+", "")).contains("WHEREmobile=?ORDERBYstore_idLIMIT1FORUPDATE");
        assertThat(storesSql).contains("CAST(sa.id AS CHAR)", "sa.status = 'ACTIVE'", "sa.expires_at");
        assertThat(passwordStoresSql).contains("slm.mobile = ?", "sa.password_hash = ?",
                "sa.status = 'ACTIVE'", "sa.expires_at");
        assertThat(selectedStoreSql).contains("slm.mobile = ?", "sa.id = ?", "sa.status = 'ACTIVE'");
        assertThat(bindingInsertSql.replaceAll("\\s+", "")).contains("(?,?),(?,?)");
    }

    @Test
    void bindingSchemaAllowsOnePhoneAcrossStoresAndSeveralPhonesPerStore() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("db/init/01-schema.sql")) {
            String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(schema).contains("UNIQUE KEY uk_store_login_mobile_store_mobile (store_id, mobile)");
            assertThat(schema).contains("KEY idx_store_login_mobile_mobile (mobile)");
        }
    }
}
