package com.xzdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xzdp.dto.Result;
import com.xzdp.dto.UserDTO;
import com.xzdp.entity.Follow;
import com.xzdp.mapper.FollowMapper;
import com.xzdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xzdp.service.IUserService;
import com.xzdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.xzdp.utils.Constants.RedisConstants.FOLLOW_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    /**
     * 尝试关注博主
     *
     *  1. 判断是关注还是取关
     *  2. 关注了，添加数据库，添加Redis  set集合
     *  3. 取关，删除数据库数据，从Redis的set集合中移除
     *
     * @param followUserId 博主id
     * @param isFollow 是否关注
     * @return 标准结果集
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        Follow follow = new Follow();
        String key = FOLLOW_KEY + userId;

        //1. 判断是关注还是取关
        if (isFollow) {
            //2.1 关注，添加数据库数据
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            //key 数据库操作成功后，在操作Redis
            if (isSuccess) {
                //2.2 添加set集合
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
                return Result.ok("关注成功！");
            }
            return Result.fail("数据异常！");
        }
        //3.1 取关，删除数据库数据
        //DELETE FROM tb_follow WHERE (user_id = ? AND follow_user_id = ?)
        boolean isSuccess = remove(new QueryWrapper<Follow>()
                .eq("user_id", userId)
                .eq("follow_user_id", followUserId));
        if (isSuccess) {
            //3.2 删除set集合
            stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            return Result.ok("取消关注成功！");
        }

        return Result.fail("数据异常！");
    }


    /**
     * 查询当前登陆用户是否关注点击的博主
     * @param followUserId 博主id
     * @return 标准结果集
     */
    @Override
    public Result isFollowed(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        String key = FOLLOW_KEY + userId;
        // 直接查Redis即可
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, followUserId.toString());
        return Result.ok(isMember);


        //查询是否关注-- 查询数据库
//        Integer count = lambdaQuery()
//                .eq(Follow::getUserId, userId)
//                .eq(Follow::getFollowUserId, followUserId)
//                .count();
//        return Result.ok(count > 0);
    }


    /**
     * 查询当前登陆用户与点击博客用户的共同关注
     * @param id 博客用户id
     * @return 返回共同关注的UserDTO对象集合，分页查询
     */
    @Override
    public Result getCommonFollow(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key1 = FOLLOW_KEY + userId.toString();
        String key2 = FOLLOW_KEY + id.toString();
        //1. 获取共同关注的set集合
        Set<String> followUserIds = stringRedisTemplate.opsForSet().intersect(key1, key2);

        //2. 解析用户id
        if (followUserIds == null || followUserIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> followIds = followUserIds
                .stream()
                .map(Long::valueOf).collect(Collectors.toList());

        //3. 查询用户，并封装UserDTO
        List<UserDTO> userDtoList = userService.listByIds(followIds)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDtoList);
    }
}
