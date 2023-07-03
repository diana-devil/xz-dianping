package com.xzdp.dto;

import lombok.Data;

//1、 保存用户的敏感信息
//2、 减少内存存储数据的压力
@Data
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;
}
