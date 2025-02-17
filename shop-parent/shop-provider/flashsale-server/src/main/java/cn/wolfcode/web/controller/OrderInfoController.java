package cn.wolfcode.web.controller;

import cn.wolfcode.common.constants.CommonConstants;
import cn.wolfcode.common.domain.UserInfo;
import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.CommonCodeMsg;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.common.web.anno.RequireLogin;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.OrderMessage;
import cn.wolfcode.mq.callback.DefaultSendCallback;
import cn.wolfcode.redis.CommonRedisKey;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@RestController
@RequestMapping("/order")
@Slf4j
public class OrderInfoController {
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private IOrderInfoService orderInfoService;

    /**
     * 本地库存售完标记 Map
     * 对应的秒杀如果在该 Map 中有值，且为 true 就表示这个秒杀已经没有库存了
     */
    public static final Map<Long, Boolean> LOCAL_STOCK_OVER_FALG_MAP = new ConcurrentHashMap<>();

    @RequireLogin
    @PostMapping("/doSeckill")
    public Result<String> doSeckill(Long seckillId, Integer time, @RequestHeader(CommonConstants.TOKEN_NAME) String token) {
        // 1. 检查用户是否登录，获取登录用户信息
        UserInfo userInfo = this.getByToken(token);
        if (userInfo == null) {
            log.warn("[秒杀功能] 当前用户未登录：token={}", token);
            throw new BusinessException(CommonCodeMsg.TOKEN_INVALID);
        }
        // 2. 基于秒杀商品 id 查询秒杀商品对象
        SeckillProductVo vo = seckillProductService.findByIdInCache(seckillId, time);
        // 3. 当前时间是否处于秒杀活动时间范围内
        if (!validTime(vo.getStartDate(), vo.getTime())) {
            log.warn("[秒杀功能] 访问了不在活动范围内的秒杀商品：seckillId={}, startDate={}, time={}", seckillId, vo.getStartDate(), vo.getTime());
            throw new BusinessException(SeckillCodeMsg.INVALID_TIME_ERROR);
        }
        // 检查本地标识是否已经卖完
        Boolean flag = LOCAL_STOCK_OVER_FALG_MAP.get(seckillId);
        if (flag != null && flag) {
            // 如果有值且为 true，说明已经没有库存了，直接返回没有库存
            log.warn("[秒杀功能] 本地标识秒杀商品已售完：seckillId={}", seckillId);
            throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
        }

        // 5. 查询当前用户是否已经下过单
        // 往redis 利用 setnx 命令直接存储当前用户抢购标识，如果已经存在，就直接提示用户已经下单
        // 唯一 key：seckill:orders:user:{seckillId}, hashKey={userId}
        Boolean absent = redisTemplate.opsForHash().putIfAbsent(SeckillRedisKey.SECKILL_ORDER_USER_RECORDS_HASH.getRealKey(seckillId + ""),
                userInfo.getPhone() + "", "1");
        if (!absent) {
            log.warn("[秒杀功能] 当前用户已经下过订单：seckillId={}, userId={}", seckillId, userInfo.getPhone());
            throw new BusinessException(SeckillCodeMsg.REPEAT_SECKILL);
        }

        // 4. 库存预减，判断库存是否足够 库存 < 0 === 库存不足
        Long remainStockCount = redisTemplate.opsForHash().increment(SeckillRedisKey.SECKILL_STOCK_COUNT_HASH.getRealKey(time + ""),
                seckillId + "", -1);
        if (remainStockCount < 0) {
            log.warn("[秒杀功能] Redis 库存预减库存不足：seckillId={}, remainStockCount={}", seckillId, remainStockCount);
            // 标识该秒杀商品已经卖完了
            LOCAL_STOCK_OVER_FALG_MAP.put(seckillId, true);
            // 删除用户重复下单标记
            redisTemplate.opsForHash().delete(SeckillRedisKey.SECKILL_ORDER_USER_RECORDS_HASH.getRealKey(seckillId + ""), userInfo.getPhone() + "");
            throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
        }

        // 6. 进行下单操作(库存数量 -1, 创建秒杀订单)
        // return Result.success(orderInfoService.createOrder(userInfo.getPhone(), seckillId));
        OrderMessage message = new OrderMessage(time, seckillId, token, userInfo.getPhone());

        rocketMQTemplate.asyncSend(MQConstant.ORDER_PENDING_DEST, message, new DefaultSendCallback("创建订单", message));
        return Result.success("正在创建订单，请稍后...");
    }

    private boolean validTime(Date startDate, Integer time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startDate); // 设置活动的开始日期
        calendar.set(Calendar.HOUR_OF_DAY, time); // 设置当前场次的小时数
        calendar.set(Calendar.MINUTE, 0); // 分/秒清零
        calendar.set(Calendar.SECOND, 0);

        Date startTime = calendar.getTime(); // 开始时间

        calendar.add(Calendar.HOUR_OF_DAY, 1); // 基于开始时间 +1小时 == 结束时间
        Date endTime = calendar.getTime(); // 结束时间

        long now = System.currentTimeMillis(); // 当前时间进行判断

        // 开始时间 <= 当前时间 < 结束时间
        return startTime.getTime() <= now && endTime.getTime() > now;
//        return true;
    }

    private UserInfo getByToken(String token) {
        String json = redisTemplate.opsForValue().get(CommonRedisKey.USER_TOKEN.getRealKey(token));
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        return JSON.parseObject(json, UserInfo.class);
    }

    @GetMapping("/find")
    public Result<OrderInfo> getById(String orderNo, @RequestHeader(CommonConstants.TOKEN_NAME) String token) {
        UserInfo userInfo = this.getByToken(token);
        if (userInfo == null) {
            return Result.error(CommonCodeMsg.TOKEN_INVALID);
        }
        return Result.success(orderInfoService.getById(orderNo, userInfo.getPhone()));
    }
}
