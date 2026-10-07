package com.sunflower_class.service.auth.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 按原三层架构集中保存业务 SQL，服务层只编排规则与事务。 */
public interface AuthMapper {
    /** 按用户名读取账号、教学空间关联、管理角色与锁定状态。 */
    @Select(
        "SELECT u.id, u.username, u.name AS display_name, u.company_id, u.utype, u.password AS password_hash, u.status, cu.linked_company, cu.company_count, EXISTS (SELECT 1 FROM user_role ur JOIN role r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.role_code IN ('admin','super','reviewer') AND r.status='1') AS platform_role, (u.locked_until IS NOT NULL AND u.locked_until > CURRENT_TIMESTAMP) AS locked FROM `user` u LEFT JOIN (SELECT user_id, MIN(company_id) AS linked_company, COUNT(DISTINCT company_id) AS company_count FROM company_user GROUP BY user_id) cu ON cu.user_id = u.id WHERE u.username = #{username}"
    )
    List<Map<String, Object>> selectLoginAccount(@Param("username") String username);

    /** 累加失败次数，连续五次失败后锁定十五分钟。 */
    @Update(
        "UPDATE `user` SET locked_until = IF(failed_attempts >= 4, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 15 MINUTE), locked_until), failed_attempts = failed_attempts + 1 WHERE id = #{userId}"
    )
    int increaseLoginFailures(@Param("userId") String userId);

    /** 登录成功后清空失败次数和锁定时间。 */
    @Update("UPDATE `user` SET failed_attempts = 0, locked_until = NULL WHERE id = #{userId}")
    int resetLoginFailures(@Param("userId") String userId);

    /** 插入账号，密码参数必须是 BCrypt 散列，用户类型由服务端指定。 */
    @Insert(
        "INSERT INTO `user`(id,username,password,name,utype,company_id,status,create_time) VALUES(#{id},#{username},#{passwordHash},#{name},#{userType},#{companyId}, '1',CURRENT_TIMESTAMP)"
    )
    int insertUser(
        @Param("id") String id,
        @Param("username") String username,
        @Param("passwordHash") String passwordHash,
        @Param("name") String name,
        @Param("userType") String userType,
        @Param("companyId") String companyId
    );

    /** 建立老师与教学空间的归属关联。 */
    @Insert("INSERT INTO company_user(id,company_id,user_id) VALUES(#{id},#{companyId},#{userId})")
    int insertCompanyUser(
        @Param("id") String id,
        @Param("companyId") String companyId,
        @Param("userId") String userId
    );

    /** 老师开户只分配教学角色，账号管理权限属于平台管理员。 */
    @Insert(
        "INSERT INTO user_role(id,user_id,role_id,create_time,creator) SELECT #{id},#{userId},id,CURRENT_TIMESTAMP,#{creator} FROM role WHERE role_code='teacher' AND status='1'"
    )
    int assignTeacherRole(
        @Param("id") String id,
        @Param("userId") String userId,
        @Param("creator") String creator
    );

    /** 平台管理员查询全部老师，不返回密码。 */
    @Select("SELECT COUNT(*) FROM `user` WHERE utype='10202'")
    Long countTeachers();

    @Select(
        "SELECT u.id,u.username,u.name,u.status,u.company_id AS companyId,c.name AS companyName,u.create_time AS createTime FROM `user` u LEFT JOIN company c ON c.id=u.company_id WHERE u.utype='10202' ORDER BY u.create_time DESC,u.id DESC LIMIT 10 OFFSET #{offset}"
    )
    List<Map<String, Object>> selectTeachers(@Param("offset") Long offset);

    @Select("SELECT COUNT(*) FROM `user` WHERE username=#{username}")
    Long countUsername(@Param("username") String username);

