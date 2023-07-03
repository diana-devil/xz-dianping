package com.xzdp.utils;

import cn.hutool.json.JSONUtil;

import java.util.*;


//tool 类型转换工具类
//  封装一些常用的类型转换类，二次封装
public class TypeCon {

    /**
     *
     *  将List<String>转换为List<Bean>
     * @param stringList List<String>
     * @param beanClass Bean.class
     * @param <T> <Bean> 泛型
     * @return
     */
    public static <T> List<T> listToBean(List<String> stringList, Class<T> beanClass) {
        List<T> beanList = new ArrayList<>();
        for (String s : stringList) {
            //将String类型转换为 对应的Bean类型
            T bean = JSONUtil.toBean(s, beanClass);
            beanList.add(bean);
        }
        return beanList;
    }


    /**
     * 将List<Bean>转换为List<String>
     * @param beanList  List<Bean>
     * @param <E> <Bean> 泛型
     * @return
     */
    public static <E> List<String> beanToList(List<E> beanList) {
        List<String> stringList = new ArrayList<>();
        for (E bean : beanList) {
            String s = JSONUtil.toJsonStr(bean);
            stringList.add(s);
        }
        return stringList;
    }


    /**
     * 将List<Bean>转换为List<String> 根据sort排序
     *  ShopType实体类内部要实现 Comparable<>接口
     *  使用优先级队列实现
     * @param beanList  List<Bean>
     * @param <E> <Bean> 泛型元素，用E，
     * @return
     */
    public static <E extends Comparable<E>> List<String> beanToListBySort(List<E> beanList ) {
        //建立优先级队列，利用传入的beanList
        // key 传入的 List 集合需要实现 Comparable<>接口, 它里面的对象需要能够比较才行
        PriorityQueue<E> queue = new PriorityQueue<>(beanList);
        List<String> stringList = new ArrayList<>();
        // key 循环的时候不要用队列的大小作为条件，因为队列的size一直在变 ， 切记！！！
        for (int i = 0; i < beanList.size(); i++) {
            String s = JSONUtil.toJsonStr(queue.poll());
            stringList.add(s);
        }
        return stringList;
    }


}