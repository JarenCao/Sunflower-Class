package com.sunflower_class.service.auth.service;

import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.model.dto.IdentityDto;
import com.sunflower_class.model.dto.InstitutionApplicationDto;
import com.sunflower_class.model.dto.LoginRequestDto;
import com.sunflower_class.model.dto.RegisterRequestDto;
import java.util.Map;

/** 认证服务接口隔离 HTTP 处理与数据库、密码及令牌业务。 */
public interface AuthService {
    String login(LoginRequestDto input);

    IdentityDto identity();

    /** 自助注册始终创建学员账号。 */
    void register(RegisterRequestDto input);

    /** 平台管理员创建老师及独立教学空间。 */
    void createTeacher(RegisterRequestDto input);

    /** 平台管理员查询老师账号，不包含密码或令牌。 */
    PageResult<Map<String, Object>> teachers(long page);

    /** 老师申请保留学员身份，通过审核后开通独立老师账号。 */
    String applyInstitution(InstitutionApplicationDto input);

    PageResult<Map<String, Object>> institutionApplications(
        long page,
        String status,
        boolean platform
    );

    void reviewInstitution(String id, boolean approved, String reason);
}
