package com.gechuang.stationery.member.service.impl;

import com.gechuang.stationery.member.dto.MemberCreateRequest;
import com.gechuang.stationery.member.dto.MemberUpdateRequest;
import com.gechuang.stationery.member.entity.Member;
import com.gechuang.stationery.member.mapper.MemberMapper;
import com.gechuang.stationery.member.service.MemberService;
import com.gechuang.stationery.member.vo.MemberVO;
import com.gechuang.stationery.notification.MemberNotificationService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MemberServiceImpl implements MemberService {

    private static final Long DEFAULT_STORE_ID = 1L;
    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;
    private static final DateTimeFormatter MEMBER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final MemberMapper memberMapper;
    private final MemberNotificationService notificationService;

    public MemberServiceImpl(MemberMapper memberMapper, MemberNotificationService notificationService) {
        this.memberMapper = memberMapper;
        this.notificationService = notificationService;
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
        Member duplicate = memberMapper.selectByMobile(DEFAULT_STORE_ID, request.getMobile());
        if (duplicate != null) {
            throw new IllegalArgumentException("Mobile already exists");
        }

        Member member = new Member();
        member.setStoreId(DEFAULT_STORE_ID);
        member.setMemberNo(generateMemberNo(request.getMobile()));
        member.setName(request.getName());
        member.setMobile(request.getMobile());
        member.setStatus(STATUS_ENABLED);
        memberMapper.insert(member);
        memberMapper.upsertWalletPoints(member.getId(), normalizePoints(request.getPoints()));
        MemberVO created = MemberVO.fromEntity(memberMapper.selectById(member.getId()));
        notificationService.notifyMemberCreated(memberMapper.selectStoreNameById(DEFAULT_STORE_ID), created.getMobile());
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberVO updateMember(MemberUpdateRequest request) {
        if (request.getId() == null) {
            throw new IllegalArgumentException("Member id is required");
        }
        Member existing = memberMapper.selectById(request.getId());
        if (existing == null) {
            throw new IllegalArgumentException("Member does not exist");
        }

        if (StringUtils.hasText(request.getMobile())) {
            Member duplicate = memberMapper.selectByMobile(DEFAULT_STORE_ID, request.getMobile());
            if (duplicate != null && !Objects.equals(duplicate.getId(), request.getId())) {
                throw new IllegalArgumentException("Mobile already exists");
            }
        }

        Member member = new Member();
        member.setId(request.getId());
        member.setName(request.getName());
        member.setMobile(request.getMobile());
        member.setStatus(normalizeStatus(request.getStatus()));
        if (hasMemberProfileUpdate(request)) {
            memberMapper.update(member);
        }
        if (request.getPoints() != null) {
            memberMapper.upsertWalletPoints(request.getId(), normalizePoints(request.getPoints()));
        }
        return MemberVO.fromEntity(memberMapper.selectById(request.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMember(Long id) {
        Member existing = memberMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Member does not exist");
        }
        memberMapper.softDeleteById(id);
    }

    private String generateMemberNo(String mobile) {
        String suffix = mobile.substring(Math.max(0, mobile.length() - 4));
        return "M" + LocalDateTime.now().format(MEMBER_NO_TIME) + suffix;
    }

    private boolean hasMemberProfileUpdate(MemberUpdateRequest request) {
        return request.getName() != null
                || request.getMobile() != null
                || request.getStatus() != null;
    }

    private int normalizePoints(Integer points) {
        if (points == null) {
            return 0;
        }
        if (points < 0) {
            throw new IllegalArgumentException("Points must not be negative");
        }
        return points;
    }

    private Integer normalizeStatus(Integer status) {
        if (status == null) {
            return null;
        }
        if (status == STATUS_ENABLED || status == STATUS_DISABLED) {
            return status;
        }
        throw new IllegalArgumentException("Status must be 0 or 1");
    }
}
