package com.sunflower_class.service.orders.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 按原三层架构集中保存业务 SQL，服务层只编排规则与事务。 */
public interface OrderMapper {
    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select("SELECT DATABASE()")
    String selectDatabase();

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select("SELECT CURRENT_TIMESTAMP")
    LocalDateTime selectCurrentTime();

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select(
        "<script>SELECT * FROM orders WHERE id=#{id} AND user_id=#{userId} AND course_id IS NOT NULL<if test=\"lock\"> FOR UPDATE</if></script>"
    )
    List<Map<String, Object>> selectOwnedOrder(
        @Param("id") Long id,
        @Param("userId") String userId,
        @Param("lock") boolean lock
    );

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select("SELECT EXISTS(SELECT 1 FROM payment_event WHERE order_id=#{orderId} AND delivered=1)")
    Boolean isPaymentDelivered(@Param("orderId") Long orderId);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select(
        "<script>SELECT COUNT(*) FROM orders WHERE user_id=#{userId} AND course_id IS NOT NULL<if test='status != null'> AND status=#{status}</if></script>"
    )
    Long countStudentOrders(@Param("userId") String userId, @Param("status") String status);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select(
        "<script>SELECT * FROM orders WHERE user_id=#{userId} AND course_id IS NOT NULL<if test='status != null'> AND status=#{status}</if> ORDER BY create_date DESC,id DESC LIMIT #{limit} OFFSET #{offset}</script>"
    )
    List<Map<String, Object>> selectStudentOrders(
        @Param("userId") String userId,
        @Param("limit") Long limit,
        @Param("offset") Long offset,
        @Param("status") String status
    );

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select("SELECT * FROM orders WHERE user_id=#{userId} AND active_course=#{courseId}")
    List<Map<String, Object>> selectActiveOrder(
        @Param("userId") String userId,
        @Param("courseId") Long courseId
    );

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Insert(
        "INSERT INTO orders(id,total_price,create_date,status,user_id,order_type,order_name,order_descrip,order_detail,out_business_id,course_id,expires_at) VALUES (#{id},#{totalPrice},#{createdAt},'60201',#{userId},'60101',#{orderName},#{description},#{detail}, #{businessId},#{courseId},#{expiresAt}) ON DUPLICATE KEY UPDATE id=id"
    )
    int insertOrder(
        @Param("id") Long id,
        @Param("totalPrice") BigDecimal totalPrice,
        @Param("createdAt") LocalDateTime createdAt,
        @Param("userId") String userId,
        @Param("orderName") String orderName,
        @Param("description") String description,
        @Param("detail") String detail,
        @Param("businessId") String businessId,
        @Param("courseId") Long courseId,
        @Param("expiresAt") LocalDateTime expiresAt
    );

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select("SELECT * FROM orders WHERE user_id=#{userId} AND active_course=#{courseId} FOR UPDATE")
    Map<String, Object> selectActiveOrderForUpdate(
        @Param("userId") String userId,
        @Param("courseId") Long courseId
    );

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Insert(
        "INSERT INTO orders_goods(order_id,goods_id,goods_type,goods_name,goods_price,goods_detail) VALUES (#{orderId},#{goodsId},'60101',#{goodsName},#{price},#{detail})"
    )
    int insertOrderGoods(
        @Param("orderId") Long orderId,
        @Param("goodsId") String goodsId,
        @Param("goodsName") String goodsName,
        @Param("price") BigDecimal price,
        @Param("detail") String detail
    );

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Insert(
        "INSERT INTO pay_record(pay_no,order_id,order_name,total_price,currency,create_date,status,user_id) VALUES (#{payNo},#{orderId},#{orderName},#{totalPrice}, 'CNY',#{createdAt},'60301',#{userId})"
    )
    int insertPayment(
        @Param("payNo") Long payNo,
        @Param("orderId") Long orderId,
        @Param("orderName") String orderName,
        @Param("totalPrice") BigDecimal totalPrice,
        @Param("createdAt") LocalDateTime createdAt,
        @Param("userId") String userId
    );

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select("SELECT * FROM orders WHERE id=#{id}")
    Map<String, Object> selectOrder(@Param("id") Long id);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Update("UPDATE pay_record SET out_pay_channel='alipay-sandbox' WHERE order_id=#{orderId}")
    int markPaymentAttempt(@Param("orderId") Long orderId);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Update("UPDATE orders SET qr_code=#{qrCode} WHERE id=#{id}")
    int updateOrderQr(@Param("qrCode") String qrCode, @Param("id") Long id);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select("SELECT * FROM orders WHERE id=#{id} AND user_id=#{userId}")
    Map<String, Object> selectStudentOrder(@Param("id") Long id, @Param("userId") String userId);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select("SELECT * FROM pay_record WHERE order_id=#{orderId} AND pay_no=#{payNo}")
    Map<String, Object> selectPayment(@Param("orderId") Long orderId, @Param("payNo") Long payNo);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Update("UPDATE orders SET status='60203',qr_code=NULL WHERE id=#{id}")
    int closeOrder(@Param("id") Long id);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select("SELECT * FROM orders WHERE id=#{id} AND course_id IS NOT NULL FOR UPDATE")
    List<Map<String, Object>> selectNotificationOrderForUpdate(@Param("id") Long id);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select("SELECT * FROM pay_record WHERE order_id=#{orderId} AND pay_no=#{payNo} FOR UPDATE")
    Map<String, Object> selectPaymentForUpdate(
        @Param("orderId") Long orderId,
        @Param("payNo") Long payNo
    );

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Update(
        "UPDATE pay_record SET status='60302',out_pay_no=#{tradeNo},out_pay_channel='alipay-sandbox',pay_success_time=#{paidAt} WHERE pay_no=#{payNo}"
    )
    int confirmPayment(
        @Param("tradeNo") String tradeNo,
        @Param("paidAt") LocalDateTime paidAt,
        @Param("payNo") Long payNo
    );

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Update("UPDATE orders SET status='60202',qr_code=NULL WHERE id=#{id}")
    int markOrderPaid(@Param("id") Long id);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Insert("INSERT INTO payment_event(order_id,payload) VALUES (#{orderId},#{payload})")
    int insertPaymentEvent(@Param("orderId") Long orderId, @Param("payload") String payload);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select(
        "SELECT id FROM orders WHERE course_id IS NOT NULL AND status='60201' AND expires_at<=CURRENT_TIMESTAMP LIMIT 20"
    )
    List<Map<String, Object>> selectExpiredOrders();

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select("SELECT * FROM orders WHERE id=#{id} FOR UPDATE")
    Map<String, Object> selectOrderForUpdate(@Param("id") Long id);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select(
        "SELECT order_id,payload FROM payment_event WHERE delivered=0 AND next_attempt<=CURRENT_TIMESTAMP LIMIT 20"
    )
    List<Map<String, Object>> selectPendingPaymentEvents();

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Update(
        "UPDATE payment_event SET attempts=attempts+1,last_error=NULL,next_attempt=DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 10 SECOND) WHERE order_id=#{orderId} AND delivered=0"
    )
    int markPaymentEventAttempt(@Param("orderId") Long orderId);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Update(
        "UPDATE payment_event SET attempts=attempts+1,last_error=#{error},next_attempt=DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 10 SECOND) WHERE order_id=#{orderId} AND delivered=0"
    )
    int markPaymentEventError(@Param("error") String error, @Param("orderId") Long orderId);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Update(
        "UPDATE payment_event SET delivered=1,delivered_at=CURRENT_TIMESTAMP,last_error=NULL WHERE order_id=#{orderId} AND delivered=0"
    )
    int markPaymentEventDelivered(@Param("orderId") Long orderId);
}
