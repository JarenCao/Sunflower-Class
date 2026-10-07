package com.sunflower_class.api;

import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.model.dto.IdentityDto;
import com.sunflower_class.model.dto.InstitutionApplicationDto;
import com.sunflower_class.model.dto.LoginRequestDto;
import com.sunflower_class.model.dto.RegisterRequestDto;
import com.sunflower_class.service.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 认证 API 层仅负责请求、响应、原生 CSRF 与 Cookie，业务交给服务层。 */
@Tag(name = "认证与老师申请")
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Value("${sunflower.auth.cookie-secure:true}")
    private boolean secureCookie;

    /** 身份和 CSRF 响应不进入浏览器或代理缓存。 */
    @ModelAttribute
    public void noCache(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    @Operation(summary = "取得防跨站请求令牌")
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    /** JWT 仅进入 HttpOnly Cookie，不向前端 JSON 返回。 */
    @Operation(summary = "登录")
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequestDto input) {
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie(authService.login(input), 1800))
            .body(Map.of("message", "登录成功"));
    }

    @Operation(summary = "取得当前登录身份")
    @GetMapping("/me")
    public IdentityDto me() {
        return authService.identity();
    }

    /** 注册只接受开户字段，拒绝客户端伪造身份、机构等额外字段。 */
    @Operation(summary = "注册学员账号")
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody Map<String, Object> input) {
        authService.register(accountInput(input));
        return ResponseEntity.status(201).body(Map.of("message", "注册成功，请登录"));
    }

    /** 非对象JSON和损坏JSON属于表单错误，不进入通用500异常处理。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> invalidForm() {
        return ResponseEntity.badRequest().body(Map.of("message", "请求数据格式不正确"));
    }

    /** 在 API 层检查字段形状，服务层再次检查值，统一返回可读的400错误。 */
    private RegisterRequestDto accountInput(Map<String, Object> input) {
        Set<String> fields = Set.of("username", "password", "confirmPassword", "name");
        if (
            !fields.containsAll(input.keySet()) ||
            !input
                .values()
                .stream()
                .allMatch(value -> value instanceof String)
        ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "账号表单包含无效字段");
        }
        return new RegisterRequestDto(
            (String) input.get("username"),
            (String) input.get("password"),
            (String) input.get("confirmPassword"),
            (String) input.get("name")
        );
    }

    /** 仅平台管理员可查询老师账号，响应不返回密码。 */
    @Operation(summary = "查询老师账号")
    @GetMapping("/teachers")
    public PageResult<Map<String, Object>> teachers(@RequestParam(defaultValue = "1") long pageNo) {
        return authService.teachers(pageNo);
    }

    /** 开户字段与注册相同，角色固定为老师，教学空间由服务端创建。 */
    @Operation(summary = "创建老师账号", description = "仅平台管理员可操作，自动创建独立教学空间。")
    @PostMapping("/teachers")
    public ResponseEntity<Map<String, String>> createTeacher(
        @RequestBody Map<String, Object> input
    ) {
        authService.createTeacher(accountInput(input));
        return ResponseEntity.status(201).body(Map.of("message", "老师账号已创建"));
    }

    /** 老师申请与平台审核复用认证服务，控制器只处理请求与响应。 */
    @Operation(summary = "提交老师申请")
    @PostMapping("/institution-applications")
    public ResponseEntity<Map<String, String>> applyInstitution(
        @RequestBody Map<String, Object> input
    ) {
        Set<String> fields = Set.of(
            "companyName",
            "contact",
            "mobile",
            "email",
            "intro",
            "username",
            "name",
            "password",
            "confirmPassword"
        );
        if (
            (!fields.equals(input.keySet()) &&
                !Set.of(
                    "name",
                    "mobile",
                    "intro",
                    "username",
                    "password",
                    "confirmPassword"
                ).equals(input.keySet())) ||
            input
                .values()
                .stream()
                .anyMatch(value -> !(value instanceof String))
        ) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "老师申请表单包含无效字段");
        String id = authService.applyInstitution(
            new InstitutionApplicationDto(
                (String) input.getOrDefault("companyName", input.get("name") + "的教学空间"),
                (String) input.getOrDefault("contact", input.get("name")),
                (String) input.get("mobile"),
                (String) input.getOrDefault("email", ""),
                (String) input.get("intro"),
                (String) input.get("username"),
                (String) input.get("name"),
                (String) input.get("password"),
                (String) input.get("confirmPassword")
            )
        );
        return ResponseEntity.status(201).body(
            Map.of("id", id, "message", "申请已提交，审核通过后可使用老师账号登录")
        );
    }

    @Operation(summary = "查询我的老师申请", description = "仅返回登录学员本人申请记录。")
    @GetMapping("/institution-applications")
    public PageResult<Map<String, Object>> ownApplications(
        @RequestParam(defaultValue = "1") long pageNo
    ) {
        return authService.institutionApplications(pageNo, null, false);
    }

    @Operation(
        summary = "查询老师申请审核队列",
        description = "仅平台管理员可访问，支持按审核状态筛选。"
    )
    @GetMapping("/platform/institution-applications")
    public PageResult<Map<String, Object>> applicationQueue(
        @RequestParam(defaultValue = "1") long pageNo,
        @RequestParam(required = false) String status
    ) {
        return authService.institutionApplications(pageNo, status, true);
    }

    @Operation(
        summary = "审核老师申请",
        description = "通过后创建老师账号和教学空间，驳回须填写原因。"
    )
    @PostMapping("/platform/institution-applications/{id}/review")
    public ResponseEntity<Void> reviewInstitution(
        @PathVariable String id,
        @RequestBody Map<String, Object> input
    ) {
        if (
            !input.keySet().equals(Set.of("approved", "reason")) ||
            !(input.get("approved") instanceof Boolean approved) ||
            !(input.get("reason") instanceof String reason)
        ) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "审核表单格式不正确");
        authService.reviewInstitution(id, approved, reason);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", 0)).build();
    }

    /** 保留现有同源 HttpOnly、SameSite 和 HTTPS Secure 规则。 */
    private String cookie(String token, long seconds) {
        return ResponseCookie.from("SUNFLOWER_TOKEN", token)
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite("Lax")
            .path("/")
            .maxAge(seconds)
            .build()
            .toString();
    }
}
