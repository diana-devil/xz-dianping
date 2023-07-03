package com.xzdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzdp.dto.Result;
import com.xzdp.dto.UserDTO;
import com.xzdp.entity.Blog;
import com.xzdp.service.IBlogService;
import com.xzdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

import static com.xzdp.utils.Constants.SystemConstants.MAX_PAGE_SIZE;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;


    /**
     * 保存博客信息到数据库
     * 并且将博客信息id推送到关注它的用户的Redis的SortedSet集合中
     * @param blog blog信息
     * @return service 层实现
     */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }


    /**
     * 实现博客点赞功能
     * @param id 博客id
     * @return service层实现
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    /**
     * 查询当前博客点赞前5名
     * @param id 博客id
     * @return service层实现
     */
    @GetMapping ("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
        return blogService.queryBlogLikes(id);
    }



    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }


    /**
     * 查询所有的博客，并按照热点降序排序
     * 分页查询
     * @param current 当前页id
     * @return 交由service实现
     */
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }


    /**
     *  点击博客，查询详情
     *
     * @param id 博客id
     * @return 交由service层处理
     */
    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        return blogService.queryBlogById(id);

    }


    /**
     * 根据用户id 查询用户博客详情
     * 分页查询
     * key ？参数   +   分页查询
     * @param current 当前页
     * @param userId 用户id
     * @return 分页查询结果
     */
    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "id") Long userId) {
        //根据当前用户查询
        Page<Blog> page = blogService.lambdaQuery()
                .eq(Blog::getUserId, userId)
                .page(new Page<>(current, MAX_PAGE_SIZE));
        //返回分页查询结果 中的当前页
        return Result.ok(page.getRecords());
    }

    /**
     *  获取我关注博主的所有博客，滚动分页显示
     * @param maxTime 上次返回的最小的时间戳，即这次查询的最大时间
     * @param offset 这次查询的偏移量
     * @return service层中实现
     */
    @GetMapping("/of/follow")
    public Result getFollowBlogs(
            @RequestParam(value = "lastId") Long maxTime,
            @RequestParam(value = "offset", defaultValue = "0") Integer offset) {
        return blogService.getFollowBlogs(maxTime, offset);
    }
}
