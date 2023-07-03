package com.xzdp.utils.Interceptor;

import com.xzdp.dto.UserDTO;
import com.xzdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

//拦截器
//做登陆校验
// 从ThreadLocal 中获取信息，拦截 需要 登陆的路径
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    // 登陆校验
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 判断用户是否存在  从从ThreadLocal中查询
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            //用户未登录，进行拦截，返回401 状态码
            response.setStatus(401);
            log.info("用户没有登陆！");
            return false;
        }

        //放行
        return true;
    }


}
