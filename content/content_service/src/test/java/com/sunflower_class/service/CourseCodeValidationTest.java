package com.sunflower_class.service;

import static org.junit.jupiter.api.Assertions.*;

import com.sunflower_class.base.exception.GlobalException;
import com.sunflower_class.model.dto.AddCourseDto;
import com.sunflower_class.service.content.service.impl.CourseBaseInfoServiceImpl;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 收费编码及价格约束的单元测试，阻止非法输入进入保存流程。
 */
class CourseCodeValidationTest {

    /**
     * 验证旧版和未知收费编码会被拒绝，防止非标准状态码写入课程。
     */
    @Test
    void rejectsLegacyAndUnknownChargeCodes() {
        var service = new CourseBaseInfoServiceImpl();
        for (String code : new String[] { "201001", "70102", "99999" }) {
            var dto = new AddCourseDto();
            dto.setCharge(code);
            dto.setPrice(BigDecimal.TEN);
            assertThrows(GlobalException.class, () ->
                ReflectionTestUtils.invokeMethod(service, "validateCharge", dto)
            );
        }
    }

    /**
     * 验证收费课程必须填写大于零的价格。
     */
    @Test
    void paidCoursesRequirePositivePrice() {
        var service = new CourseBaseInfoServiceImpl();
        var dto = new AddCourseDto();
        dto.setCharge("30202");
        dto.setPrice(BigDecimal.ZERO);
        assertThrows(GlobalException.class, () ->
            ReflectionTestUtils.invokeMethod(service, "validateCharge", dto)
        );
        dto.setPrice(BigDecimal.TEN);
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "validateCharge", dto));
    }
}
