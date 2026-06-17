package com.gechuang.stationery.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gechuang.stationery.member.dto.MemberCreateRequest;
import com.gechuang.stationery.member.dto.MemberUpdateRequest;
import com.gechuang.stationery.member.vo.MemberVO;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class MemberServiceRealDatabaseTest {

    @Autowired
    private MemberService memberService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void memberCrudShouldReadAndWriteRealStoreMemberTables() {
        String mobile = uniqueMobile();
        Long createdId = null;

        try {
            MemberCreateRequest createRequest = new MemberCreateRequest();
            createRequest.setName("Real Db Test Member");
            createRequest.setMobile(mobile);
            createRequest.setPoints(620);

            MemberVO created = memberService.createMember(createRequest);
            createdId = created.getId();

            assertThat(created.getId()).isNotNull();
            assertThat(created.getMobile()).isEqualTo(mobile);
            assertThat(created.getPoints()).isEqualTo(620);
            assertThat(created.getStatus()).isEqualTo(1);
            assertThat(created.getLevel()).isEqualTo("VIP");
            assertThat(countRows("store_member", mobile)).isEqualTo(1);
            assertThat(countWalletRows(created.getId())).isEqualTo(1);

            assertThat(memberService.listMembers(mobile))
                    .extracting(MemberVO::getId)
                    .contains(created.getId());

            MemberUpdateRequest updateRequest = new MemberUpdateRequest();
            updateRequest.setId(created.getId());
            updateRequest.setName("Real Db Test Member Updated");
            updateRequest.setPoints(125);
            updateRequest.setStatus(0);

            MemberVO updated = memberService.updateMember(updateRequest);

            assertThat(updated.getName()).isEqualTo("Real Db Test Member Updated");
            assertThat(updated.getPoints()).isEqualTo(125);
            assertThat(updated.getStatus()).isEqualTo(0);
            assertThat(updated.getLevel()).isEqualTo("NORMAL");

            memberService.deleteMember(created.getId());

            assertThat(memberService.listMembers(mobile)).isEmpty();
            assertThat(countVisibleRows(mobile)).isZero();
            assertThat(countDeletedRows(mobile)).isEqualTo(1);
        } finally {
            cleanup(mobile, createdId);
        }
    }

    private String uniqueMobile() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 100_000_000);
        return "139" + String.format("%08d", suffix);
    }

    private Integer countRows(String tableName, String mobile) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE mobile = ?",
                Integer.class,
                mobile
        );
    }

    private Integer countWalletRows(Long memberId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member_wallet WHERE member_id = ?",
                Integer.class,
                memberId
        );
    }

    private Integer countVisibleRows(String mobile) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM store_member WHERE mobile = ? AND deleted_at IS NULL",
                Integer.class,
                mobile
        );
    }

    private Integer countDeletedRows(String mobile) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM store_member WHERE mobile = ? AND deleted_at IS NOT NULL AND status = 'DELETED'",
                Integer.class,
                mobile
        );
    }

    private void cleanup(String mobile, Long createdId) {
        if (createdId != null) {
            jdbcTemplate.update("DELETE FROM member_wallet WHERE member_id = ?", createdId);
        }
        jdbcTemplate.update("""
                DELETE FROM member_wallet
                WHERE member_id IN (SELECT id FROM store_member WHERE mobile = ?)
                """, mobile);
        jdbcTemplate.update("DELETE FROM store_member WHERE mobile = ?", mobile);
    }
}
