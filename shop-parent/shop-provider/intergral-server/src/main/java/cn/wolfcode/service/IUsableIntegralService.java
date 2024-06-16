package cn.wolfcode.service;

import cn.wolfcode.domain.OperateIntergralVo;
import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;

/* 开启 TCC */
@LocalTCC
public interface IUsableIntegralService {

    @TwoPhaseBusinessAction(name = "decrIntegral",
            commitMethod = "confirmDecrIntegral", rollbackMethod = "cancelDecrIntegral")
    void tryDecrIntegral(@BusinessActionContextParameter(paramName = "vo") OperateIntergralVo vo,
                         BusinessActionContext context);

    void confirmDecrIntegral(BusinessActionContext context);

    void cancelDecrIntegral(BusinessActionContext context);
}
