package com.gechuang.stationery.member.service.impl;

import com.gechuang.stationery.member.dto.MemberCreateRequest;
import com.gechuang.stationery.member.dto.MemberUpdateRequest;
import com.gechuang.stationery.member.entity.Member;
import com.gechuang.stationery.member.mapper.MemberMapper;
import com.gechuang.stationery.member.service.MemberService;
import com.gechuang.stationery.member.vo.MemberVO;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MemberServiceImpl implements MemberService {

    private static final int STATUS_ENABLED = 1;
    private static final String DEFAULT_LEVEL = "NORMAL";
    private static final DateTimeFormatter MEMBER_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MemberMapper memberMapper;

    public MemberServiceImpl(MemberMapper memberMapper) {
        this.memberMapper = memberMapper;
    }

    @Override
    public List<MemberVO> listMembers(String keyword) {
        return memberMapper.selectMembers(keyword).stream()
                .map(MemberVO::fromEntity)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberVO createMember(MemberCreateRequest request) {
        Member duplicate = memberMapper.selectByMobile(request.getMobile());
        if (duplicate != null) {
            throw new IllegalArgumentException("Mobile already exists");
        }

        Member member = new Member();
        member.setMemberNo(generateMemberNo(request.getMobile()));
        member.setName(request.getName());
        member.setMobile(request.getMobile());
        member.setLevel(StringUtils.hasText(request.getLevel()) ? request.getLevel() : DEFAULT_LEVEL);
        member.setPoints(request.getPoints() == null ? 0 : request.getPoints());
        member.setStatus(STATUS_ENABLED);
        memberMapper.insert(member);
        return MemberVO.fromEntity(memberMapper.selectById(member.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberVO updateMember(MemberUpdateRequest request) {
        Member existing = memberMapper.selectById(request.getId());
        if (existing == null) {
            throw new IllegalArgumentException("Member does not exist");
        }

        if (StringUtils.hasText(request.getMobile())) {
            Member duplicate = memberMapper.selectByMobile(request.getMobile());
            if (duplicate != null && !Objects.equals(duplicate.getId(), request.getId())) {
                throw new IllegalArgumentException("Mobile already exists");
            }
        }

        Member member = new Member();
        member.setId(request.getId());
        member.setName(request.getName());
        member.setMobile(request.getMobile());
        member.setLevel(request.getLevel());
        member.setPoints(request.getPoints());
        member.setStatus(request.getStatus());
        memberMapper.update(member);
        return MemberVO.fromEntity(memberMapper.selectById(request.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMember(Long id) {
        Member existing = memberMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Member does not exist");
        }
        memberMapper.deleteById(id);
    }

    private String generateMemberNo(String mobile) {
        String suffix = mobile.substring(Math.max(0, mobile.length() - 4));
        return "M" + LocalDate.now().format(MEMBER_NO_DATE) + suffix;
    }
}
