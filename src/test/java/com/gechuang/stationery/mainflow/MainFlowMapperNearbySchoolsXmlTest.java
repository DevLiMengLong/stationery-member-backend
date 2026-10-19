package com.gechuang.stationery.mainflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class MainFlowMapperNearbySchoolsXmlTest {

    @Test
    void storeQueriesAndWritesShouldMapNearbySchools() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/mainflow/MainFlowMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        String namespace = MainFlowMapper.class.getName() + ".";
        String storeSql = configuration.getMappedStatement(namespace + "selectStoreById")
                .getBoundSql(Map.of("id", 7L)).getSql();
        String overviewSql = configuration.getMappedStatement(namespace + "selectOverviewStores")
                .getBoundSql(Map.of()).getSql();
        Map<String, Object> store = Map.ofEntries(
                Map.entry("id", 7L),
                Map.entry("account", "sample_store"),
                Map.entry("passwordHash", "hash"),
                Map.entry("shopName", "示例门店"),
                Map.entry("contactMobile", "13800000000"),
                Map.entry("avatarUrl", ""),
                Map.entry("address", "门店地址"),
                Map.entry("status", "ACTIVE"),
                Map.entry("smsQuota", 0),
                Map.entry("expiresAt", ""),
                Map.entry("nearbySchools", "东城小学\n实验中学"));
        String insertSql = configuration.getMappedStatement(namespace + "insertStore")
                .getBoundSql(store).getSql();
        String updateSql = configuration.getMappedStatement(namespace + "updateStore")
                .getBoundSql(store).getSql();

        assertThat(storeSql).contains("nearby_schools AS nearbySchools");
        assertThat(overviewSql).contains("sa.nearby_schools AS nearbySchools");
        assertThat(insertSql).contains("nearby_schools");
        assertThat(updateSql).contains("nearby_schools = ?");
    }
}
