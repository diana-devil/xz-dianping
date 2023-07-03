package com.xzdp.service;

import com.xzdp.dto.Result;
import com.xzdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 点击博客，查询详情
     * @param id 博客id
     * @return 标准结果解
     */
    Result queryBlogById(Long id);

    /**
     * 查询所有的博客，并按照热点降序排序
     * @param current 当前页id
     * @return 标准结果集
     */
    Result queryHotBlog(Integer current);

    /**
     * 给博客点赞
     *  一个用户只能点赞一次，再次点击则取消点赞
     *
     * @param id 博客id
     * @return 标准结果集
     */
    Result likeBlog(Long id);

    /**
     * 查询当前博客点赞前5名
     * @param id 博客id
     * @return 标准结果
     */
    Result queryBlogLikes(Long id);

    /**
     * 保存博客信息到数据库
     * 并且将博客信息id推送到关注它的用户的Redis的SortedSet集合中
     * @param blog 博客信息
     * @return 标准结果集
     */
    Result saveBlog(Blog blog);


    /**
     * 获取我关注博主的所有博客，滚动分页显示
     * @param maxTime 上次返回的最小的时间戳，即这次查询的最大时间
     * @param offset 这次查询的偏移量
     * @return 博客信息+本次查询的最小时间戳+本次查询相同的最小时间戳的个数
     */
    Result getFollowBlogs(Long maxTime, Integer offset);
}
