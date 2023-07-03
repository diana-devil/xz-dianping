package com.xzdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xzdp.dto.LoginFormDTO;
import com.xzdp.dto.Result;
import com.xzdp.entity.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    /**
     *  发送验证码
     * @param phone 手机号
     * @param session session对象
     * @return 标准结果集
     */
    Result sendCode(String phone, HttpSession session);

    /**
     *  登陆功能实现
     *  登陆校验  使用session进行校验
     *  适用于单台 tomcat
     * @param loginForm 登陆信息
     * @param session session对象
     * @return 标准结果集
     */
    Result loginWithSession(LoginFormDTO loginForm, HttpSession session);

    /**
     * 登陆功能实现
     *  登陆校验 使用redis进行校验
     *  适用于 tomcat 集群 ，因为tomcat的session域信息不共享
     * @param loginForm 登陆信息
     * @return
     */
    Result login(LoginFormDTO loginForm);


    /**
     *  登出功能
     * @param request request 请求对象
     * @return
     */
    Result logout(HttpServletRequest request, HttpServletResponse response);

    /**
     * 实现用户签到
     * @return 返回为空
     */
    Result sign();

    /**
     * 得到用户本月截止到今天的连续签到数
     * @return 返回连续签到天数
     */
    Result getSignConDays();
}
