package com.sunflower_class.base.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

/** 从框架已验签的身份读取业务字段，不接受请求体或自定义请求头中的机构编号。 */
public final class CurrentUser {

    private CurrentUser() {}

    /** 后台任务没有登录身份，不能借用开发机构执行用户操作。 */
    public static Jwt jwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return jwt;
    }

    /** 业务层重复核对角色，不能仅依赖页面可见性或网关。 */
    public static void requireRole(String role) {
        if (!role.equals(jwt().getClaimAsString("role"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有此操作权限");
        }
    }

    /** 学员不具备机构身份，不能通过传入 companyId 访问管理数据。 */
    public static Long companyId() {
        requireRole("teacher");
        Number company = jwt().getClaim("companyId");
        if (company == null || company.longValue() <= 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有机构身份");
        }
        return company.longValue();
    }
}
