package com.xzdp.controller;


import com.xzdp.dto.Result;
import com.xzdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    /**
     *  尝试关注博主
     * @param followUserId 博主id
     * @param isFollow 是否关注
     * @return service层实现
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(followUserId, isFollow);
    }

    /**
     * 查询当前登陆用户是否关注点击的博主
     * @param followUserId 博主id
     * @return service层实现
     */
    @GetMapping("/or/not/{id}")
    public Result isFollowed(@PathVariable("id") Long followUserId) {
        return followService.isFollowed(followUserId);
    }


    /**
     *  查询当前登陆用户与点击博客用户的共同关注
     * @param id 博客用户id
     * @return service层实现
     */
    @GetMapping("/common/{id}")
    public Result getCommonFollow(@PathVariable("id") Long id) {
        return followService.getCommonFollow(id);
    }

}
