package com.xzdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xzdp.dto.Result;
import com.xzdp.entity.Voucher;
import com.xzdp.mapper.VoucherMapper;
import com.xzdp.entity.SeckillVoucher;
import com.xzdp.service.ISeckillVoucherService;
import com.xzdp.service.IVoucherService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

import static com.xzdp.utils.Constants.RedisConstants.SECKILL_STOCK_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 *
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }


    /**
     * 保存优惠券到数据库
     * 同时将优惠券信息存到Redis
     * @param voucher 优惠券
     */
    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券到数据库
        save(voucher);
        // 保存秒杀信息到数据库
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
        //保存秒杀信息到Redis
//        String json = JSONUtil.toJsonStr(seckillVoucher);
//        System.out.println(json);
//        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucher.getId(), json);

        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucher.getId(), voucher.getStock().toString());

    }
}
