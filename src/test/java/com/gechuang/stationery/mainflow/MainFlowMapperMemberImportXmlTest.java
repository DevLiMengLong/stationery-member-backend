package com.gechuang.stationery.mainflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class MainFlowMapperMemberImportXmlTest {

    @Test
    void memberImportStatementsShouldParseAndBuildSql() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/mainflow/MainFlowMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        String namespace = MainFlowMapper.class.getName() + ".";
        assertThat(configuration.hasStatement(namespace + "selectVisibleMemberMobiles")).isTrue();
        assertThat(configuration.hasStatement(namespace + "insertImportedMembers")).isTrue();
        assertThat(configuration.hasStatement(namespace + "selectMemberIdsByMobiles")).isTrue();
        assertThat(configuration.hasStatement(namespace + "insertWallets")).isTrue();

        String mobileLookupSql = configuration.getMappedStatement(namespace + "selectVisibleMemberMobiles")
                .getBoundSql(Map.of("storeId", 7L, "mobiles", List.of("13800000001")))
                .getSql();
        String insertSql = configuration.getMappedStatement(namespace + "insertImportedMembers")
                .getBoundSql(Map.of("members", List.of(Map.of(
                        "storeId", 7L,
                        "memberNo", "M202610011122334441234abcd",
                        "name", "测试会员",
                        "mobile", "13800000001",
                        "gender", "MALE",
                        "age", 20))))
                .getSql();
        BoundSql walletStatement = configuration.getMappedStatement(namespace + "insertWallets")
                .getBoundSql(Map.of("wallets", List.of(Map.of(
                        "memberId", 101L,
                        "rechargeBalance", new BigDecimal("80.20"),
                        "giftBalance", new BigDecimal("20.05")))));

        assertThat(mobileLookupSql.replaceAll("\\s+", "")).contains("mobileIN(?)");
        assertThat(insertSql).contains("INSERT INTO store_member", "'ACTIVE'");
        assertThat(walletStatement.getSql()).contains("INSERT INTO member_wallet", "recharge_balance", "gift_balance");
        assertThat(walletStatement.getParameterMappings().stream()
                .map(mapping -> walletStatement.getAdditionalParameter(mapping.getProperty())).toList())
                .containsExactly(101L, new BigDecimal("80.20"), new BigDecimal("20.05"));
    }
}