    /** 复用原申请表及唯一约束，密码只保存散列。 */
    @Insert(
        "INSERT INTO institution_application(id,applicant_id,company_name,contact,mobile,email,intro,admin_username,admin_name,password_hash) VALUES(#{id},#{applicantId},#{companyName},#{contact},#{mobile},#{email},#{intro},#{adminUsername},#{adminName},#{passwordHash})"
    )
    int insertInstitutionApplication(
        @Param("id") String id,
        @Param("applicantId") String applicantId,
        @Param("companyName") String companyName,
        @Param("contact") String contact,
        @Param("mobile") String mobile,
        @Param("email") String email,
        @Param("intro") String intro,
        @Param("adminUsername") String adminUsername,
        @Param("adminName") String adminName,
        @Param("passwordHash") String passwordHash
    );

    /** 平台管理操作实时核对账号启用状态和显式管理角色。 */
    @Select(
        "SELECT COUNT(*) FROM `user` u WHERE u.id=#{userId} AND u.utype='10203' AND u.status='1' AND EXISTS(SELECT 1 FROM user_role ur JOIN role r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.role_code IN ('admin','super','reviewer') AND r.status='1')"
    )
    Integer countPlatformAdmin(@Param("userId") String userId);

    @Select("SELECT * FROM institution_application WHERE id=#{id} FOR UPDATE")
    List<Map<String, Object>> selectInstitutionApplicationForUpdate(@Param("id") String id);

    @Insert(
        "INSERT INTO company(id,name,linkname,mobile,email,intro,status) VALUES(#{id},#{name},#{contact},#{mobile},#{email},#{intro},'1')"
    )
    int insertCompany(
        @Param("id") String id,
        @Param("name") String name,
        @Param("contact") String contact,
        @Param("mobile") String mobile,
        @Param("email") String email,
        @Param("intro") String intro
    );

    /** 历史方法名保留，实际开户仅为老师类型，角色由事务中的 assignTeacherRole 分配。 */
    @Insert(
        "INSERT INTO `user`(id,username,password,name,utype,company_id,status,create_time) VALUES(#{id},#{username},#{passwordHash},#{name},'10202',#{companyId},'1',CURRENT_TIMESTAMP)"
    )
    int insertInstitutionAdmin(
        @Param("id") String id,
        @Param("username") String username,
        @Param("passwordHash") String passwordHash,
        @Param("name") String name,
        @Param("companyId") String companyId
    );

    /** 保存审核结论与创建结果，并清除申请中的临时密码散列。 */
    @Update(
        "UPDATE institution_application SET status=#{status},reason=#{reason},reviewer_id=#{reviewerId},reviewed_at=CURRENT_TIMESTAMP,company_id=#{companyId},admin_user_id=#{adminUserId},password_hash=NULL WHERE id=#{id}"
    )
    int reviewInstitutionApplication(
        @Param("status") String status,
        @Param("reason") String reason,
        @Param("reviewerId") String reviewerId,
        @Param("companyId") String companyId,
        @Param("adminUserId") String adminUserId,
        @Param("id") String id
    );

    /** 按权限限定申请人，状态参数为空时保留全部状态。 */
    @Select(
        "<script>SELECT COUNT(*) FROM institution_application <where><if test='applicantId != null'>applicant_id=#{applicantId}</if><if test='status != null'> AND status=#{status}</if></where> </script>"
    )
    Long countInstitutionApplications(
        @Param("applicantId") String applicantId,
        @Param("status") String status
    );

    /** 分页查询老师申请，沿用表字段别名，不返回密码散列。 */
    @Select(
        "<script>SELECT id,company_name AS companyName,contact,mobile,email,intro,admin_username AS adminUsername,admin_name AS adminName,status,reason,reviewer_id AS reviewerId,reviewed_at AS reviewedAt,company_id AS companyId,created_at AS createdAt FROM institution_application <where><if test='applicantId != null'>applicant_id=#{applicantId}</if><if test='status != null'> AND status=#{status}</if></where>  ORDER BY created_at DESC,id DESC LIMIT 10 OFFSET #{offset}</script>"
    )
    List<Map<String, Object>> selectInstitutionApplications(
        @Param("applicantId") String applicantId,
        @Param("status") String status,
        @Param("offset") Long offset
    );
}
