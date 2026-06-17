package com.gechuang.stationery.member.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class Member {

    private Long id;
    private Long storeId;
    private String memberNo;
    private String name;
    private String mobile;
    private String level;
    private Integer points;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
