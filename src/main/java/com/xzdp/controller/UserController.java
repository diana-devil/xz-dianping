package com.xzdp.controller;


import cn.hutool.core.bean.BeanUtil;
import com.xzdp.dto.LoginFormDTO;
import com.xzdp.dto.Result;
import com.xzdp.dto.UserDTO;
import com.xzdp.entity.User;
import com.xzdp.entity.UserInfo;
import com.xzdp.service.IUserInfoService;
import com.xzdp.service.IUserService;
import com.xzdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;


    /**
     * 发送手机验证码
     * @param phone 输入手机号
     * @param session session对象
     * @return
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        //key 具体的业务代码实现 放到service中编写
        return userService.sendCode(phone, session);
    }


    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        //使用session校验
//        return userService.loginWithSession(loginForm, session);
        //使用redis校验
        return userService.login(loginForm);
    }


    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(HttpServletRequest request, HttpServletResponse response){
        //用户登出
        return userService.logout(request, response);
    }


    /**
     * 得到 ThreadLocal中用户信息，并返回给前端
     * @return 将用户中有用的部分信息 封装UserDTO 返回给前端
     */
    @GetMapping("/me")
    public Result me(){
        UserDTO userDTO = UserHolder.getUser();
        return Result.ok(userDTO);
    }


    /**
     * 访问个人主页
     * @param userId 用户id
     * @return
     */
    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        //查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            log.info("没有详情！");
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }


    /**
     * 根据用户id 查询用户信息
     * 封装UserDTO返回
     * @param userId 用户id
     * @return UserDTO对象
     */
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId) {
        User user = userService.getById(userId);
        if (userId == null) {
            return Result.fail("当前用户信息不存在！");
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    /**
     * 实现用户签到
     * @return server层实现
     */
    @PostMapping("/sign")
    public Result sign() {
        return userService.sign();
    }

    /**
     * 得到用户本月截止到今天的连续签到数
     * @return service层实现
     */
    @GetMapping("/sign/count")
    public Result getSignConDays() {
        return userService.getSignConDays();
    }
}
