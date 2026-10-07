package com.sunflower_class.model.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 原选课表使用学生和课程联合主键；只用自定义 Mapper 查询，不声明单字段主键。 */
@Data
@TableName("course_enrollment")
public class CourseEnrollment implements Serializable {

    private static final long serialVersionUID = 1L;
    /** 学生标识。 */
    private String userId;
    /** 课程标识。 */
    private Long courseId;
    /** 选课时的课程名称。 */
    private String courseName;
    /** 选课类型。 */
    private String enrollmentType;
    /** 选课状态。 */
    private String status;
    /** 选课时的价格。 */
    private BigDecimal price;
    /** 选课时的有效天数。 */
    private Integer validDays;
    /** 选课时间。 */
    private LocalDateTime createdAt;
    /** 资格到期时间。 */
    private LocalDateTime expiresAt;

    /** 当前课程是否上架。 */
    @TableField(exist = false)
    private boolean available;

    /** 学习资格是否已到期。 */
    @TableField(exist = false)
    private boolean expired;

    /** 本人已到期且最新发布仍免费的记录才展示续期入口。 */
    @TableField(exist = false)
    private boolean renewable;

    /** 封面来自课程发布副本，不向选课表添加重复字段。 */
    @TableField(exist = false)
    private String pic;
}
