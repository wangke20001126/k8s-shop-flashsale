package cn.wolfcode.service;


import cn.wolfcode.domain.OperateIntergralVo;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.PayLog;

/**
 * Created by wolfcode
 */
public interface IOrderInfoService {

    OrderInfo getById(String id);

    OrderInfo getById(String id, Long userId);

    OrderInfo getByUserIdAndSeckillId(Long phone, Long seckillId);

    String createOrder(Long userId, Long seckillId);

    void checkOrderTimeout(String orderNo, Long seckillId);

    int paySuccess(String orderNo, int payType);

    void insertPayLog(PayLog log);

    void refund(String orderNo);

    void integralPaySuccess(OrderInfo orderInfo);

    void integralPrepay(OperateIntergralVo vo);
}
