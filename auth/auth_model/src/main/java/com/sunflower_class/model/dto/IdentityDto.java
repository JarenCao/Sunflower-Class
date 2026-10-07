package com.sunflower_class.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 对外身份数据；无机构时保留原接口的空字符串，避免改变两端约定。 */
@Schema(description = "对外身份数据；无机构时保留原接口的空字符串，避免改变两端约定。")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdentityDto {

    /** 用户编号，以字符串返回。 */
    @Schema(description = "用户编号，以字符串返回。")
    private String id;

    /** 显示名称。 */
    @Schema(description = "显示名称。")
    private String name;

    /** 身份角色：student（学员）、teacher（老师）、admin（平台管理员）。 */
    @Schema(description = "身份角色：student（学员）、teacher（老师）、admin（平台管理员）。")
    private String role;

    /** 老师教学空间编号；学员与管理员返回空字符串。 */
    @Schema(description = "老师教学空间编号；学员与管理员返回空字符串。")
    private Object companyId;

    /** 是否具有老师账号管理权限，仅平台管理员为 true。 */
    @Schema(description = "是否具有老师账号管理权限，仅平台管理员为 true。")
    private boolean canManageTeachers;
}
