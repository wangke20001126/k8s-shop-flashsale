package cn.wolfcode.redis;

import lombok.Getter;

import java.util.concurrent.TimeUnit;

/**
 * Created by wolfcode
 */
@Getter
public enum SeckillRedisKey {
    INIT_SECKILL_PRODUCT_LIST_STRING("init:seckill:products:string:"),
    INIT_SECKILL_PRODUCT_DETAIL_HASH("init:seckill:products:hash:"),
    SECKILL_ORDER_USER_RECORDS_HASH("seckill:orders:user:records:hash:"),
    SECKILL_ORDER_HASH("seckill:orders:user:details:hash:"),
    SECKILL_STOCK_COUNT_HASH("seckillStockCount:"),
    SECKILL_REAL_COUNT_HASH("seckillRealCount:");
    SeckillRedisKey(String prefix, TimeUnit unit, int expireTime){
        this.prefix = prefix;
        this.unit = unit;
        this.expireTime = expireTime;
    }
    SeckillRedisKey(String prefix){
        this.prefix = prefix;
    }
    public String getRealKey(String key){
        return this.prefix+key;
    }
    private String prefix;
    private TimeUnit unit;
    private int expireTime;
}
