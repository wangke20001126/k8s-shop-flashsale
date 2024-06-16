//package cn.wolfcode.canal;
//
//import cn.wolfcode.domain.OrderInfo;
//import cn.wolfcode.redis.SeckillRedisKey;
//import com.alibaba.fastjson.JSON;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.stereotype.Component;
//import top.javatool.canal.client.annotation.CanalTable;
//import top.javatool.canal.client.handler.EntryHandler;
//
//@Slf4j
//@Component
//@CanalTable(value = "t_order_info")
//public class OrderInfoHandler implements EntryHandler<OrderInfo> {
//
//    @Autowired
//    private StringRedisTemplate redisTemplate;
//
//    @Override
//    public void insert(OrderInfo orderInfo) {
//        log.info("[订单数据监听] 创建了新的订单，准备缓存:{}", JSON.toJSONString(orderInfo));
//        String realKey = SeckillRedisKey.SECKILL_ORDER_HASH.getRealKey(orderInfo.getUserId() + "");
//        redisTemplate.opsForHash().put(realKey, orderInfo.getOrderNo(), JSON.toJSONString(orderInfo));
//    }
//
//    @Override
//    public void update(OrderInfo before, OrderInfo after) {
//        log.info("[订单数据监听] 修改了订单：");
//        log.info("before:{}", JSON.toJSONString(before));
//        String json = JSON.toJSONString(after);
//        log.info("after:{}", json);
//
//        String realKey = SeckillRedisKey.SECKILL_ORDER_HASH.getRealKey(after.getUserId() + "");
//        redisTemplate.opsForHash().put(realKey, after.getOrderNo(), json);
//    }
//
//    @Override
//    public void delete(OrderInfo orderInfo) {
//        log.info("[订单数据监听] 有订单删除了，准备删除缓存: {}", JSON.toJSONString(orderInfo));
//        String realKey = SeckillRedisKey.SECKILL_ORDER_HASH.getRealKey(orderInfo.getUserId() + "");
//        redisTemplate.delete(realKey);
//    }
//}
