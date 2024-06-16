package cn.wolfcode.web.controller;


import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.CodeMsg;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.OperateIntergralVo;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.PayLog;
import cn.wolfcode.domain.PayVo;
import cn.wolfcode.feign.AlipayFeignApi;
import cn.wolfcode.feign.IntegralFeignApi;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


@Slf4j
@RestController
@RequestMapping("/orderPay")
@RefreshScope
public class OrderPayController {
    @Autowired
    private IOrderInfoService orderInfoService;
    @Autowired
    private AlipayFeignApi alipayFeignApi;
    @Autowired
    private IntegralFeignApi integralFeignApi;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Value("${pay.returnUrl}")
    private String returnUrl;
    @Value("${pay.notifyUrl}")
    private String notifyUrl;

    /**
     * 作用：提供给支付宝调用，接收到支付宝请求的参数后，进行签名验证，确认参数正常，然后获取到对应参数以后，返回对应的页面给前端用户
     */
    @GetMapping("/return_url")
    public void returnUrl(@RequestParam HashMap<String, String> params, HttpServletResponse resp) {
//        HashMap<String, String> params = this.resolveParams(req);
        log.info("[订单支付] 收到支付宝同步回调请求：{}", params);

        // 远程调用支付服务验证签名
        Result<Boolean> result = alipayFeignApi.checkSignature(params);
        if (result.hasError()) {
            throw new BusinessException(new CodeMsg(result.getCode(), result.getMsg()));
        }

        try {
            boolean signVerified = result.getData();
            if (signVerified) {
                //商户订单号
                String out_trade_no = params.get("out_trade_no");

                // 验证签名成功
                resp.sendRedirect("http://localhost/order_detail.html?orderNo=" + out_trade_no);
                return;
            }

            // 验证签名失败
            resp.sendRedirect("https://www.wolfcode.cn");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private HashMap<String, String> resolveParams(HttpServletRequest request) {
        HashMap<String, String> params = null;
        params = new HashMap<String, String>();
        Map<String, String[]> requestParams = request.getParameterMap();
        for (Iterator<String> iter = requestParams.keySet().iterator(); iter.hasNext(); ) {
            String name = (String) iter.next();
            String[] values = (String[]) requestParams.get(name);
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i]
                        : valueStr + values[i] + ",";
            }
            //乱码解决，这段代码在出现乱码时使用
//            valueStr = new String(valueStr.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
            params.put(name, valueStr);
        }
        return params;
    }

    @PostMapping("/notify_url")
    public String notifyUrl(@RequestParam HashMap<String, String> params) {
//        HashMap<String, String> params = this.resolveParams(req);
        log.info("[订单支付] 收到支付宝异步回调请求：{}", params);

        // 远程调用支付服务验证签名
        Result<Boolean> result = alipayFeignApi.checkSignature(params);
        if (result.hasError()) {
            throw new BusinessException(new CodeMsg(result.getCode(), result.getMsg()));
        }

        boolean signVerified = result.getData();

        /* 实际验证过程建议商户务必添加以下校验：
            1、需要验证该通知数据中的out_trade_no是否为商户系统中创建的订单号，
            2、判断total_amount是否确实为该订单的实际金额（即商户订单创建时的金额），
            3、校验通知中的seller_id（或者seller_email) 是否为out_trade_no这笔单据的对应的操作方（有的时候，一个商户可能有多个seller_id/seller_email）
            4、验证app_id是否为该商户本身。
        */
        if (signVerified) {//验证成功
            //商户订单号
            String outTradeNo = params.get("out_trade_no");
            String totalAmount = params.get("total_amount");
            String notifyTime = params.get("notify_time");

            //支付宝交易号
            String tradeNo = params.get("trade_no");

            OrderInfo orderInfo = orderInfoService.getById(outTradeNo);
            if (orderInfo == null) {
                log.error("[订单支付] 订单编号错误，无法查询到对应的订单信息 tradeNo={}, orderNo={}, totalAmount={}", tradeNo, outTradeNo, totalAmount);
                return "fail";
            }

            if (!orderInfo.getSeckillPrice().toString().equals(totalAmount)) {
                log.error("[订单支付] 订单支付金额有误 tradeNo={}, orderNo={}, totalAmount={}", tradeNo, outTradeNo, totalAmount);
                return "fail";
            }

            //交易状态
            String trade_status = params.get("trade_status");

            if (trade_status.equals("TRADE_FINISHED")) {
                log.info("[订单支付] 处理订单已完成，更新订单状态，不可退款");
            } else if (trade_status.equals("TRADE_SUCCESS")) {
                // 更新订单状态为已支付
                // 更新订单支付时间、支付类型
                orderInfoService.paySuccess(outTradeNo, OrderInfo.PAYTYPE_ONLINE);

                // 增加支付记录
                PayLog log = new PayLog();
                log.setPayType(OrderInfo.PAYTYPE_ONLINE);
                log.setNotifyTime(notifyTime);
                log.setTradeNo(tradeNo);
                log.setOutTradeNo(outTradeNo);
                log.setTotalAmount(totalAmount);
                orderInfoService.insertPayLog(log);
            }

            return "success";
        }

        log.error("[订单支付] 异步回调签名验证失败.....");
        return "fail";
    }

