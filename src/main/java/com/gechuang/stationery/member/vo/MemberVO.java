package com.gechuang.stationery.member.vo;

import com.gechuang.stationery.member.entity.Member;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.Data;

@Data
public class MemberVO {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long id;
    private String memberNo;
    private String name;
    private String mobile;
    private String level;
    private Integer points;
    private Integer status;
    private String createdAt;
    private String updatedAt;

    public static MemberVO fromEntity(Member member) {
        MemberVO vo = new MemberVO();
        vo.setId(member.getId());
        vo.setMemberNo(member.getMemberNo());
        vo.setName(member.getName());
        vo.setMobile(member.getMobile());
        vo.setLevel(member.getLevel());
        vo.setPoints(member.getPoints());
        vo.setStatus(member.getStatus());
        vo.setCreatedAt(format(member.getCreatedAt()));
        vo.setUpdatedAt(format(member.getUpdatedAt()));
        return vo;
    }

    private static String format(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME_FORMATTER);
    }
}
