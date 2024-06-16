package cn.wolfcode.web.msg;

import cn.wolfcode.common.web.CodeMsg;

/**
 * Created by wolfcode
 */
public class PayCodeMsg extends CodeMsg {

    public static final PayCodeMsg ALIPAY_SIGNATURE_FAILED = new PayCodeMsg(501002, "支付宝签名失败");
    public static final CodeMsg REFUND_FAILED = new PayCodeMsg(501003, "退款失败");

    private PayCodeMsg(Integer code, String msg) {
        super(code, msg);
    }
}
