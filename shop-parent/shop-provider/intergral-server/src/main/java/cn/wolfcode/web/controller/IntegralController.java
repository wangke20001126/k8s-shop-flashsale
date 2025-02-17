package cn.wolfcode.web.controller;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.OperateIntergralVo;
import cn.wolfcode.service.IUsableIntegralService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/integral")
public class IntegralController {
    @Autowired
    private IUsableIntegralService usableIntegralService;

    @PostMapping("/pay")
    public Result<String> pay(@RequestBody OperateIntergralVo vo) {
        usableIntegralService.tryDecrIntegral(vo, null);
        return Result.success("积分支付成功");
    }
}
