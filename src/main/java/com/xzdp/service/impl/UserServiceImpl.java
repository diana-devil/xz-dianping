package com.xzdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xzdp.dto.LoginFormDTO;
import com.xzdp.dto.Result;
import com.xzdp.dto.UserDTO;
import com.xzdp.entity.User;
import com.xzdp.mapper.UserMapper;
import com.xzdp.service.IUserService;
import com.xzdp.utils.RegexUtils;
import com.xzdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.xzdp.utils.Constants.RedisConstants.*;
import static com.xzdp.utils.Constants.SessionContants.CODE;
import static com.xzdp.utils.Constants.SessionContants.USER;
import static com.xzdp.utils.Constants.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    /**
     *  给用户发送验证码
     * @param phone 手机号
     * @param session session对象
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.手机号校验
        //函数返回true 表示手机号码格式不正确
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号码不正确！请重新输入。");
        }


        //2.生成验证码
        //tool 利用第三方工具包 生成 验证码
        String code = RandomUtil.randomNumbers(6); //生成6位随机数字

        //3. 将验证码存到redis中
        //key 多个 tomcat的session域的数据不共享，所以使用redis代替session
        //使用手机号作为键(手机号唯一)，验证码作为值  都用 String类型
        // 给验证码设置 有效期，避免redis 存储过多数据
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);


//        //3.将验证码保存到session中
        // 这里如果只有一台 tomcat 不会有session域共享问题，可以这么做
//        session.setAttribute(CODE, code);

        //3.发送验证码  -- 模拟发送
        log.info("发送短信成功！验证码为{}", code);

        //返回ok
        return Result.ok();
    }


    /**
     *  登陆校验 -- 使用session
     *  适用于单台 tomcat
     * @param loginForm 登陆信息
     * @param session session对象
     * @return
     */
    @Override
    public Result loginWithSession(LoginFormDTO loginForm, HttpSession session) {
        //1. 得到手机号和验证码
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();

        //2.1.1 校验手机号格式
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号码格式错误！请重新输入。");
        }
        //2.1.2 校验手机号是否是获取验证码的手机号
        // 存储phone到session中，检测两个phone是否一样


        //2.2 校验验证码 —— 与session中的验证码对比
        String cacheCode = session.getAttribute(CODE).toString();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码不正确！");
        }

        //3. 根据手机号查询用户
