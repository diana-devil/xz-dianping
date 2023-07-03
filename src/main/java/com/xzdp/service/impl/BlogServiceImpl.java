package com.xzdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzdp.dto.Result;
import com.xzdp.dto.ScrollResult;
import com.xzdp.dto.UserDTO;
import com.xzdp.entity.Blog;
import com.xzdp.entity.Follow;
import com.xzdp.entity.User;
import com.xzdp.mapper.BlogMapper;
import com.xzdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xzdp.service.IFollowService;
import com.xzdp.service.IUserService;
import com.xzdp.utils.Constants.SystemConstants;
import com.xzdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.xzdp.utils.Constants.RedisConstants.BLOG_LIKED_KEY;
import static com.xzdp.utils.Constants.RedisConstants.FEED_KEY;
import static com.xzdp.utils.Constants.SystemConstants.DEFAULT_PAGE_SIZE;

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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    /**
     * 查询所有的博客，并按照热点降序排序
     *
     * @param current 当前页id
     * @return
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // key lambda表达式应用
        // 封装博客用户信息 + 查询博客是否被当前用户点赞
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });

        return Result.ok(records);
    }


    /**
     * 点击博客，查询详情
     *
     *  1. 根据id查询博客
     *  2. 查询博客有关用户,并封装信息
     *  3. 查询是否点赞，并设置isLike字段
     * @param id 博客id
     * @return 标准结果集
     */
    @Override
    public Result queryBlogById(Long id) {
        //1.根据id查询博客
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("博客不存在！");
        }
        //2.查询博客有关用户
        queryBlogUser(blog);
        //3.查询是否点赞，并设置isLike字段
        isBlogLiked(blog);
        return Result.ok(blog);
    }


    /**
     *  查询当前用户，是否给博客点赞
     *  从Redis中查询 当前登陆用户id，若存在，则设置isLike字段为 true，否则为false
     * @param blog 博客信息
     */
    private void isBlogLiked(Blog blog) {
        //1.获取登录用户 --- 登陆拦截
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录，无需查询是否点赞
            return;
        }
        String userId = user.getId().toString();
        //2.判断当前登录用户是否已经点赞
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId);
        blog.setIsLike(score != null);
    }



    /**
     *  给博客点赞
     *
     *  一个用户只能点赞一次，再次点击则取消点赞
     *  使用value采用SortedSet这种数据结构，保证唯一性,且可以获取排名
     *
     *
     *  1. 检测用户id是否存在，如果存在，则取消点赞
     *      1.1 数据库点赞数 -1
     *      1.2 从SortedSet集合中移除用户id
     *
     *  2. 如果不存在，则将用户id存入set集合，并且点赞
     *      2.1 数据库点赞数 +1
     *      2.2 向SortedSet集合中添加用户id，并设置分数为时间戳
     *
     * @param id 博客id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        Blog blog = getById(id);
        String userId = UserHolder.getUser().getId().toString();
        String key = BLOG_LIKED_KEY + blog.getId();
        // 数据库是否操作成功
        boolean isSuccess;

        //1.检测用户id是否存在，如果存在，则取消点赞
        Double score = stringRedisTemplate.opsForZSet().score(key, userId);
        if (score != null) {
            //1.1 数据库点赞数 -1
            isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            //1.2 key 数据库操作成功后，在进行redis相关操作
            // 从SortedSet集合中移除用户id
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId);
            }
            log.info("取消点赞！");
            return Result.ok();
        }

        //2.如果不存在，则将用户id存入set集合，点赞数加1
        //2.1 数据库点赞数 +1
        isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
        if (isSuccess) {
            //2.2 向SortedSet集合中添加用户id,使用时间戳作为 分数
            stringRedisTemplate.opsForZSet().add(key, userId, System.currentTimeMillis());
        }
        log.info("点赞成功！");

        return Result.ok();
    }


    /**
     * 查询当前博客点赞前5名
     * key 使用stream流的方式来处理
     *
     * @param id 博客id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        //1.查询点赞前5名用户
        Set<String> userIds = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (userIds == null || userIds.isEmpty()) {
            log.info("点赞数为0！");
            return Result.ok("还没有人点赞哦，来做第一个吧！");
        }

        // 2.解析出用户id
        // key stream 流；使用 Long.valueOf()方法；处理结果进行收集转成list集合
        List<Long> ids = userIds.stream().map(Long::valueOf).collect(Collectors.toList());
//        List<Long> ids = userIds.stream().map(uid -> Long.valueOf(uid)).collect(Collectors.toList());

        //3. 根据用户id查询用户信息，并封装UserDTO
        // tool ids=[1,2,3]  --> idStr = "1,2,3"
        // 将list集合中元素，用","分割拼接
        String idStr = StrUtil.join(",", ids);
        System.out.println(idStr);
        // WHERE id IN ( 5 , 1 ) --- 查询出来的顺序是按照id排序的 即 （1,5）
        //key  WHERE id IN ( 5 , 1 ) order by field(id, 5,1) ————(5,1)
        List<UserDTO> userDTOS = userService.query()
                //WHERE (id IN (?,?,?)) order by field (id,1010,1011,1)
                .in("id", ids).last("order by field (id," + idStr +")").list()
                .stream()
                //调用 BeanUtil.copyProperties(user, UserDTO.class) 方法，将user转换成UserDTO
                // 箭头左侧为 查询的List<User> 中的一个User对象
                // 箭头右侧为  将User对象的相应属性copy过后的 一个UserDTO对象
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                // 收集处理结果，变成list集合
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    /**
     *
     * 保存博客信息到数据库
     * 并且将博客信息id推送到关注它的用户的Redis的SortedSet集合中
     *
     *  1. 获取发博客用户
     *  2. 保存探店博文到数据库
     *  3. 获取关注当前博主的用户 *
     *  4. 将博客id推送到对应的SortedSet集合中
     *  5. 返回博客id
     * @param blog 博客信息
     * @return 博客id
     */
    @Override
    public Result saveBlog(Blog blog) {
        //1. 获取发博客用户
        Long blogId = UserHolder.getUser().getId();
        blog.setUserId(blogId);
        //2. 保存探店博文到数据库
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("进程异常！");
        }
        //3. 获取关注当前博主的用户Id
        // select * from tb_follow where follow_user_id = blogId
        //3.1 从数据库中查询关注当前博主的所有 *
        List<Follow> followUser = followService.lambdaQuery().eq(Follow::getFollowUserId, blogId).list();
