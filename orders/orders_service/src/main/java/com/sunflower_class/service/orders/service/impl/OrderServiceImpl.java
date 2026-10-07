package com.sunflower_class.service.orders.service.impl;

import com.sunflower_class.base.course.CourseMessageSender;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.base.payment.CourseOrderSnapshotDto;
import com.sunflower_class.base.payment.PaymentSuccessEvent;
import com.sunflower_class.base.security.CurrentUser;
import com.sunflower_class.model.dto.AlipayTradeDto;
import com.sunflower_class.model.dto.CourseOrderDto;
import com.sunflower_class.service.orders.mapper.OrderMapper;
import com.sunflower_class.service.orders.service.AlipaySandboxService;
import com.sunflower_class.service.orders.service.OrderService;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** 原有orders三表保留历史数据，价格从学习服务已保存的选课快照读取。 */
@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JsonMapper json;

    @Autowired
    private AlipaySandboxService alipay;

    @Autowired
    private CourseMessageSender sender;

    private RestClient learning;

    @Value("${sunflower.learning.endpoint}")
    private String endpoint;

    /** 注入完成后确认独立订单库，并保留原请求超时。 */
    @PostConstruct
    public void initialize() {
        if (!"orders".equals(orderMapper.selectDatabase())) throw new IllegalStateException(
            "订单服务必须连接 orders 数据库"
        );
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        learning = RestClient.builder().baseUrl(endpoint).requestFactory(factory).build();
    }

    private String student() {
        CurrentUser.requireRole("student");
        return CurrentUser.jwt().getSubject();
    }

    private long number(String value) {
        try {
            long n = Long.parseLong(value);
            if (n <= 0) throw new NumberFormatException();
            return n;
        } catch (RuntimeException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单编号无效");
        }
    }

    private LocalDateTime time(Object value) {
        return value == null
            ? null
            : value instanceof LocalDateTime local
              ? local
              : LocalDateTime.ofInstant(((Timestamp) value).toInstant(), ZoneOffset.UTC);
    }

    /** 数据库存UTC，页面展示北京时间；不得混用JVM本地时间与数据库时间。 */
    private LocalDateTime displayTime(Object value) {
        return time(value)
            .atOffset(ZoneOffset.UTC)
            .atZoneSameInstant(ZoneId.of("Asia/Shanghai"))
            .toLocalDateTime();
    }

    private LocalDateTime now() {
        return orderMapper.selectCurrentTime();
    }

    private List<Map<String, Object>> own(String id, boolean lock) {
        return orderMapper.selectOwnedOrder(number(id), student(), lock);
    }

    private Map<String, Object> required(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        return rows.getFirst();
    }

    private CourseOrderDto dto(Map<String, Object> row) {
        boolean delivered = Boolean.TRUE.equals(
            orderMapper.isPaymentDelivered(((Number) row.get("id")).longValue())
        );
        return new CourseOrderDto(
            row.get("id").toString(),
            ((Number) row.get("course_id")).longValue(),
            row.get("order_name").toString(),
            (BigDecimal) row.get("total_price"),
            row.get("status").toString(),
            displayTime(row.get("create_date")),
            displayTime(row.get("expires_at")),
            delivered
        );
    }

    public CourseOrderDto get(String id) {
        return dto(required(own(id, false)));
    }

    public PageResult<CourseOrderDto> list(long page, long size, String status) {
        String user = student();
        if (page < 1 || page > 100000 || size < 1 || size > 100) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "分页参数无效"
        );
        // 状态筛选与分页在同一用户范围内执行，不只过滤当前页。
        if (
            status != null && !Set.of("60201", "60202", "60203", "60204", "60205").contains(status)
        ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单状态无效");
        }
        long count = orderMapper.countStudentOrders(user, status);
        List<Map<String, Object>> rows = orderMapper.selectStudentOrders(
            user,
            size,
            (page - 1) * size,
            status
        );
        return new PageResult<>(rows.stream().map(this::dto).toList(), count, page, size);
    }

    public CourseOrderDto create(long courseId) {
        String user = student();
        if (courseId <= 0) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "课程编号无效"
        );
        CourseOrderSnapshotDto snapshot;
        try {
            snapshot = learning
                .get()
                .uri("/enrollments/{id}/order-snapshot", courseId)
                .headers(h -> h.setBearerAuth(CurrentUser.jwt().getTokenValue()))
                .retrieve()
                .body(CourseOrderSnapshotDto.class);
        } catch (RestClientResponseException error) {
            throw new ResponseStatusException(
                HttpStatus.valueOf(error.getStatusCode().value()),
                "课程尚未选课、已开通或暂不可购买"
            );
        } catch (RestClientException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "学习服务暂不可用");
        }
        if (
            snapshot == null ||
            !user.equals(snapshot.getUserId()) ||
            snapshot.getCourseId() != courseId ||
            snapshot.getPrice() == null ||
            snapshot.getPrice().signum() <= 0 ||
            snapshot.getPrice().scale() > 2 ||
            snapshot.getPrice().compareTo(new BigDecimal("99999999.99")) > 0 ||
            (snapshot.getValidDays() != null && snapshot.getValidDays() < 0)
        ) throw new ResponseStatusException(HttpStatus.CONFLICT, "选课价格快照无效");
        return tx.execute(status -> {
            List<Map<String, Object>> existing = orderMapper.selectActiveOrder(user, courseId);
            if (!existing.isEmpty()) return dto(existing.getFirst());
            long id = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
            String detail = json.writeValueAsString(snapshot);
            LocalDateTime created = now();
            orderMapper.insertOrder(
                id,
                snapshot.getPrice(),
                created,
                user,
                snapshot.getCourseName(),
                "课程购买",
                detail,
                UUID.randomUUID().toString(),
                courseId,
                created.plusMinutes(30)
            );
            Map<String, Object> current = orderMapper.selectActiveOrderForUpdate(user, courseId);
            if (((Number) current.get("id")).longValue() != id) return dto(current);
            String goodsName = snapshot.getCourseName();
            if (goodsName.length() > 100) goodsName = goodsName.substring(0, 100);
            orderMapper.insertOrderGoods(
                id,
                Long.toString(courseId),
                goodsName,
                snapshot.getPrice(),
                detail
            );
            orderMapper.insertPayment(
                id,
                id,
                snapshot.getCourseName(),
                snapshot.getPrice(),
                created,
                user
            );
            return dto(orderMapper.selectOrder(id));
        });
    }

    public String pay(String id) {
        Map<String, Object> before = required(own(id, false));
        if (
            !"60201".equals(before.get("status")) || !now().isBefore(time(before.get("expires_at")))
        ) throw new ResponseStatusException(HttpStatus.CONFLICT, "订单已支付或超时，请刷新状态");
        alipay.requireConfigured();
        // 先持久化渠道，再调用支付平台；网络结果不明时仍能用稳定商户单号查询，不能丢失支付关联。
        tx.executeWithoutResult(t -> {
            Map<String, Object> row = required(own(id, true));
            if (
                !"60201".equals(row.get("status")) || !now().isBefore(time(row.get("expires_at")))
            ) throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "订单已支付或超时，请刷新状态"
            );
            orderMapper.markPaymentAttempt(((Number) row.get("id")).longValue());
        });
        return tx.execute(t -> {
            Map<String, Object> row = required(own(id, true));
            if (
                !"60201".equals(row.get("status")) || !now().isBefore(time(row.get("expires_at")))
            ) throw new ResponseStatusException(HttpStatus.CONFLICT, "订单不可支付");
            if (row.get("qr_code") != null) return row.get("qr_code").toString();
            long seconds = Duration.between(now(), time(row.get("expires_at"))).getSeconds();
            if (seconds <= 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "订单已超时");
            String qr = alipay.precreate(
                id,
                row.get("order_name").toString(),
                (BigDecimal) row.get("total_price"),
                seconds
            );
            orderMapper.updateOrderQr(qr, ((Number) row.get("id")).longValue());
            return qr;
        });
    }

    public CourseOrderDto refresh(String id) {
        String user = student();
        return tx.execute(t -> {
            Map<String, Object> row = required(own(id, true));
            refreshRow(row);
            return dto(orderMapper.selectStudentOrder(((Number) row.get("id")).longValue(), user));
        });
    }

    private void refreshRow(Map<String, Object> row) {
        if (!"60201".equals(row.get("status"))) return;
        Map<String, Object> payment = orderMapper.selectPayment(
            ((Number) row.get("id")).longValue(),
            ((Number) row.get("id")).longValue()
        );
        boolean expired = !now().isBefore(time(row.get("expires_at")));
        if (payment.get("out_pay_channel") == null) {
            if (expired) orderMapper.closeOrder(((Number) row.get("id")).longValue());
            return;
        }
        AlipayTradeDto trade = alipay.query(row.get("id").toString());
        if (Set.of("TRADE_SUCCESS", "TRADE_FINISHED").contains(trade.getStatus())) {
            paid(row, trade);
            return;
        }
        if ("TRADE_CLOSED".equals(trade.getStatus())) {
            orderMapper.closeOrder(((Number) row.get("id")).longValue());
            return;
        }
        if (expired) {
            alipay.close(row.get("id").toString());
            orderMapper.closeOrder(((Number) row.get("id")).longValue());
        }
    }

    public void notification(Map<String, String> params) {
        AlipayTradeDto trade = alipay.notification(params);
        tx.executeWithoutResult(t -> {
            long id = number(trade.getPayNo());
            Map<String, Object> row = required(orderMapper.selectNotificationOrderForUpdate(id));
            if (Set.of("TRADE_SUCCESS", "TRADE_FINISHED").contains(trade.getStatus())) paid(
                row,
                trade
            );
            else if (
                "TRADE_CLOSED".equals(trade.getStatus()) && "60201".equals(row.get("status"))
            ) {
                if (
                    trade.getAmount().compareTo((BigDecimal) row.get("total_price")) != 0
                ) throw new ResponseStatusException(HttpStatus.CONFLICT, "通知金额不匹配");
                orderMapper.closeOrder(id);
            }
        });
    }

    /** 锁定订单后校验金额、流水和实际支付时间，再同事务保存支付状态与可靠事件。 */
    private void paid(Map<String, Object> row, AlipayTradeDto trade) {
        // 支付平台时间固定为北京时间，事件和MySQL DATETIME统一转换为UTC。
        LocalDateTime utcPaid =
            trade.getPaidAt() == null
                ? null
                : trade
                      .getPaidAt()
                      .atZone(ZoneId.of("Asia/Shanghai"))
                      .withZoneSameInstant(ZoneOffset.UTC)
                      .toLocalDateTime();
        String id = row.get("id").toString();
        if (
            !id.equals(trade.getPayNo()) ||
            trade.getTradeNo() == null ||
            trade.getTradeNo().isBlank() ||
            trade.getAmount() == null ||
            trade.getAmount().compareTo((BigDecimal) row.get("total_price")) != 0 ||
            utcPaid == null ||
            utcPaid.isBefore(time(row.get("create_date"))) ||
            !utcPaid.isBefore(time(row.get("expires_at")))
        ) throw new ResponseStatusException(HttpStatus.CONFLICT, "支付金额、流水或支付时间不匹配");
        Map<String, Object> payment = orderMapper.selectPaymentForUpdate(
            ((Number) row.get("id")).longValue(),
            ((Number) row.get("id")).longValue()
        );
        if ("60302".equals(payment.get("status"))) {
            if (
                !trade.getTradeNo().equals(payment.get("out_pay_no"))
            ) throw new ResponseStatusException(HttpStatus.CONFLICT, "重复通知流水不一致");
            return;
        }
        if (
            !Set.of("60201", "60203").contains(row.get("status"))
        ) throw new ResponseStatusException(HttpStatus.CONFLICT, "订单状态不能确认支付");
        orderMapper.confirmPayment(trade.getTradeNo(), utcPaid, number(id));
        orderMapper.markOrderPaid(number(id));
        CourseOrderSnapshotDto snapshot = json.readValue(
            row.get("order_detail").toString(),
            CourseOrderSnapshotDto.class
        );
        orderMapper.insertPaymentEvent(
            number(id),
            json.writeValueAsString(new PaymentSuccessEvent(id, snapshot, utcPaid))
        );
    }

    /** 发布确认只证明消息到队列；直到学习服务提交并回执才停止重发。 */
    public void maintain() {
        List<Map<String, Object>> expired = orderMapper.selectExpiredOrders();
        for (Map<String, Object> row : expired)
            try {
                tx.executeWithoutResult(t -> {
                    Map<String, Object> order = orderMapper.selectOrderForUpdate(
                        ((Number) row.get("id")).longValue()
                    );
                    refreshRow(order);
                });
            } catch (Exception error) {
                log.warn(
                    "超时订单查询失败，orderId={}，type={}",
                    row.get("id"),
                    error.getClass().getSimpleName()
                );
            }
        List<Map<String, Object>> events = orderMapper.selectPendingPaymentEvents();
        for (Map<String, Object> row : events)
            try {
                sender.send(
                    "payment.learning",
                    json.readValue(row.get("payload").toString(), PaymentSuccessEvent.class)
                );
                orderMapper.markPaymentEventAttempt(((Number) row.get("order_id")).longValue());
            } catch (Exception error) {
                orderMapper.markPaymentEventError(
                    error.getClass().getSimpleName(),
                    ((Number) row.get("order_id")).longValue()
                );
            }
    }

    public void receipt(String orderId) {
        orderMapper.markPaymentEventDelivered(number(orderId));
    }
}