//        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
//        qw.eq(User::getPhone, phone);
//        User user = userMapper.selectOne(qw);

        // key 使用userService中 lambdaQuery 方法 不用自己创建对象
        User user = lambdaQuery().eq(User::getPhone, phone).one();


        //4. 用户不存在，创建新用户，并保存到数据库中
        if (user == null) {
            user = CreateUserByPhone(phone);
        }

        //5. 将用户存到session中，
        // key 存入UserDTO 对象   使用工具类进行转换
        //  session中存储值，有效期默认是 30分钟
        session.setAttribute(USER, BeanUtil.copyProperties(user, UserDTO.class));

        return Result.ok();
    }



    /**
     * 登陆校验 -- 使用redis
     * 适用于tomcat集群
     * @param loginForm 登陆信息
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm) {
        //1. 得到手机号和验证码
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();

        //2.1.1 校验手机号格式
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号码格式错误！请重新输入。");
        }
        //2.1.2 校验手机号是否是获取验证码的手机号
        // 根据 reids 使用手机号作为验证码的键值，可以解决这个问题
        // 因为 你如果换了手机号，其在redis中存的cacheCode为null ， 就登陆不上去了


        //2.2 校验验证码 —— 与reids中的验证码对比
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        System.out.println(cacheCode);
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码不正确！");
        }

        //3. 根据手机号查询用户
        User user = lambdaQuery().eq(User::getPhone, phone).one();


        //4. 用户不存在，创建新用户，并保存到数据库中
        if (user == null) {
            user = CreateUserByPhone(phone);
        }

        //5. 将用户存到reids中 ， 使用 token：s 作为键，使用 hash模式存储用户信息
        //5.1 获取uuid作为唯一键值
        String token = UUID.randomUUID().toString();

        //5.2 将User对象装出UserDTO对象的HashMap存储
        // tool 存入UserDTO 对象   使用工具类进行转换
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //将用户信息封装成Map
        // tool 使用工具类 实现
        // 注意 将 id 从Long类型转换成String
//        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true) //忽略null值
                        //将value值 使用 string类型存储
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        //手动实现
//        Map<String, String> map = new HashMap<>();
//        map.put("ID", userDTO.getId().toString());
//        map.put("ICON", userDTO.getIcon());
//        map.put("NICKNAME", userDTO.getNickName());

        //5.3使用hash类型存储用户信息
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);

        //5.4设置有效时间 30分钟
        // 要在拦截器中配置 不断刷新登录token令牌的存活时间
        // 保证存活时间是用户在不活跃的时候开始倒计时
        stringRedisTemplate.expire(tokenKey, 30, TimeUnit.MINUTES);

        // 将token 返回给前端
        return Result.ok(token);
    }

    /**
     * 实现用户登出
     * @param request request 请求对象
     * @return
     */
    @Override
    public Result logout(HttpServletRequest request, HttpServletResponse response) {
        //从浏览器头中获取token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return Result.ok();
        }
        //从redis中删除对应的键
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.delete(tokenKey);
        log.info("删除redis中键！");
        response.setStatus(200);
        return Result.ok();
    }


    /**
     *
     * 实现用户签到功能
     *  以 用户id+年+月 为键， 以bitMap为值，存储用户签到信息
     *
     *  1. 获取用户信息
     *  2. 获取当前时间，并封装键
     *  3. 设置键对应的值
     * @return 标准返回
     */
    @Override
    public Result sign() {
        //1. 获取用户信息
        Long userId = UserHolder.getUser().getId();
        //2. 获取当前时间，并封装键
        LocalDate timeNow = LocalDate.now();
        String keySuffix = timeNow.format(DateTimeFormatter.ofPattern(":yyyy-MM"));
        String key = USER_SIGN_KEY + userId + keySuffix;

        //3. 设置键对应的值  dayOfMonth是从1到31
        int dayOfMonth = timeNow.getDayOfMonth() - 1;
        Boolean isSign = stringRedisTemplate.opsForValue().setBit(key, dayOfMonth, true);
        if (BooleanUtil.isTrue(isSign)) {
            return Result.ok("签到成功！");
        }

        return Result.ok("签到失败！");
    }


    /**
     * 得到用户本月截止到今天的连续签到数
     * @return 返回连续签到天数
     */
    @Override
    public Result getSignConDays() {
        //1. 拼接键值
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyy-MM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //2. 获取截止当前天数的所有签到情况,是一个十进制的数字
        // bitfiled ke get u[dayOfMonth] 0
        int dayOfMonth = now.getDayOfMonth();
        List<Long> res = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0));
        if (res == null || res.isEmpty()) {
            //没有任何签到天数
            return Result.ok(0);
        }
        Long number = res.get(0);
        if (number == null || number == 0) {
            return Result.ok(0);
        }
        log.info("数字：{}", number);

        //3. 循环遍历
        int count = 0;
        //4. 与1做与运算，得到是数字最后一个bit位
        //4.1 判断是否是0 ，是0 ，则未签到，直接结束
        while ((number & 1) != 0) {
            //4.2 不是0， 签到天数加一，将数字右移一位，继续循环
            count++;
            number = number >>> 1;
        }

        return Result.ok(count);
    }


    /**
     *  根据用户输入手机号 创建用户，并返回创建好的用户对象
     * @param phone 用户输入手机号
     * @return 返回创建好的用户对象
     */
    private User CreateUserByPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + 1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        // 直接使用save方法 保存对象
        save(user);
        return user;
    }
}
