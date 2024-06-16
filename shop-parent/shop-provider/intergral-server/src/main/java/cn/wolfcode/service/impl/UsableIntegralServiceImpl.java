package cn.wolfcode.service.impl;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.domain.AccountLog;
import cn.wolfcode.domain.AccountTransaction;
import cn.wolfcode.domain.OperateIntergralVo;
import cn.wolfcode.mapper.AccountLogMapper;
import cn.wolfcode.mapper.AccountTransactionMapper;
import cn.wolfcode.mapper.UsableIntegralMapper;
import cn.wolfcode.service.IUsableIntegralService;
import cn.wolfcode.web.msg.IntergralCodeMsg;
import com.alibaba.fastjson.JSONObject;
import io.seata.rm.tcc.api.BusinessActionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

import static cn.wolfcode.domain.AccountTransaction.*;


@Slf4j
@Service
public class UsableIntegralServiceImpl implements IUsableIntegralService {
    @Autowired
    private UsableIntegralMapper usableIntegralMapper;
    @Autowired
    private AccountTransactionMapper accountTransactionMapper;
    @Autowired
    private AccountLogMapper accountLogMapper;

    private void insertAccountTransactionRecord(OperateIntergralVo vo, int type, int state, String txId, Long branchId) {
        AccountTransaction transaction = new AccountTransaction();
        transaction.setAmount(vo.getValue());
        transaction.setType(type + "");
        transaction.setUserId(vo.getUserId());
        transaction.setState(state);
        Date date = new Date();
        transaction.setGmtCreated(date);
        transaction.setGmtModified(date);
        transaction.setTxId(txId);
        transaction.setActionId(branchId + "");

        accountTransactionMapper.insert(transaction);
    }

    @Transactional
    @Override
    public void tryDecrIntegral(OperateIntergralVo vo, BusinessActionContext context) {
        log.info("[TCC事务] 预留积分资源：xid={}, branchId={}, ctx={}", context.getXid(), context.getBranchId(), context.getActionContext());
        // 插入事务记录
        this.insertAccountTransactionRecord(vo, 0, STATE_TRY, context.getXid(), context.getBranchId());

        // TCC 的第一阶段：尝试预留积分资源
        int row = usableIntegralMapper.freezeIntergral(vo.getUserId(), vo.getValue());
        if (row <= 0) {
            log.warn("[TCC事务] 预留资源失败，账户余额不足...");
            throw new BusinessException(IntergralCodeMsg.INTERGRAL_NOT_ENOUGH);
        }
    }

    @Override
    public void confirmDecrIntegral(BusinessActionContext context) {
        log.info("[TCC事务] 确认事务提交：xid={}, branchId={}, ctx={}", context.getXid(), context.getBranchId(), context.getActionContext());

        // 1. 查询事务记录，判断是否为空，为空则直接抛出异常
        AccountTransaction transaction = accountTransactionMapper.get(context.getXid(), context.getBranchId() + "");

        // 2. 判断状态是否是已回滚，如果是已回滚，抛出异常
        if (transaction == null || transaction.getState() == STATE_CANCEL) {
            log.warn("[TCC事务] 事务状态异常，事务记录为空或状态不正确：{}", transaction);
            throw new BusinessException(IntergralCodeMsg.OP_INTERGRAL_ERROR);
        }

        // 3. 不为空，判断是否是已提交，如果是已提交直接返回
        if (transaction.getState() == STATE_COMMIT) {
            log.warn("[TCC事务] 事务状态异常，重复提交执行幂等处理：{}", transaction);
            return;
        }

        // 4. 如果既不是提交也不是回滚，说明是初始化，就继续执行业务
        // 修改事务状态为已提交
        int row = accountTransactionMapper.updateAccountTransactionState(context.getXid(), context.getBranchId() + "",
                STATE_COMMIT, STATE_TRY);
        if (row <= 0) {
            // 如果状态修改失败，就不允许执行提交的业务逻辑
            throw new BusinessException(IntergralCodeMsg.OP_INTERGRAL_ERROR);
        }

        JSONObject json = (JSONObject) context.getActionContext("vo");
        OperateIntergralVo vo = json.toJavaObject(OperateIntergralVo.class);

        // 真实扣除积分，取消冻结金额
        usableIntegralMapper.commitChange(vo.getUserId(), vo.getValue());
        // 保存账户变动日志记录
        AccountLog account = new AccountLog();
        account.setType(0);
        account.setInfo(vo.getInfo());
        account.setPkValue(vo.getPk());
        account.setAmount(vo.getValue());
        account.setGmtTime(new Date());
        accountLogMapper.insert(account);
        log.info("[TCC事务] 事务提交成功，记录操作日志：{}", account);
    }

    @Override
    public void cancelDecrIntegral(BusinessActionContext context) {
        log.warn("[TCC事务] 一阶段执行失败，准备回滚积分：xid={}, branchId={}, ctx={}", context.getXid(), context.getBranchId(), context.getActionContext());
        JSONObject json = (JSONObject) context.getActionContext("vo");
        OperateIntergralVo vo = json.toJavaObject(OperateIntergralVo.class);

        // 1. 查询事务记录
        AccountTransaction transaction = accountTransactionMapper.get(context.getXid(), context.getBranchId() + "");
        // 2. 如果事务记录为空（空回滚）
        if (transaction == null) {
            // 2.1 插入事务记录，如果插入失败直接抛异常
            this.insertAccountTransactionRecord(vo, 0, STATE_CANCEL, context.getXid(), context.getBranchId());
            // 2.2 插入成功，执行幂等处理，直接结束方法
            return;
        }

        // 3. 如果事务记录不为空（正常回滚）
        // 3.1 判断状态是否是已回滚，如果是已回滚，执行幂等直接结束方法
        if (transaction.getState() == STATE_CANCEL) {
            return;
        }
        // 3.2 如果状态是已提交，抛出异常
        if (transaction.getState() == STATE_COMMIT) {
            throw new BusinessException(IntergralCodeMsg.OP_INTERGRAL_ERROR);
        }
        // 3.3 如果都不是，说明状态为初始化，属于正常状态，继续执行业务逻辑
        // 3.4 更新事务记录状态为已回滚，修改失败则抛出异常
        int row = accountTransactionMapper.updateAccountTransactionState(context.getXid(), context.getBranchId() + "",
                STATE_CANCEL, STATE_TRY);
        if (row <= 0) {
            throw new BusinessException(IntergralCodeMsg.OP_INTERGRAL_ERROR);
        }
        usableIntegralMapper.unFreezeIntergral(vo.getUserId(), vo.getValue());
    }
}
