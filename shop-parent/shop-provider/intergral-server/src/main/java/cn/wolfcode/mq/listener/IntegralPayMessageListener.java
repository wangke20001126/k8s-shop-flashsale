package cn.wolfcode.mq.listener;

import cn.wolfcode.domain.OperateIntergralVo;
import cn.wolfcode.service.IUsableIntegralService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
        consumerGroup = "tx_integral_pay_group",
        topic = "tx_topic3"
)
public class IntegralPayMessageListener implements RocketMQListener<OperateIntergralVo> {

    @Autowired
    private IUsableIntegralService usableIntegralService;

    @Override
    public void onMessage(OperateIntergralVo vo) {
        log.info("[事务消息] 收到积分支付事务消息，正在准备扣除积分......");
        usableIntegralService.tryDecrIntegral(vo, null);
    }
}
