package com.sunflower_class.service.orders.service;

import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.model.dto.CourseOrderDto;
import java.util.Map;

/** 订单接口由业务层核对身份、快照、支付状态及可靠通知。 */
public interface OrderService {
    CourseOrderDto create(long courseId);

    CourseOrderDto get(String id);

    PageResult<CourseOrderDto> list(long page, long size, String status);

    String pay(String id);

    CourseOrderDto refresh(String id);

    void notification(Map<String, String> params);

    void maintain();

    void receipt(String orderId);
}
