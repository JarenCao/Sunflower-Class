package com.sunflower_class.service.orders.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.sunflower_class.model.dto.AlipayTradeDto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** 使用支付宝官方SDK加签、验签与网络请求，不自行拼装签名协议。 */
@Service
public class AlipaySandboxService {

    @Value("${sunflower.alipay.gateway:https://openapi-sandbox.dl.alipaydev.com/gateway.do}")
    private String gateway;

    @Value("${sunflower.alipay.app-id:}")
    private String appId;

    @Value("${sunflower.alipay.private-key:}")
    private String privateKey;

    @Value("${sunflower.alipay.public-key:}")
    private String publicKey;

    @Value("${sunflower.alipay.seller-id:}")
    private String sellerId;

    @Value("${sunflower.alipay.notify-url:}")
    private String notifyUrl;

    private AlipayClient client;

    @Autowired
    private JsonMapper json;

    private synchronized AlipayClient client() {
        if (
            !Set.of(
                "https://openapi-sandbox.dl.alipaydev.com/gateway.do",
                "https://openapi.alipaydev.com/gateway.do"
            ).contains(gateway)
        ) throw new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "当前只允许支付宝沙箱网关"
        );
        if (
            appId.isBlank() || privateKey.isBlank() || publicKey.isBlank() || sellerId.isBlank()
        ) throw new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "支付宝沙箱配置未完成，请联系管理员"
        );
        if (client == null) try {
            AlipayConfig config = new AlipayConfig();
            config.setServerUrl(gateway);
            config.setAppId(appId);
            config.setPrivateKey(privateKey);
            config.setAlipayPublicKey(publicKey);
            config.setCharset("UTF-8");
            config.setFormat("json");
            config.setSignType("RSA2");
            config.setConnectTimeout(3000);
            config.setReadTimeout(5000);
            client = new DefaultAlipayClient(config);
        } catch (AlipayApiException invalid) {
            throw unavailable();
        }
        return client;
    }

    /** 缺少沙箱配置时不得留下已经向支付平台发起请求的渠道标记。 */
    public void requireConfigured() {
        client();
    }

    public String precreate(String payNo, String name, BigDecimal amount, long seconds) {
        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        request.setBizContent(
            json.writeValueAsString(
                Map.of(
                    "out_trade_no",
                    payNo,
                    "subject",
                    name,
                    "total_amount",
                    amount.toPlainString(),
                    "seller_id",
                    sellerId,
                    "time_expire",
                    LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
                        .plusSeconds(seconds)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                )
            )
        );
        if (!notifyUrl.isBlank()) request.setNotifyUrl(notifyUrl);
        try {
            AlipayTradePrecreateResponse response = client().execute(request);
            if (
                !response.isSuccess() ||
                response.getQrCode() == null ||
                !payNo.equals(response.getOutTradeNo())
            ) throw unavailable();
            return response.getQrCode();
        } catch (AlipayApiException error) {
            throw unavailable();
        }
    }

    public AlipayTradeDto query(String payNo) {
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizContent(json.writeValueAsString(Map.of("out_trade_no", payNo)));
        try {
            AlipayTradeQueryResponse response = client().execute(request);
            if (!response.isSuccess()) {
                if ("ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())) return new AlipayTradeDto(
                    payNo,
                    null,
                    null,
                    null,
                    "NOT_EXIST"
                );
                throw unavailable();
            }
            if (!payNo.equals(response.getOutTradeNo())) throw unavailable();
            return new AlipayTradeDto(
                payNo,
                response.getTradeNo(),
                new BigDecimal(response.getTotalAmount()),
                response.getSendPayDate() == null
                    ? null
                    : LocalDateTime.ofInstant(
                          response.getSendPayDate().toInstant(),
                          ZoneId.of("Asia/Shanghai")
                      ),
                response.getTradeStatus()
            );
        } catch (AlipayApiException | NumberFormatException error) {
            throw unavailable();
        }
    }

    public void close(String payNo) {
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
        request.setBizContent(json.writeValueAsString(Map.of("out_trade_no", payNo)));
        try {
            AlipayTradeCloseResponse response = client().execute(request);
            if (
                !response.isSuccess() &&
                !"ACQ.TRADE_NOT_EXIST".equals(response.getSubCode()) &&
                !"ACQ.TRADE_HAS_CLOSE".equals(response.getSubCode())
            ) throw unavailable();
        } catch (AlipayApiException error) {
            throw unavailable();
        }
    }

    /** 回调来自支付平台，必须核对RSA2、应用和收款账号；不能把浏览器付款按钮当成成功。 */
    public AlipayTradeDto notification(Map<String, String> params) {
        client();
        try {
            if (
                !"RSA2".equals(params.get("sign_type")) ||
                !appId.equals(params.get("app_id")) ||
                !sellerId.equals(params.get("seller_id")) ||
                !AlipaySignature.rsaCheckV1(params, publicKey, "UTF-8", "RSA2")
            ) throw new IllegalArgumentException();
            String status = params.get("trade_status");
            LocalDateTime paid = Set.of("TRADE_SUCCESS", "TRADE_FINISHED").contains(status)
                ? LocalDateTime.parse(
                      params.get("gmt_payment"),
                      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                  )
                : null;
            return new AlipayTradeDto(
                params.get("out_trade_no"),
                params.get("trade_no"),
                new BigDecimal(params.get("total_amount")),
                paid,
                status
            );
        } catch (Exception invalid) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "支付通知签名或账号校验失败");
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            "支付宝沙箱调用失败，请稍后查询订单状态"
        );
    }
}
