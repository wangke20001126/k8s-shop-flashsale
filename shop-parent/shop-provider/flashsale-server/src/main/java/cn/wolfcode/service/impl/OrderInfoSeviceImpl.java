package cn.wolfcode.service.impl;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.CodeMsg;
import cn.wolfcode.common.web.CommonCodeMsg;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.*;
import cn.wolfcode.feign.AlipayFeignApi;
import cn.wolfcode.feign.IntegralFeignApi;
import cn.wolfcode.feign.ProductFeignApi;
import cn.wolfcode.mapper.OrderInfoMapper;
import cn.wolfcode.mapper.PayLogMapper;
import cn.wolfcode.mapper.RefundLogMapper;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.callback.DefaultSendCallback;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.util.IdGenerateUtil;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import com.alibaba.fastjson.JSON;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Created by wolfcode
 */
@Slf4j
@Service
public class OrderInfoSeviceImpl implements IOrderInfoService {
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private PayLogMapper payLogMapper;
    @Autowired
    private RefundLogMapper refundLogMapper;
    @Autowired
    private ProductFeignApi productFeignApi;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private AlipayFeignApi alipayFeignApi;
    @Autowired
    private IntegralFeignApi integralFeignApi;

    @Override
    public OrderInfo getById(String id) {
        log.info("查询订单信息：{}", id);
        return orderInfoMapper.find(id);
    }

    @Override
    public OrderInfo getById(String id, Long userId) {
        String realKey = SeckillRedisKey.SECKILL_ORDER_HASH.getRealKey(userId + "");
        String json = (String) redisTemplate.opsForHash().get(realKey, id);
        if (StringUtils.isEmpty(json)) {
            throw new BusinessException(CommonCodeMsg.ILLEGAL_OPERATION);
        }
        return JSON.parseObject(json, OrderInfo.class);
    }

    @Override
    public OrderInfo getByUserIdAndSeckillId(Long phone, Long seckillId) {
        return orderInfoMapper.getByUserIdAndSeckillId(phone, seckillId);
    }

