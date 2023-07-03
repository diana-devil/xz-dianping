package com.xzdp.config;

import com.xzdp.utils.Interceptor.LoginInterceptor;
import com.xzdp.utils.Interceptor.RefreshTokenInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 配置MVC
@Configuration
public class MvcConfig implements WebMvcConfigurer{

//    @Autowired
//    private StringRedisTemplate stringRedisTemplate;

    //将拦截器注册成bean
    @Bean
    public RefreshTokenInterceptor refreshTokenInterceptor() {
        return new RefreshTokenInterceptor();
    }

    // 配置拦截器
    // 新声明 先执行 （默认order都是0）
    //也可以手动设置  order,越小，优先级越高
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 添加 token刷新拦截器 拦截一切路径
        // 使用构造函数的方法引入 stringRedisTemplate
//        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).order(0);

        //将拦截器注册成Bean后，在拦截器内部使用@Autowired自动注入
        registry.addInterceptor(refreshTokenInterceptor()).order(0);

        //添加登陆验证拦截器 并配置拦截器 不 拦截的路径
       registry.addInterceptor(new LoginInterceptor())
               .excludePathPatterns(
                       "/user/login",
                       "/user/code",
                       "/shop/**",
                       "/voucher/**",
                       "/upload/**",
                       "/blog/hot",
                       "/shop-type/**"
               ).order(1);
    }
}
