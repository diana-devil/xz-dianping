package com.xzdp.service;

import com.xzdp.dto.Result;
import com.xzdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

import javax.servlet.http.HttpServletRequest;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 完成秒杀优惠券抢购
     * @param voucherId 优惠券id
     * @return 标准结果
     */
    Result seckillVoucher(Long voucherId, HttpServletRequest request);

    /**
     * 同步执行 VoucherOrderServiceImpl
     * 实现 秒杀优惠券的订单实现
     * @param voucherId 优惠券id
     * @param  userId 用户id
     * @return 标准结果集
     */
    Result createVoucherOrder(Long voucherId, Long userId);

    /**
     * 异步执行 VoucherOrderServiceImpl2
     * 实现 秒杀优惠券的订单实现
     * @param voucherOrder 订单信息
     * @return 标准结果集
     */
    void createVoucherOrder(VoucherOrder voucherOrder);



}