    /**
     * 测试参数：
     * 线程：100
     * 次数：5000
     * 数据：
     * TPS：89/s
     * 异常比例：1.92%
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public String createOrder(Long userId, Long seckillId) {
        // 创建锁对象
        RLock lock = redissonClient.getLock("seckill:product:stock:lock:" + seckillId);
        SeckillProductVo vo = null;
        try {
            // 加锁
            lock.lock(10, TimeUnit.SECONDS);
            // 查询最新的库存情况
            vo = seckillProductService.findById(seckillId);
            if (vo.getStockCount() > 0) { // 库存大于 0 才扣库存
                // 扣除库存
                int row = seckillProductService.decrStockCount(seckillId);
                if (row <= 0) {
                    // 乐观锁成功，库存数不足
                    throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
                }
            }
            // 创建订单对象
            OrderInfo orderInfo = this.create(userId, vo);

            // 保存订单对象
            orderInfoMapper.insert(orderInfo);

            return orderInfo.getOrderNo();
        } catch (BusinessException be) {
            throw be; // 不处理继续往外抛出
        } catch (Exception e) {
            // 重新同步 redis 库存，设置本地库存售完标识为 false
            if (vo != null && vo.getStockCount() > 0) {
                this.rollbackStockCount(vo);
            }
            log.error("[创建订单] 创建订单失败：", e);

            // 继续向外抛出异常
            throw new BusinessException(SeckillCodeMsg.REPEAT_SECKILL);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    @Override
    public void checkOrderTimeout(String orderNo, Long seckillId) {
        // 1. 基于订单编号查询订单对象
        // 2. 检查订单是否已经支付，如果已支付，直接忽略
        // 3. 修改订单状态为超时取消
        int row = orderInfoMapper.updateCancelStatus(orderNo, OrderInfo.STATUS_TIMEOUT);
        if (row > 0) {
            // 4. 回滚库存（mysql/redis）
            seckillProductService.incrStockCount(seckillId); // +1
            SeckillProductVo vo = seckillProductService.findById(seckillId);
            // 5. 取消库存售完标记（本地标识 JVM） => 分布式缓存同步 => 广播模式
            rollbackStockCount(vo);
        }
    }

    @Override
    public int paySuccess(String orderNo, int payType) {
        int row = orderInfoMapper.changePayStatus(orderNo, OrderInfo.STATUS_ACCOUNT_PAID, payType);
        if (row <= 0) {
            log.warn("[订单支付] 订单状态错误，当前订单状态不是未支付 orderNo={}", orderNo);
            throw new BusinessException(SeckillCodeMsg.PAY_STATUS_UPDATE_ERROR);
        }
        return row;
    }

    @Override
    public void insertPayLog(PayLog log) {
        payLogMapper.insert(log);
    }

    @Override
    public void refund(String orderNo) {
        // 1. 用户点击申请退款按钮,后台根据退款订单号查询订单消息;
        OrderInfo orderInfo = this.getById(orderNo);
        // 2. 相关参数的校验
        if (orderInfo == null || !orderInfo.getStatus().equals(OrderInfo.STATUS_ACCOUNT_PAID)) {
            throw new BusinessException(CommonCodeMsg.ILLEGAL_OPERATION);
        }
        // 3. 调用支付宝SDK的退款接口进行退款
        String reason = "RNM,退钱...";
        RefundVo vo = new RefundVo(orderNo, orderInfo.getSeckillPrice().toString(), reason);
        // 4. 根据接口同步返回的接口,判断返回 code=10000 并且里面有一个关键参数 found_change="Y" 才表示退款成功
        Result<String> result = alipayFeignApi.refund(vo);

        if (result.hasError()) {
            throw new BusinessException(SeckillCodeMsg.REFUND_ERROR);
        }

        // 5. 更新订单状态为已退款
        int row = orderInfoMapper.changeRefundStatus(orderNo, OrderInfo.STATUS_REFUND);
        if (row <= 0) {
            throw new BusinessException(SeckillCodeMsg.REFUND_ERROR);
        }
        // 6. 新增一条退款记录并保存
        RefundLog refundLog = new RefundLog();
        refundLog.setRefundReason(reason);
        refundLog.setRefundTime(new Date());
        refundLog.setRefundAmount(orderInfo.getSeckillPrice().toString());
        refundLog.setRefundType(0);
        refundLog.setOutTradeNo(orderNo);
        refundLogMapper.insert(refundLog);

        // 7. 回滚库存（mysql、redis、本地标识）
        seckillProductService.incrStockCount(orderInfo.getSeckillId());
        SeckillProductVo sp = seckillProductService.findById(orderInfo.getSeckillId());
        this.rollbackStockCount(sp);
        log.info("[订单服务] 订单退款成功 orderNo={}, refundAmount={}", orderInfo.getOrderNo(), orderInfo.getSeckillPrice());
    }

    @Override
    public void integralPaySuccess(OrderInfo orderInfo) {
        // 新增支付日志
        PayLog log = new PayLog();
        log.setPayType(OrderInfo.PAYTYPE_INTERGRAL);
        log.setNotifyTime(new Date().getTime() + "");
        // log.setTradeNo(tradeNo); 积分支付时没有交易号
        log.setOutTradeNo(orderInfo.getOrderNo());
        log.setTotalAmount(orderInfo.getIntergral() + "");
        this.insertPayLog(log);

        // 修改订单状态
        this.paySuccess(orderInfo.getOrderNo(), OrderInfo.PAYTYPE_INTERGRAL);
    }

    /* 开启全局事务 */
    @GlobalTransactional
    @Override
    public void integralPrepay(OperateIntergralVo vo) {
        // 积分支付入口
        Result<String> result = integralFeignApi.pay(vo);
        if (result.hasError()) {
            throw new BusinessException(new CodeMsg(result.getCode(), result.getMsg()));
        }

        OrderInfo orderInfo = getById(vo.getPk());
        integralPaySuccess(orderInfo); // 更新订单状态，增加支付记录
    }

    private void rollbackStockCount(SeckillProduct sp) {
        // redis 库存回补
        redisTemplate.opsForHash().put(SeckillRedisKey.SECKILL_STOCK_COUNT_HASH.getRealKey(sp.getTime() + ""),
                sp.getId() + "", sp.getStockCount() + "");
        // 取消本地标识
        rocketMQTemplate.asyncSend(MQConstant.CANCEL_SECKILL_OVER_SIGN_TOPIC, sp.getId(), new DefaultSendCallback("取消本地标识", sp.getId()));
        log.warn("[回滚库存] 订单创建失败，回补 redis 库存以及取消本地售完标记");
    }

    private OrderInfo create(Long userId, SeckillProductVo vo) {
        Date date = new Date();
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderNo(IdGenerateUtil.get().nextId() + ""); // 雪花算法生成分布式唯一 id
        orderInfo.setCreateDate(date);
        orderInfo.setIntergral(vo.getIntergral()); // 积分
        orderInfo.setDeliveryAddrId(1L); // 收货地址
        orderInfo.setProductCount(1); // 商品数量
        orderInfo.setProductId(vo.getProductId());
        orderInfo.setProductImg(vo.getProductImg());
        orderInfo.setProductName(vo.getProductName());
        orderInfo.setProductPrice(vo.getProductPrice());
        orderInfo.setSeckillDate(date);
        orderInfo.setSeckillId(vo.getId());
        orderInfo.setSeckillPrice(vo.getSeckillPrice());
        orderInfo.setSeckillTime(vo.getTime());
        orderInfo.setStatus(OrderInfo.STATUS_ARREARAGE);
        orderInfo.setUserId(userId);
        orderInfo.setPayType(-1);
        orderInfo.setPayDate(date);
        return orderInfo;
    }
}
