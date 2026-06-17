package com.gechuang.stationery.member.mapper;

import com.gechuang.stationery.member.entity.Member;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MemberMapper {

    List<Member> selectMembers(@Param("keyword") String keyword);

    Member selectById(@Param("id") Long id);

    Member selectByMobile(@Param("storeId") Long storeId, @Param("mobile") String mobile);

    int insert(Member member);

    int update(Member member);

    int upsertWalletPoints(@Param("memberId") Long memberId, @Param("points") Integer points);

    int softDeleteById(@Param("id") Long id);
}
