package com.sunflower_class.api;

import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.model.dto.CourseOrderDto;
import com.sunflower_class.service.orders.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 控制器只做请求映射，不接受客户端用户、价格或支付成功状态。 */
@Tag(name = "课程订单与支付")
@RestController
public class OrderController {

    @Autowired
    private OrderService service;

    @Operation(summary = "创建课程订单")
    @PostMapping("/purchases/{courseId}")
    public CourseOrderDto create(@PathVariable long courseId) {
        return service.create(courseId);
    }

    @Operation(
        summary = "分页查询我的课程订单",
        description = "仅查询登录学员本人订单，支持订单状态筛选。"
    )
    @GetMapping("/purchases")
    public PageResult<CourseOrderDto> list(
        @RequestParam(defaultValue = "1") long pageNo,
        @RequestParam(defaultValue = "10") long pageSize,
        @RequestParam(required = false) String status
    ) {
        return service.list(pageNo, pageSize, status);
    }

    @Operation(
        summary = "查询我的课程订单详情",
        description = "服务端核对订单归属，不接受客户端指定学员身份。"
    )
    @GetMapping("/purchases/{id}")
    public CourseOrderDto get(@PathVariable String id) {
        return service.get(id);
    }

    @Operation(summary = "取得支付宝沙箱支付二维码")
    @PostMapping("/purchases/{id}/pay")
    public Map<String, String> pay(@PathVariable String id) {
        return Map.of("qrCode", service.pay(id));
    }

    @Operation(summary = "查询支付并刷新订单状态")
    @PostMapping("/purchases/{id}/refresh")
    public CourseOrderDto refresh(@PathVariable String id) {
        return service.refresh(id);
    }

    /** 支付平台通知只豁免此精确入口的CSRF，单值表单参数仍需服务层验签。 */
    @Operation(summary = "接收支付宝验签通知")
    @PostMapping(
        value = "/payments/alipay/notify",
        consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
        produces = MediaType.TEXT_PLAIN_VALUE
    )
    public String notify(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, value) -> {
            if (value.length != 1 || value[0].length() > 16384) throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "支付通知参数无效"
            );
            params.put(key, value[0]);
        });
        if (params.size() > 100) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "支付通知参数过多"
        );
        service.notification(params);
        return "success";
    }
}
