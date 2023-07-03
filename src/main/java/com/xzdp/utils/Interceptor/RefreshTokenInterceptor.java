package com.xzdp.utils.Interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.xzdp.dto.UserDTO;
import com.xzdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.xzdp.utils.Constants.RedisConstants.LOGIN_USER_KEY;
import static com.xzdp.utils.Constants.SystemConstants.TOKEN_HEADER;

// 配置拦截器
// 该拦截器 拦截一切请求 实现 对 token 令牌的有效期的刷新
@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {

    //key 拦截器加载的时间点在springcontext之前，而Bean又是由spring进行管理 所以不能自动注入
    // 自动注入的方法是： 在SpringMVC配置拦截器前，将拦截器其手动的注册成一个Bean
//    private StringRedisTemplate stringRedisTemplate;
//
//    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
//        this.stringRedisTemplate = stringRedisTemplate;
//    }

    //key 在 MVCConfig 中配置 了bean，可以在这里自动注入
    // 说明这个 拦击器已经在容器内部了，可以使用自动注入的方式，获取spring容器中的bean
    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    //获取用户信息，并存入 ThreadLocal
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 1.获取用户， 从redis中获取
        // 1.1得到请求头中token
        String token = request.getHeader(TOKEN_HEADER);
        //tool 使用工具类判空
        if (StrUtil.isBlank(token)) {
           // 如果token为空，即没有登陆，直接放行
            return true;
        }
        //1.2 根据key 去redis中获取对象
        String tokenKey = LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);


        //2. 判断用户是否存在
        //2.1 用户不存在，即没有登陆，直接放行
        if (userMap.isEmpty()) {
            return true;
        }

        // 2.2 将查询的map类型的user 转换成 UserDTO类型
        // tool 使用工具类将Map转换成UserDTO类型
        // false 表示不忽略异常
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        //2.3 key 用户存在，保存用户到 ThreadLocal 供给每个线程使用
        UserHolder.saveUser(userDTO);

        //3.刷新token有效期 30分钟
//        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        stringRedisTemplate.expire(tokenKey, 1000, TimeUnit.MINUTES);
        //log.info("token 令牌刷新成功！");

        //放行
        return true;
    }


    // 数据销毁
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //key 因为ThreadLocal底层是ThreadLocalMap，当期线程Threadlocal作为key(弱引用)，user作为value(强引用)然后
        // jvm不会把强引用的value回收掉，所以value不会被自动释放 需要手动释放

        //移除user信息
        UserHolder.removeUser();
//        log.info("用户信息已销毁");
    }
}