//        //3.2 提取用户id
//        List<Long> userIds = followUser.stream().map(Follow::getUserId).collect(Collectors.toList());

        //4. 将博客id推送到对应的SortedSet集合中
        followUser.forEach(follow -> stringRedisTemplate.opsForZSet()
                .add(FEED_KEY + follow.getUserId(), blog.getId().toString(), System.currentTimeMillis()));
//        userIds.forEach(userId -> stringRedisTemplate.opsForZSet()
//                .add(FEED_KEY + userId, blog.getId().toString(), System.currentTimeMillis()));

        // 4.返回博客id
        return Result.ok(blog.getId());
    }



    /**
     * 获取我关注博主的所有博客，滚动分页显示
     *
     *
     *  1. 查询当前用户的 feed SortedSet集合
     *  2. 根据当前查询的最大时间戳和偏移量，滚动分页查询博客id, 降序排列，时间戳大的在前面
     *  3. 解析数据,获取博客id，minTime，offset
     *  4. 根据博客id，查询数据库, 注意按照博客id顺序排序
     *  5. 丰富博客信息，添加博客用户，及是否点赞
     *  6. 封装返回对象 ScrollResult
     *
     *
     * @param maxTime 上次返回的最小的时间戳，即这次查询的最大时间
     *                key 时间戳是Long类型的
     * @param offset 这次查询的偏移量
     * @return 博客信息+本次查询的最小时间戳+本次查询相同的最小时间戳的个数
     */
    @Override
    public Result getFollowBlogs(Long maxTime, Integer offset) {
        //1. 获取当前用户的 feed-SortedSet集合的键
        Long userId = UserHolder.getUser().getId();
        String key = FEED_KEY + userId;
        //2. 根据当前查询的最大时间戳和偏移量，滚动分页查询博客id, 降序排列，时间戳大的在前面
        //zrevrangebyscore feedkey max min [withscores] [limit offset count]
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, maxTime, offset, DEFAULT_PAGE_SIZE);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok("没有关注博主，快去关注吧！");
        }

        //3. 解析数据,获取博客id，minTime，offset
        List<Long> blogIds = new ArrayList<>(typedTuples.size());
        long minTime = Long.MAX_VALUE;
        //偏移量
        int of = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            // 将博客id添加到list集合
            blogIds.add(Long.valueOf(typedTuple.getValue()));
            //记录，比较最小值
            Long score = typedTuple.getScore().longValue();
            if (score == minTime) {
                of++;
            } else if (score < minTime) {
                minTime = score;
                //重置最小时间戳技术
                of = 1;
            }
        }

        //4. 根据博客id，查询数据库, 注意按照博客id顺序排序
        //WHERE (id IN (?,?,?)) order by field (id,1010,1011,1)
        String ids = StrUtil.join(",", blogIds);
        List<Blog> blogs = lambdaQuery()
                .in(Blog::getId, blogIds)
                .last("order by field (id," + ids + ")")
                .list();

        if (blogs == null || blogs.isEmpty()) {
            return Result.fail("数据库异常！");
        }

//        5. 丰富博客信息，添加博客用户，及是否点赞
        for (Blog blog : blogs) {
            //5.1 查询博客有关用户
            queryBlogUser(blog);
            //5.2 查询是否点赞，并设置isLike字段
            isBlogLiked(blog);
        }

        //6. 封装返回对象 ScrollResult
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(of);

        return Result.ok(scrollResult);
    }


    /**
     * 根据博客中的用户id，查询发博用户信息,并进行封装
     *
     * @param blog 博客对象
     */
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }


}
