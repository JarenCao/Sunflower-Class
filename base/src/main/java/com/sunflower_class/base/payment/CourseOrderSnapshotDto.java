package com.sunflower_class.base.payment;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 学习服务提供已保存的选课价格与有效期，订单不能接受客户端价格。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseOrderSnapshotDto {

    /** 用户编号。 */
    private String userId;

    /** 课程编号。 */
    private long courseId;

    /** 课程名称快照。 */
    private String courseName;

    /** 价格快照，由服务端读取，不能采用客户端价格。 */
    private BigDecimal price;

    /** 课程资格有效天数。 */
    private Integer validDays;
}
