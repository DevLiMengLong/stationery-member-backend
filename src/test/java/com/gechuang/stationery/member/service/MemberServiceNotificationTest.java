package com.gechuang.stationery.member.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gechuang.stationery.member.dto.MemberCreateRequest;
import com.gechuang.stationery.member.entity.Member;
import com.gechuang.stationery.member.mapper.MemberMapper;
import com.gechuang.stationery.member.service.impl.MemberServiceImpl;
import com.gechuang.stationery.notification.MemberNotificationService;
import org.junit.jupiter.api.Test;

class MemberServiceNotificationTest {

    @Test
    void createMemberShouldSendWelcomeNotification() {
        MemberMapper memberMapper = mock(MemberMapper.class);
        MemberNotificationService notificationService = mock(MemberNotificationService.class);
        MemberServiceImpl service = new MemberServiceImpl(memberMapper, notificationService);
        MemberCreateRequest request = new MemberCreateRequest();
        request.setName("测试会员");
        request.setMobile("13800000073");
        request.setPoints(0);
        when(memberMapper.selectByMobile(1L, "13800000073")).thenReturn(null);
        when(memberMapper.selectStoreNameById(1L)).thenReturn("晨光文具店");
        doAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            member.setId(99L);
            return 1;
        }).when(memberMapper).insert(any(Member.class));
        Member created = new Member();
        created.setId(99L);
        created.setMobile("13800000073");
        created.setMemberNo("M202609190073");
        created.setName("测试会员");
        created.setStatus(1);
        when(memberMapper.selectById(99L)).thenReturn(created);

        service.createMember(request);

        verify(notificationService).notifyMemberCreated("晨光文具店", "M202609190073", "13800000073");
    }
}