    @GetMapping("/refund")
    public Result<String> refund(String orderNo) {
        orderInfoService.refund(orderNo);
        return Result.success("退款成功");
    }

    /**
     * 买家账号：eymsxt9454@sandbox.com
     */
    @GetMapping("/alipay")
    public Result<String> prepay(String orderNo, Integer type) {
        // 查询订单
        OrderInfo orderInfo = orderInfoService.getById(orderNo);
        if (orderInfo == null) {
            throw new BusinessException(SeckillCodeMsg.ORDER_NOT_EXISTS_ERROR);
        }

        if (!OrderInfo.STATUS_ARREARAGE.equals(orderInfo.getStatus())) {
            throw new BusinessException(SeckillCodeMsg.ORDER_STATUS_EXCEPTION);
        }

        if (OrderInfo.PAYTYPE_ONLINE == type) {
            // 支付宝支付
            // 构建支付对象
            PayVo vo = this.buildPayRequest(orderInfo);
            // 远程调用支付服务发起预支付请求
            return alipayFeignApi.prepay(vo);
        }

        // 积分支付
        /*OperateIntergralVo vo = this.buildIntegralRequest(orderInfo);
        // 远程调用积分服务进行支付
        Result<String> result = integralFeignApi.pay(vo);
        if (result.hasError()) {
            return result;
        }*/

        OperateIntergralVo vo = this.buildIntegralRequest(orderInfo);
        orderInfoService.integralPrepay(vo);

        /*OperateIntergralVo vo = this.buildIntegralRequest(orderInfo);
        // 基于 RocketMQ 事务消息
        Message<OperateIntergralVo> message = MessageBuilder.withPayload(vo).build();
        TransactionSendResult sendResult = rocketMQTemplate.sendMessageInTransaction(
                "tx_group", "tx_topic3", message, orderNo);

        String sendStatus = sendResult.getSendStatus().name();
        String localTXState = sendResult.getLocalTransactionState().name();
        log.info(">>>> send status={}, localTransactionState={} <<<<", sendStatus, localTXState);*/
        return Result.success("积分支付成功");
    }

    private OperateIntergralVo buildIntegralRequest(OrderInfo orderInfo) {
        OperateIntergralVo vo = new OperateIntergralVo();
        vo.setInfo("秒杀订单积分支付：" + orderInfo.getProductName()); // 描述信息
        vo.setPk(orderInfo.getOrderNo()); // 为哪个订单扣除积分
        vo.setUserId(orderInfo.getUserId()); // 从哪个账户中扣除积分
        vo.setValue(orderInfo.getIntergral()); // 本次要扣除的积分
        return vo;
    }

    private PayVo buildPayRequest(OrderInfo orderInfo) {
        PayVo vo = new PayVo();
        vo.setBody("秒杀活动 " + orderInfo.getSeckillTime() + " 场次成功秒杀1个商品");
        vo.setSubject(orderInfo.getProductName());
        vo.setNotifyUrl(notifyUrl);
        vo.setReturnUrl(returnUrl);
        vo.setTotalAmount(orderInfo.getSeckillPrice().toString());
        vo.setOutTradeNo(orderInfo.getOrderNo());
        return vo;
    }
}
