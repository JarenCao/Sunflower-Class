package com.sunflower_class.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 当前学员选课快照及实时资格；不返回其他学员身份或受保护视频地址。 */
@Schema(description = "当前学员选课快照及实时资格；不返回其他学员身份或受保护视频地址。")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseEnrollmentDto {

    /** 课程编号。 */
    @Schema(description = "课程编号。")
    private long courseId;

    /** 选课时保存的课程名称。 */
    @Schema(description = "选课时保存的课程名称。")
    private String name;

    /** 选课类型：70101 免费、70102 收费。 */
    @Schema(description = "选课类型：70101 免费、70102 收费。")
    private String enrollmentType;

    /** 选课状态：70201 选课成功、70202 待支付。 */
    @Schema(description = "选课状态：70201 选课成功、70202 待支付。")
    private String status;

    /** 价格快照，由服务端读取，不能采用客户端价格。 */
    @Schema(description = "价格快照，由服务端读取，不能采用客户端价格。")
    private BigDecimal price;

    /** 创建时间。 */
    @Schema(description = "创建时间。")
    private LocalDateTime createdAt;

    /** 有效截止时间。 */
    @Schema(description = "有效截止时间。")
    private LocalDateTime expiresAt;

    /** 学习资格：70301 可学习、70302 无资格、70303 已到期。 */
    @Schema(description = "学习资格：70301 可学习、70302 无资格、70303 已到期。")
    private String qualification;

    /** 课程当前是否可学习。 */
    @Schema(description = "课程当前是否可学习。")
    private boolean courseAvailable;

    @Schema(description = "是否可免费续期，由最新发布规则和当前资格决定")
    private boolean renewable;

    @Schema(description = "课程发布副本中的封面地址")
    private String pic;
}
