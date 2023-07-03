package com.xzdp.utils;

import com.xzdp.dto.UserDTO;

//ThreadLocal 方法
// 往里面存数据，取数据，删除数据
// key 使用UserDTO 可以减小存储空, 不需要User的全部数据，将需要的部分数据封装到UserDTO中
public class UserHolder {

    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
