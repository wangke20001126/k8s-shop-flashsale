package cn.wolfcode.mq.listener;

import cn.wolfcode.domain.OperateIntergralVo;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.service.IOrderInfoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;

@Slf4j
@RocketMQTransactionListener(txProducerGroup = "tx_group")
public class IntegralPayTxTransactionListener implements RocketMQLocalTransactionListener {

    @Autowired
    private IOrderInfoService orderInfoService;

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        String orderNo = (String) arg;
        OrderInfo orderInfo = orderInfoService.getById(orderNo);
        log.info("[事务消息] 本地事务监听器准备执行本地事务：{}", orderNo);
        if (!orderInfo.getStatus().equals(OrderInfo.STATUS_ARREARAGE)) {
            log.warn("[事务消息] 本地事务执行失败，订单状态异常：{}", orderInfo);
            // 如果不是未支付，不能继续支付，需要回滚消息
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        try {
            // 订单积分支付
            orderInfoService.integralPaySuccess(orderInfo);
            log.info("[事务消息] 本地事务执行成功：{}", orderNo);
        } catch (Exception e) {
            log.error("[事务消息] 本地事务执行失败", e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }

        // 本地事务执行成功
        return RocketMQLocalTransactionState.COMMIT;
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        OperateIntergralVo vo = (OperateIntergralVo) msg.getPayload();
        OrderInfo orderInfo = orderInfoService.getById(vo.getPk());

        log.info("[事务消息] 本地事务检查订单状态：{}", orderInfo);
        if (orderInfo != null && orderInfo.getStatus().equals(OrderInfo.STATUS_ACCOUNT_PAID)
                && OrderInfo.PAYTYPE_INTERGRAL == orderInfo.getPayType()) {
            log.info("[事务消息] 本地事务执行成功，提交事务消息......");
            return RocketMQLocalTransactionState.COMMIT;
        }

        log.warn("[事务消息] 本地事务执行失败，回滚事务消息......");
        return RocketMQLocalTransactionState.ROLLBACK;
    }
}
