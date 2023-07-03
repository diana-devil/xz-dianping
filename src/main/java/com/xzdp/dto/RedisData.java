package com.xzdp.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 将原始数据和逻辑过期时间进行封装，可以减少对源代码的侵入
 *
 */
@Data
public class RedisData {
    /**
     *  逻辑过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 原始数据
     */
    private Object data;
}
