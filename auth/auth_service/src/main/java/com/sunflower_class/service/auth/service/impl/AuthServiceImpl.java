package com.sunflower_class.service.auth.service.impl;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.base.security.CurrentUser;
import com.sunflower_class.model.dto.IdentityDto;
import com.sunflower_class.model.dto.InstitutionApplicationDto;
import com.sunflower_class.model.dto.LoginRequestDto;
import com.sunflower_class.model.dto.RegisterRequestDto;
import com.sunflower_class.service.auth.mapper.AuthMapper;
import com.sunflower_class.service.auth.service.AuthService;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 认证业务层负责账号查询、密码与状态校验、失败锁定和 JWT 签发。 */
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private AuthMapper authMapper;

    private JwtEncoder encoder;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final String missingPassword = passwords.encode("不存在的账号仅用于等时校验");

    @Autowired
    private SecretKeySpec identityKey;

    /** 注入完成后使用原共享密钥初始化签发器。 */
    @PostConstruct
    public void initialize() {
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(identityKey));
    }

    /** 复用已有校验与锁定逻辑，模块拆分不改变登录规则。 */
    @Override
    public String login(LoginRequestDto input) {
        if (
            input.getUsername() == null ||
            input.getPassword() == null ||
            input.getUsername().isBlank() ||
            input.getUsername().length() > 45 ||
            input.getPassword().getBytes(StandardCharsets.UTF_8).length > 72 ||
            input.getPassword().isBlank()
        ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入有效账号和密码");
        }
        List<Map<String, Object>> users = authMapper.selectLoginAccount(input.getUsername());
        Map<String, Object> user = users.isEmpty() ? null : users.getFirst();
        String hash = user == null ? null : (String) user.get("password_hash");
        // 微信标识和损坏散列不是密码；仍执行一次 BCrypt，统一失败响应且不开放明文回退。
        boolean bcrypt =
            hash != null && hash.matches("\\A\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}\\z");
        boolean valid =
            passwords.matches(input.getPassword(), bcrypt ? hash : missingPassword) && bcrypt;
        String role =
            user == null
                ? ""
                : switch (String.valueOf(user.get("utype"))) {
                      case "10201" -> "student";
                      case "10202" -> "teacher";
                      case "10203" -> "admin";
                      default -> "";
                  };
        // 平台管理员必须同时具备管理员用户类型和已启用的显式管理角色。
        if ("admin".equals(role) && ((Number) user.get("platform_role")).intValue() != 1) {
            role = "";
        }
        Long companyId = null;
        boolean companyValid = true;
        if ("teacher".equals(role)) {
            String direct = (String) user.get("company_id");
            String linked = (String) user.get("linked_company");
            Number count = (Number) user.get("company_count");
            companyValid =
                (count == null || count.intValue() <= 1) &&
                (direct == null || linked == null || direct.equals(linked));
            try {
                companyId = Long.valueOf(direct != null ? direct : linked);
                companyValid &= companyId > 0;
            } catch (RuntimeException failure) {
                companyValid = false;
            }
        }
        if (
            user == null ||
            !valid ||
            !"1".equals(user.get("status")) ||
            role.isEmpty() ||
            !companyValid ||
            ((Number) user.get("locked")).intValue() != 0
        ) {
            if (user != null) authMapper.increaseLoginFailures((String) user.get("id"));
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "账号或密码错误，或账号暂不可用"
            );
        }
        authMapper.resetLoginFailures((String) user.get("id"));
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
            .issuer("sunflower-class")
            .subject(user.get("id").toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(1800))
            .claim("username", user.get("username"))
            .claim("name", user.get("display_name"))
            .claim("role", role);
        // 仅机构类型获得机构身份，学生或管理员不会因旧字段而获得机构权限。
        if (companyId != null) claims.claim("companyId", companyId);
        claims.claim("canManageTeachers", "admin".equals(role));
        return encoder
            .encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(),
                    claims.build()
                )
            )
            .getTokenValue();
    }

    /** 只返回已验证身份中的公开字段，不携带密码、散列或令牌。 */
    @Override
    public IdentityDto identity() {
        Jwt jwt = CurrentUser.jwt();
        return new IdentityDto(
            jwt.getSubject(),
            jwt.getClaimAsString("name"),
            jwt.getClaimAsString("role"),
            jwt.hasClaim("companyId") ? jwt.getClaim("companyId") : "",
            "admin".equals(jwt.getClaimAsString("role"))
        );
    }

    /** 复用已有用户表、唯一索引与 BCrypt；不授予机构或管理角色。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequestDto input) {
        validateAccount(input);
        insertAccount(input, "10201", null);
    }

    /** 注册与机构开户采用同一密码规则，字节上限与 BCrypt 实际能力一致。 */
    private void validateAccount(RegisterRequestDto input) {
        if (
            input == null ||
            input.getUsername() == null ||
            !input.getUsername().matches("[A-Za-z0-9][A-Za-z0-9_.-]{2,44}") ||
            input.getName() == null ||
            input.getName().isBlank() ||
            input.getName().length() > 45 ||
            input.getPassword() == null ||
            input.getPassword().length() < 8 ||
            input.getPassword().isBlank() ||
            input.getPassword().getBytes(StandardCharsets.UTF_8).length > 72 ||
            !input.getPassword().equals(input.getConfirmPassword())
        ) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "账号须为3至45位字母、数字、点、下划线或短横线；姓名不超过45字，密码至少8位且不超过72字节，两次密码须一致"
            );
        }
    }

    /** 数据库唯一约束处理并发重名；密码仅以散列落库，不写入日志或响应。 */
    private String insertAccount(RegisterRequestDto input, String type, Long companyId) {
        String id = UUID.randomUUID().toString().replace("-", "");
        try {
            authMapper.insertUser(
                id,
                input.getUsername(),
                passwords.encode(input.getPassword()),
                input.getName().trim(),
                type,
                companyId == null ? null : companyId.toString()
            );
        } catch (DuplicateKeyException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该账号已存在，请更换账号");
        }
        return id;
    }

    /** 账号、老师角色与机构关联在一个事务保存；任何一步失败均回滚。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createTeacher(RegisterRequestDto input) {
        requirePlatformAdmin();
        // 新老师使用独立教学空间，旧账号及课程的所属机构不变。
        long company = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        validateAccount(input);
        authMapper.insertCompany(
            Long.toString(company),
            input.getName().trim() + "的教学空间",
            input.getName().trim(),
            "",
            "",
            ""
        );
        String id = insertAccount(input, "10202", company);
        authMapper.insertCompanyUser(
            UUID.randomUUID().toString().replace("-", ""),
            Long.toString(company),
            id
        );
        int assigned = authMapper.assignTeacherRole(
            UUID.randomUUID().toString().replace("-", ""),
            id,
            CurrentUser.jwt().getSubject()
        );
        if (assigned != 1) throw new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "老师角色配置不可用"
        );
    }

    /** 平台管理员统一查询老师账号，响应不返回密码。 */
    @Override
    public PageResult<Map<String, Object>> teachers(long page) {
        requirePlatformAdmin();
        long current = Math.max(1, Math.min(page, 100000));
        Long total = authMapper.countTeachers();
        List<Map<String, Object>> rows = authMapper.selectTeachers((current - 1) * 10);
        return new PageResult<>(rows, total == null ? 0L : total, current, 10L);
    }

    /** 复用注册密码规则；申请资料只保留散列，审核前没有机构、账号或权限。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String applyInstitution(InstitutionApplicationDto input) {
        CurrentUser.requireRole("student");
        if (input == null) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "请填写老师申请资料"
        );
        RegisterRequestDto account = new RegisterRequestDto(
            input.getUsername(),
            input.getPassword(),
            input.getConfirmPassword(),
            input.getName()
        );
        validateAccount(account);
        if (
            !validText(input.getCompanyName(), 128) ||
            !validText(input.getContact(), 64) ||
            input.getMobile() == null ||
            !input.getMobile().matches("1[3-9][0-9]{9}") ||
            // 原机构表使用 utf8mb3，邮箱也必须通过相同字符范围校验，避免审批时才失败。
            input.getEmail() == null ||
            (!input.getEmail().isBlank() &&
                (!validText(input.getEmail(), 128) ||
                    !input.getEmail().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) ||
            !validText(input.getIntro(), 512) ||
            !validText(input.getName(), 45)
        ) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "姓名、手机号或教学简介不正确"
            );
        }
        if (authMapper.countUsername(input.getUsername()) > 0) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "老师账号已存在，请选择新的账号"
        );
        String id = UUID.randomUUID().toString().replace("-", "");
        try {
            authMapper.insertInstitutionApplication(
                id,
                CurrentUser.jwt().getSubject(),
                input.getCompanyName().trim(),
                input.getContact().trim(),
                input.getMobile(),
                input.getEmail(),
                input.getIntro().trim(),
                input.getUsername(),
                input.getName().trim(),
                passwords.encode(input.getPassword())
            );
        } catch (DuplicateKeyException failure) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "已有待审核申请或该老师账号正在申请中"
            );
        }
        return id;
    }

    /** 原机构表使用 UTF-8 三字节字符集，不让四字节字符在通过审核时才触发保存失败。 */
    private boolean validText(String value, int limit) {
        return (
            value != null &&
            !value.isBlank() &&
            value.length() <= limit &&
            value.codePoints().allMatch(point -> point <= 0xffff)
        );
    }

    /** 平台权限同时核对实时账号和显式平台角色，旧JWT不能维持已撤销的权限。 */
    private void requirePlatformAdmin() {
        CurrentUser.requireRole("admin");
        Integer enabled = authMapper.countPlatformAdmin(CurrentUser.jwt().getSubject());
        if (enabled == null || enabled != 1) throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "仅启用的平台管理员可执行此操作"
        );
    }

    /** 申请人只能查询自己的申请；平台队列不返回密码散列。 */
    @Override
    public PageResult<Map<String, Object>> institutionApplications(
        long page,
        String status,
        boolean platform
    ) {
        if (platform) requirePlatformAdmin();
        else CurrentUser.requireRole("student");
        if (
            page < 1 ||
            page > 100000 ||
            (status != null && !Set.of("40101", "40102", "40103").contains(status))
        ) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页或审核状态不正确");
        // 使用绑定参数限制申请人和状态，平台权限仍由前置检查控制。
        String applicantId = platform ? null : CurrentUser.jwt().getSubject();
        Long total = authMapper.countInstitutionApplications(applicantId, status);
        List<Map<String, Object>> rows = authMapper.selectInstitutionApplications(
            applicantId,
            status,
            (page - 1) * 10
        );
        return new PageResult<>(rows, total, page, 10L);
    }

    /** 锁定申请后一次提交机构、独立老师账号和老师角色；任何失败整体回滚。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewInstitution(String id, boolean approved, String reason) {
        requirePlatformAdmin();
        if (
            id == null ||
            !id.matches("[0-9a-f]{32}") ||
            reason == null ||
            reason.isBlank() ||
            reason.length() > 500
        ) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写不超过500字的审核说明");
        List<Map<String, Object>> rows = authMapper.selectInstitutionApplicationForUpdate(id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "申请不存在");
        Map<String, Object> row = rows.getFirst();
        String target = approved ? "40102" : "40103";
        if (!"40101".equals(row.get("status"))) {
            if (target.equals(row.get("status"))) return;
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该申请已审核，不能变更结论");
        }
        String company = null,
            user = null;
        if (approved) {
            company = Long.toString(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE);
            user = UUID.randomUUID().toString().replace("-", "");
            authMapper.insertCompany(
                company,
                (String) row.get("company_name"),
                (String) row.get("contact"),
                (String) row.get("mobile"),
                (String) row.get("email"),
                (String) row.get("intro")
            );
            try {
                authMapper.insertInstitutionAdmin(
                    user,
                    (String) row.get("admin_username"),
                    (String) row.get("password_hash"),
                    (String) row.get("admin_name"),
                    company
                );
            } catch (DuplicateKeyException conflict) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "老师账号已被占用，请驳回后重新申请"
                );
            }
            authMapper.insertCompanyUser(
                UUID.randomUUID().toString().replace("-", ""),
                company,
                user
            );
            int assigned = authMapper.assignTeacherRole(
                UUID.randomUUID().toString().replace("-", ""),
                user,
                CurrentUser.jwt().getSubject()
            );
            if (assigned != 1) throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "老师角色配置不可用，开户已回滚"
            );
        }
        // 完成审核后删除申请中的密码散列，只在新账号表保留需要的密码。
        authMapper.reviewInstitutionApplication(
            target,
            reason.trim(),
            CurrentUser.jwt().getSubject(),
            company,
            user,
            id
        );
    }
}
