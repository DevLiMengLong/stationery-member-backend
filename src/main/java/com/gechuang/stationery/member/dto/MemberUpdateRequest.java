package com.gechuang.stationery.member.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MemberUpdateRequest {

    @NotNull
    private Long id;

    @Size(max = 64)
    private String name;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "must be a valid mainland China mobile number")
    private String mobile;

    @Size(max = 32)
    private String level;

    @Min(0)
    private Integer points;

    @Min(0)
    private Integer status;
}
