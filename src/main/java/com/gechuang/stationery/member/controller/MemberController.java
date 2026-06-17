package com.gechuang.stationery.member.controller;

import com.gechuang.stationery.common.RestResponse;
import com.gechuang.stationery.member.dto.MemberCreateRequest;
import com.gechuang.stationery.member.dto.MemberUpdateRequest;
import com.gechuang.stationery.member.service.MemberService;
import com.gechuang.stationery.member.vo.MemberVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public RestResponse<List<MemberVO>> list(@RequestParam(required = false) String keyword) {
        return RestResponse.success(memberService.listMembers(keyword));
    }

    @PostMapping
    public RestResponse<MemberVO> create(@Valid @RequestBody MemberCreateRequest request) {
        return RestResponse.success(memberService.createMember(request));
    }

    @PutMapping("/{id}")
    public RestResponse<MemberVO> update(
            @PathVariable @NotNull Long id,
            @Valid @RequestBody MemberUpdateRequest request
    ) {
        request.setId(id);
        return RestResponse.success(memberService.updateMember(request));
    }

    @DeleteMapping("/{id}")
    public RestResponse<Void> delete(@PathVariable @NotNull Long id) {
        memberService.deleteMember(id);
        return RestResponse.success();
    }
}
