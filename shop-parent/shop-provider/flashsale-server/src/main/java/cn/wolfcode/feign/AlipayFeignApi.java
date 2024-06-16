package cn.wolfcode.feign;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.PayVo;
import cn.wolfcode.domain.RefundVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.HashMap;

@FeignClient("pay-service")
public interface AlipayFeignApi {

    @PostMapping("/alipay/prepay")
    Result<String> prepay(@RequestBody PayVo vo);

    @PostMapping("/alipay/checkSignature")
    Result<Boolean> checkSignature(@RequestBody HashMap<String, String> params);

    @PostMapping("/alipay/refund")
    Result<String> refund(@RequestBody RefundVo vo);
}
