package com.gechuang.stationery.member.service;

import com.gechuang.stationery.member.dto.MemberCreateRequest;
import com.gechuang.stationery.member.dto.MemberUpdateRequest;
import com.gechuang.stationery.member.vo.MemberVO;
import java.util.List;

public interface MemberService {

    List<MemberVO> listMembers(String keyword);

    MemberVO createMember(MemberCreateRequest request);

    MemberVO updateMember(MemberUpdateRequest request);

    void deleteMember(Long id);
}
