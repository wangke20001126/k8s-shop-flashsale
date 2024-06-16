package cn.wolfcode.feign;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.OperateIntergralVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient("intergral-service")
public interface IntegralFeignApi {

    @PostMapping("/integral/pay")
    Result<String> pay(@RequestBody OperateIntergralVo vo);
}
