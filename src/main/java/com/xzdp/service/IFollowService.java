package com.xzdp.service;

import com.xzdp.dto.Result;
import com.xzdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 尝试关注博主
     *
     * @param followUserId 博主id
     * @param isFollow 是否关注
     * @return 标准结果
     */
    Result follow(Long followUserId, Boolean isFollow);

    /**
     * 查询当前登陆用户是否关注点击的博主
     * @param followUserId 博主id
     * @return 标准结果
     */
    Result isFollowed(Long followUserId);

    /**
     * 查询当前登陆用户与点击博客用户的共同关注
     * @param id 博客用户id
     * @return 标准结果集
     */
    Result getCommonFollow(Long id);
}
