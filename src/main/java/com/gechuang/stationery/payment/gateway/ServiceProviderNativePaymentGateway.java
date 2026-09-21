package com.gechuang.stationery.payment.gateway;

import com.gechuang.stationery.common.BusinessException;
import com.gechuang.stationery.common.ErrorCode;
import com.gechuang.stationery.payment.WechatPayProperties;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.partnerpayments.model.TransactionAmount;
import com.wechat.pay.java.service.partnerpayments.nativepay.NativePayService;
import com.wechat.pay.java.service.partnerpayments.nativepay.model.Amount;
import com.wechat.pay.java.service.partnerpayments.nativepay.model.CloseOrderRequest;
import com.wechat.pay.java.service.partnerpayments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.partnerpayments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.partnerpayments.nativepay.model.QueryOrderByOutTradeNoRequest;
import com.wechat.pay.java.service.partnerpayments.nativepay.model.SceneInfo;
import com.wechat.pay.java.service.partnerpayments.nativepay.model.StoreInfo;
import com.wechat.pay.java.service.partnerpayments.nativepay.model.Transaction;
import java.math.BigDecimal;

public class ServiceProviderNativePaymentGateway implements WechatPaymentGateway {

    private static final BigDecimal FEN_PER_YUAN = new BigDecimal("100");

    private final NativePayService nativePayService;
    private final NotificationParser notificationParser;
    private final WechatPayProperties properties;

    public ServiceProviderNativePaymentGateway(NativePayService nativePayService,
                                               NotificationParser notificationParser,
                                               WechatPayProperties properties) {
        this.nativePayService = nativePayService;
        this.notificationParser = notificationParser;
        this.properties = properties;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public NativeOrder createNativeOrder(CreateNativeOrder command) {
        PrepayRequest request = new PrepayRequest();
        request.setSpAppid(properties.getWechat().getSpAppId());
        request.setSpMchid(properties.getWechat().getSpMchid());
        request.setSubMchid(command.subMchid());
        request.setDescription(command.description());
        request.setOutTradeNo(command.outTradeNo());
        request.setNotifyUrl(properties.getWechat().getNotifyUrl());

        Amount amount = new Amount();
        amount.setTotal(toFen(command.amount()));
        amount.setCurrency("CNY");
        request.setAmount(amount);
        request.setSceneInfo(sceneInfo(command));

        PrepayResponse response = nativePayService.prepay(request);
        return new NativeOrder(response.getCodeUrl());
    }

    @Override
    public GatewayPayment queryByOutTradeNo(String outTradeNo, String subMchid) {
        QueryOrderByOutTradeNoRequest request = new QueryOrderByOutTradeNoRequest();
        request.setOutTradeNo(outTradeNo);
        request.setSpMchid(properties.getWechat().getSpMchid());
        request.setSubMchid(subMchid);
        return payment(nativePayService.queryOrderByOutTradeNo(request));
    }

    @Override
    public void closeOrder(String outTradeNo, String subMchid) {
        CloseOrderRequest request = new CloseOrderRequest();
        request.setOutTradeNo(outTradeNo);
        request.setSpMchid(properties.getWechat().getSpMchid());
        request.setSubMchid(subMchid);
        nativePayService.closeOrder(request);
    }

    @Override
    public GatewayPayment parseNotification(WechatNotification notification) {
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(notification.serialNumber())
                .signature(notification.signature())
                .timestamp(notification.timestamp())
                .nonce(notification.nonce())
                .body(notification.rawBody())
                .build();
        return payment(notificationParser.parse(requestParam, Transaction.class));
    }

    private SceneInfo sceneInfo(CreateNativeOrder command) {
        StoreInfo storeInfo = new StoreInfo();
        storeInfo.setId(command.storeId());
        storeInfo.setName(command.storeName());
        storeInfo.setAreaCode(command.storeAreaCode());
        storeInfo.setAddress(command.storeAddress());

        SceneInfo sceneInfo = new SceneInfo();
        sceneInfo.setPayerClientIp(command.payerClientIp());
        sceneInfo.setStoreInfo(storeInfo);
        return sceneInfo;
    }

    private int toFen(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "支付金额必须大于 0 元");
        }
        try {
            return amount.multiply(FEN_PER_YUAN).intValueExact();
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "支付金额必须精确到分且不能超出范围");
        }
    }

    private GatewayPayment payment(Transaction transaction) {
        TransactionAmount transactionAmount = transaction.getAmount();
        BigDecimal amount = transactionAmount == null || transactionAmount.getTotal() == null
                ? null
                : BigDecimal.valueOf(transactionAmount.getTotal(), 2);
        return new GatewayPayment(
                transaction.getOutTradeNo(),
                transaction.getTransactionId(),
                transaction.getSpAppid(),
                transaction.getSpMchid(),
                transaction.getSubMchid(),
                transaction.getTradeState() == null ? null : transaction.getTradeState().name(),
                transaction.getTradeStateDesc(),
                amount,
                transaction.getSuccessTime());
    }
}
