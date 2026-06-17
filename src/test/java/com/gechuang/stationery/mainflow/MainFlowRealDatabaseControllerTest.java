package com.gechuang.stationery.mainflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class MainFlowRealDatabaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<String> tokens = new ArrayList<>();

    @AfterEach
    void cleanupSessions() {
        for (String token : tokens) {
            jdbcTemplate.update("DELETE FROM login_session WHERE token = ?", token);
        }
        tokens.clear();
    }

    @Test
    void adminOverviewShouldUseRealDatabaseCounts() throws Exception {
        Cookie adminToken = login("/api/auth/admin/login", Map.of(
                "username", "superadmin",
                "password", "Member@123"
        ), "ADMIN_TOKEN");

        JsonNode overview = data(mockMvc.perform(get("/api/admin/overview").cookie(adminToken))
                .andExpect(status().isOk())
                .andReturn());

        Integer storeCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM store_account", Integer.class);
        BigDecimal pendingAmount = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(w.recharge_balance + w.gift_balance), 0)
                FROM store_member m
                JOIN member_wallet w ON w.member_id = m.id
                WHERE m.deleted_at IS NULL
                """, BigDecimal.class);
        BigDecimal xinyuePendingAmount = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(w.recharge_balance + w.gift_balance), 0)
                FROM store_member m
                JOIN member_wallet w ON w.member_id = m.id
                JOIN store_account s ON s.id = m.store_id
                WHERE s.account = 'xinyue_store'
                  AND m.deleted_at IS NULL
                  AND m.status != 'DELETED'
                """, BigDecimal.class);

        assertThat(overview.path("storeCount").asInt()).isEqualTo(storeCount);
        assertThat(overview.path("pendingConsumptionAmount").asText()).isEqualTo(money(pendingAmount));
        assertThat(overview.path("stores")).hasSize(storeCount);
        assertThat(overview.path("stores").get(0).path("pendingConsumptionAmount").asText())
                .isEqualTo(money(xinyuePendingAmount));
    }

    @Test
    void merchantMembersShouldUseRealStoreMemberRows() throws Exception {
        Cookie merchantToken = login("/api/auth/merchant/login", Map.of(
                "account", "xinyue_store",
                "password", "123456"
        ), "MERCHANT_TOKEN");

        JsonNode page = data(mockMvc.perform(get("/api/merchant/members")
                        .param("pageNo", "1")
                        .param("pageSize", "20")
                        .cookie(merchantToken))
                .andExpect(status().isOk())
                .andReturn());

        Integer memberCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM store_member m
                JOIN store_account s ON s.id = m.store_id
                WHERE s.account = 'xinyue_store'
                  AND m.deleted_at IS NULL
                  AND m.status != 'DELETED'
                """, Integer.class);

        assertThat(page.path("total").asInt()).isEqualTo(memberCount);
        assertThat(page.path("records")).hasSize(memberCount);
        for (JsonNode row : page.path("records")) {
            assertThat(row.path("name").asText()).doesNotStartWith("演示会员");
        }
    }

    private Cookie login(String url, Map<String, String> payload, String cookieName) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie(cookieName);
        assertThat(cookie).isNotNull();
        tokens.add(cookie.getValue());
        return cookie;
    }

    private JsonNode data(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(response.path("success").asBoolean()).isTrue();
        return response.path("data");
    }

    private String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
