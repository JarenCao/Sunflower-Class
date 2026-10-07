-- UTF-8：全新环境初始化结构及公开字典，不包含用户、密码、订单或课程业务数据。
-- 历史迁移脚本已移除；现有环境需制定专用升级方案，CREATE IF NOT EXISTS不会升级旧表字段。
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS=0;
CREATE DATABASE IF NOT EXISTS `system` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `system`;
CREATE TABLE IF NOT EXISTS `system`.`dictionary` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id标识',
  `name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '数据字典名称',
  `code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '数据字典代码',
  `item_values` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '数据字典项--json格式\n  ',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `tb_code_unique` (`code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='数据字典';

CREATE DATABASE IF NOT EXISTS `users` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `users`;
CREATE TABLE IF NOT EXISTS `users`.`company` (
  `id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `linkname` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '联系人名称',
  `name` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '名称',
  `mobile` varchar(11) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `email` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `intro` varchar(512) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '简介',
  `logo` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT 'logo',
  `identitypic` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '身份证照片',
  `worktype` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '工具性质',
  `businesspic` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '营业执照',
  `status` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '企业状态',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `users`.`company_user` (
  `id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `company_id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `user_id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `company_user_unique` (`company_id`,`user_id`) USING BTREE,
  KEY `company_user_user_id` (`user_id`) USING BTREE,
  CONSTRAINT `company_user_company_id` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `company_user_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `users`.`institution_application` (
  `id` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `applicant_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `company_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contact` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `mobile` varchar(11) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `intro` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `admin_username` varchar(45) COLLATE utf8mb4_unicode_ci NOT NULL,
  `admin_name` varchar(45) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_hash` varchar(96) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '40101',
  `reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reviewer_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `company_id` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `admin_user_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `active_applicant` varchar(64) COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case when (`status` = _utf8mb4'40101') then `applicant_id` else NULL end)) STORED,
  `reserved_username` varchar(45) COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case when (`status` in (_utf8mb4'40101',_utf8mb4'40102')) then `admin_username` else NULL end)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `application_pending_applicant` (`active_applicant`),
  UNIQUE KEY `application_reserved_username` (`reserved_username`),
  KEY `application_queue` (`status`,`created_at`),
  KEY `application_owner` (`applicant_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `users`.`menu` (
  `id` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `code` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '菜单编码',
  `p_id` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '父菜单ID',
  `menu_name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '名称',
  `url` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '请求地址',
  `is_menu` char(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '是否是菜单',
  `level` int DEFAULT NULL COMMENT '菜单层级',
  `sort` int DEFAULT NULL COMMENT '菜单排序',
  `status` char(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `icon` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `FK_CODE` (`code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `users`.`oauth_access_token` (
  `token_id` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `token` blob,
  `authentication_id` varchar(48) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `user_name` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `client_id` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `authentication` blob,
  `refresh_token` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  PRIMARY KEY (`authentication_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `users`.`oauth_approvals` (
  `userId` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `clientId` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `scope` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `status` varchar(10) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `expiresAt` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `lastModifiedAt` timestamp NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `users`.`oauth_client_details` (
  `client_id` varchar(48) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `resource_ids` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `client_secret` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `scope` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `authorized_grant_types` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `web_server_redirect_uri` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `authorities` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `access_token_validity` int DEFAULT NULL,
  `refresh_token_validity` int DEFAULT NULL,
  `additional_information` varchar(4096) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `autoapprove` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  PRIMARY KEY (`client_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `users`.`oauth_client_token` (
  `token_id` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `token` blob,
  `authentication_id` varchar(48) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `user_name` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `client_id` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  PRIMARY KEY (`authentication_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `users`.`oauth_code` (
  `code` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `authentication` blob
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `users`.`oauth_refresh_token` (
  `token_id` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `token` blob,
  `authentication` blob
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `users`.`permission` (
  `id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `role_id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `menu_id` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `permission_unique` (`role_id`,`menu_id`) USING BTREE,
  KEY `permission_menu_id` (`menu_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `users`.`role` (
  `id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `role_name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `role_code` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `description` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `status` char(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `unique_role_name` (`role_name`) USING BTREE,
  UNIQUE KEY `unique_role_value` (`role_code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `users`.`teacher` (
  `id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `user_id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '用户id',
  `name` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '称呼',
  `intro` varchar(512) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '个人简介',
  `resume` varchar(1024) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '个人简历',
  `pic` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '老师照片',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `teacher_user_id` (`user_id`) USING BTREE,
  CONSTRAINT `teacher_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `users`.`user` (
  `id` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `username` varchar(45) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `password` varchar(96) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `salt` varchar(45) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `wx_unionid` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '微信unionid',
  `nickname` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '昵称',
  `name` varchar(45) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `userpic` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '头像',
  `company_id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `utype` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `birthday` datetime DEFAULT NULL,
  `sex` char(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `email` varchar(45) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `cellphone` varchar(45) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `qq` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `status` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '用户状态',
  `create_time` datetime NOT NULL,
  `update_time` datetime DEFAULT NULL,
  `failed_attempts` int NOT NULL DEFAULT '0',
  `locked_until` datetime DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `unique_user_username` (`username`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `users`.`user_role` (
  `id` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `user_id` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `role_id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `creator` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `user_role_user_id` (`user_id`) USING BTREE,
  KEY `user_role_role_id` (`role_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;

CREATE DATABASE IF NOT EXISTS `class` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `class`;
CREATE TABLE IF NOT EXISTS `class`.`course_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` bigint NOT NULL COMMENT '课程id',
  `audit_mind` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审核意见',
  `audit_status` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '审核状态',
  `audit_people` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审核人',
  `audit_date` datetime DEFAULT NULL COMMENT '审核时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `class`.`course_base` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `company_id` bigint NOT NULL COMMENT '机构ID',
  `company_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '机构名称',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程名称',
  `users` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '适用人群',
  `tags` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '课程标签',
  `mt` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '大分类',
  `st` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '小分类',
  `grade` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程等级',
  `teachmode` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '教育模式(common普通，record 录播，live直播等）',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '课程介绍',
  `pic` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '课程图片',
  `create_date` datetime DEFAULT NULL COMMENT '创建时间',
  `change_date` datetime DEFAULT NULL COMMENT '修改时间',
  `create_people` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `change_people` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `audit_status` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '审核状态',
  `status` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '30501' COMMENT '课程发布状态 未发布  已发布 下线',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='课程基本信息';

CREATE TABLE IF NOT EXISTS `class`.`course_category` (
  `id` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键',
  `name` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类名称',
  `label` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类标签默认和名称一样',
  `parentid` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '父结点id（第一级的父节点是0，自关联字段id）',
  `is_show` tinyint DEFAULT NULL COMMENT '是否显示',
  `orderby` int DEFAULT NULL COMMENT '排序字段',
  `is_leaf` tinyint DEFAULT NULL COMMENT '是否叶子',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='课程分类';

CREATE TABLE IF NOT EXISTS `class`.`course_market` (
  `id` bigint NOT NULL COMMENT '主键，课程id',
  `charge` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '收费规则，对应数据字典',
  `price` decimal(10,2) DEFAULT NULL COMMENT '现价',
  `original_price` decimal(10,2) DEFAULT NULL COMMENT '原价',
  `qq` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '咨询qq',
  `wechat` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '微信',
  `phone` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '电话',
  `valid_days` int DEFAULT NULL COMMENT '有效期天数',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='课程营销信息';

CREATE TABLE IF NOT EXISTS `class`.`course_publish` (
  `id` bigint NOT NULL COMMENT '主键',
  `company_id` bigint NOT NULL COMMENT '机构ID',
  `company_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '公司名称',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程名称',
  `users` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '适用人群',
  `tags` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签',
  `username` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `mt` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '大分类',
  `mt_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '大分类名称',
  `st` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '小分类',
  `st_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '小分类名称',
  `grade` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程等级',
  `teachmode` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '教育模式',
  `pic` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程图片',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '课程介绍',
  `market` text COLLATE utf8mb4_unicode_ci COMMENT '课程营销信息，json格式',
  `teachplan` text COLLATE utf8mb4_unicode_ci COMMENT '所有课程计划，json格式',
  `teachers` text COLLATE utf8mb4_unicode_ci COMMENT '教师信息，json格式',
  `create_date` datetime DEFAULT NULL COMMENT '发布时间',
  `online_date` datetime DEFAULT NULL COMMENT '上架时间',
  `offline_date` datetime DEFAULT NULL COMMENT '下架时间',
  `status` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT '30501' COMMENT '发布状态',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `charge` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '收费规则，对应数据字典--203',
  `price` decimal(10,2) DEFAULT NULL COMMENT '现价',
  `original_price` decimal(10,2) DEFAULT NULL COMMENT '原价',
  `valid_days` int DEFAULT NULL COMMENT '课程有效期天数',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='课程发布';

CREATE TABLE IF NOT EXISTS `class`.`course_publish_pre` (
  `id` bigint NOT NULL COMMENT '主键',
  `company_id` bigint NOT NULL COMMENT '机构ID',
  `company_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '公司名称',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程名称',
  `users` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '适用人群',
  `tags` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签',
  `username` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `mt` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '大分类',
  `mt_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '大分类名称',
  `st` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '小分类',
  `st_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '小分类名称',
  `grade` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程等级',
  `teachmode` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '教育模式',
  `pic` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程图片',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '课程介绍',
  `market` text COLLATE utf8mb4_unicode_ci COMMENT '课程营销信息，json格式',
  `teachplan` text COLLATE utf8mb4_unicode_ci COMMENT '所有课程计划，json格式',
  `teachers` text COLLATE utf8mb4_unicode_ci COMMENT '教师信息，json格式',
  `create_date` datetime DEFAULT NULL COMMENT '提交时间',
  `audit_date` datetime DEFAULT NULL COMMENT '审核时间',
  `status` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT '30402' COMMENT '状态',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `charge` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '收费规则，对应数据字典--203',
  `price` decimal(10,2) DEFAULT NULL COMMENT '现价',
  `original_price` decimal(10,2) DEFAULT NULL COMMENT '原价',
  `valid_days` int DEFAULT NULL COMMENT '课程有效期天数',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='课程发布';

CREATE TABLE IF NOT EXISTS `class`.`course_teacher` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `course_id` bigint DEFAULT NULL COMMENT '课程标识',
  `teacher_name` varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '教师标识',
  `position` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '教师职位',
  `introduction` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '教师简介',
  `photograph` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '照片',
  `create_date` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `courseid_teacherId_unique` (`course_id`,`teacher_name`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='课程-教师关系表';

CREATE TABLE IF NOT EXISTS `class`.`mq_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息id',
  `message_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息类型代码: course_publish ,  media_test',
  `business_key1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联业务信息',
  `business_key2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联业务信息',
  `business_key3` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联业务信息',
  `execute_num` int unsigned NOT NULL DEFAULT '0' COMMENT '通知次数',
  `state` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '处理状态，0:初始，1:成功',
  `returnfailure_date` datetime DEFAULT NULL COMMENT '回复失败时间',
  `returnsuccess_date` datetime DEFAULT NULL COMMENT '回复成功时间',
  `returnfailure_msg` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '回复失败内容',
  `execute_date` datetime DEFAULT NULL COMMENT '最近通知时间',
  `stage_state1` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '阶段1处理状态, 0:初始，1:成功',
  `stage_state2` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '阶段2处理状态, 0:初始，1:成功',
  `stage_state3` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '阶段3处理状态, 0:初始，1:成功',
  `stage_state4` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '阶段4处理状态, 0:初始，1:成功',
  `payload` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '课程发布或下架时的正式快照',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `class`.`mq_message_history` (
  `id` bigint NOT NULL COMMENT '消息id',
  `message_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息类型代码',
  `business_key1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联业务信息',
  `business_key2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联业务信息',
  `business_key3` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联业务信息',
  `execute_num` int unsigned DEFAULT NULL COMMENT '通知次数',
  `state` int(10) unsigned zerofill DEFAULT NULL COMMENT '处理状态，0:初始，1:成功，2:失败',
  `returnfailure_date` datetime DEFAULT NULL COMMENT '回复失败时间',
  `returnsuccess_date` datetime DEFAULT NULL COMMENT '回复成功时间',
  `returnfailure_msg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '回复失败内容',
  `execute_date` datetime DEFAULT NULL COMMENT '最近通知时间',
  `stage_state1` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `stage_state2` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `stage_state3` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `stage_state4` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `class`.`teachplan` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pname` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程计划名称',
  `parentid` bigint NOT NULL COMMENT '课程计划父级Id',
  `grade` smallint NOT NULL COMMENT '层级，分为1、2、3级',
  `media_type` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '课程类型:1视频、2文档',
  `start_time` datetime DEFAULT NULL COMMENT '开始直播时间',
  `end_time` datetime DEFAULT NULL COMMENT '直播结束时间',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '章节及课程时介绍',
  `timelength` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '时长，单位时:分:秒',
  `orderby` int DEFAULT '0' COMMENT '排序字段',
  `course_id` bigint NOT NULL COMMENT '课程标识',
  `course_pub_id` bigint DEFAULT NULL COMMENT '课程发布标识',
  `status` int NOT NULL DEFAULT '10101' COMMENT '状态（1正常  0删除）',
  `is_preview` char(1) COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '是否支持试学或预览（试看）',
  `create_date` datetime DEFAULT NULL COMMENT '创建时间',
  `change_date` datetime DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='课程计划';

CREATE TABLE IF NOT EXISTS `class`.`teachplan_media` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `media_id` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '媒资文件id',
  `teachplan_id` bigint NOT NULL COMMENT '课程计划标识',
  `course_id` bigint NOT NULL COMMENT '课程标识',
  `media_fileName` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '媒资文件原始名称',
  `create_date` datetime DEFAULT NULL,
  `create_people` varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `change_people` varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `class`.`teachplan_work` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `work_id` bigint NOT NULL COMMENT '作业信息标识',
  `work_title` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作业标题',
  `teachplan_id` bigint NOT NULL COMMENT '课程计划标识',
  `course_id` bigint DEFAULT NULL COMMENT '课程标识',
  `create_date` datetime DEFAULT NULL,
  `course_pub_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

CREATE DATABASE IF NOT EXISTS `media` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `media`;
CREATE TABLE IF NOT EXISTS `media`.`media_files` (
  `id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '文件id,md5值',
  `company_id` bigint DEFAULT NULL COMMENT '机构ID',
  `company_name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '机构名称',
  `filename` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '文件名称',
  `file_type` varchar(12) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '文件类型（图片、文档，视频）',
  `tags` varchar(120) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '标签',
  `bucket` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '存储目录',
  `file_path` varchar(512) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '存储路径',
  `file_id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '文件id',
  `url` varchar(1024) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '媒资文件访问地址',
  `username` varchar(60) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '上传人',
  `create_date` datetime DEFAULT NULL COMMENT '上传时间',
  `change_date` datetime DEFAULT NULL COMMENT '修改时间',
  `status` varchar(12) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '20301' COMMENT '状态,1:正常，0:不展示',
  `remark` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',
  `audit_status` varchar(12) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '审核状态',
  `audit_mind` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '审核意见',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小',
  `delete_error` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `unique_fileid` (`file_id`) USING BTREE COMMENT '文件id唯一索引 '
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='媒资信息';

CREATE TABLE IF NOT EXISTS `media`.`media_process` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `file_id` varchar(120) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '文件标识',
  `filename` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '文件名称',
  `bucket` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '存储桶',
  `file_path` varchar(512) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '存储路径',
  `status` varchar(12) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '状态,1:未处理，2：处理成功  3处理失败',
  `create_date` datetime NOT NULL COMMENT '上传时间',
  `finish_date` datetime DEFAULT NULL COMMENT '完成时间',
  `fail_count` int DEFAULT '0' COMMENT '失败次数',
  `url` varchar(1024) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '媒资文件访问地址',
  `errormsg` varchar(1024) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '失败原因',
  `retry_at` datetime DEFAULT NULL,
  `processing_at` datetime DEFAULT NULL,
  `dispatch_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `unique_fileid` (`file_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `media`.`media_process_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `file_id` varchar(120) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '文件标识',
  `filename` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '文件名称',
  `bucket` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '存储源',
  `status` varchar(12) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '状态,1:未处理，2：处理成功  3处理失败',
  `create_date` datetime NOT NULL COMMENT '上传时间',
  `finish_date` datetime NOT NULL COMMENT '完成时间',
  `url` varchar(1024) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '媒资文件访问地址',
  `fail_count` int DEFAULT '0' COMMENT '失败次数',
  `file_path` varchar(512) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '文件路径',
  `errormsg` varchar(1024) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '失败原因',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `media`.`mq_message` (
  `id` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '消息id',
  `message_type` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '消息类型代码',
  `business_key1` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '关联业务信息',
  `business_key2` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '关联业务信息',
  `business_key3` varchar(512) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '关联业务信息',
  `mq_host` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '消息队列主机',
  `mq_port` int NOT NULL COMMENT '消息队列端口',
  `mq_virtualhost` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '消息队列虚拟主机',
  `mq_queue` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '队列名称',
  `inform_num` int unsigned NOT NULL COMMENT '通知次数',
  `state` char(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '处理状态，0:初始，1:成功',
  `returnfailure_date` datetime DEFAULT NULL COMMENT '回复失败时间',
  `returnsuccess_date` datetime DEFAULT NULL COMMENT '回复成功时间',
  `returnfailure_msg` varchar(2048) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '回复失败内容',
  `inform_date` datetime DEFAULT NULL COMMENT '最近通知时间',
  `stage_state1` char(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '阶段1处理状态, 0:初始，1:成功',
  `stage_state2` char(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '阶段2处理状态, 0:初始，1:成功',
  `stage_state3` char(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '阶段3处理状态, 0:初始，1:成功',
  `stage_state4` char(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '阶段4处理状态, 0:初始，1:成功',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `media`.`mq_message_history` (
  `id` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '消息id',
  `message_type` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '消息类型代码',
  `business_key1` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '关联业务信息',
  `business_key2` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '关联业务信息',
  `business_key3` varchar(512) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '关联业务信息',
  `mq_host` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '消息队列主机',
  `mq_port` int NOT NULL COMMENT '消息队列端口',
  `mq_virtualhost` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '消息队列虚拟主机',
  `mq_queue` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '队列名称',
  `inform_num` int(10) unsigned zerofill DEFAULT NULL COMMENT '通知次数',
  `state` int(10) unsigned zerofill DEFAULT NULL COMMENT '处理状态，0:初始，1:成功，2:失败',
  `returnfailure_date` datetime DEFAULT NULL COMMENT '回复失败时间',
  `returnsuccess_date` datetime DEFAULT NULL COMMENT '回复成功时间',
  `returnfailure_msg` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '回复失败内容',
  `inform_date` datetime DEFAULT NULL COMMENT '最近通知时间',
  `stage_state1` char(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `stage_state2` char(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `stage_state3` char(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  `stage_state4` char(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC;

CREATE DATABASE IF NOT EXISTS `learning` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `learning`;
CREATE TABLE IF NOT EXISTS `learning`.`choose_course` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `course_id` bigint NOT NULL COMMENT '课程id',
  `course_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程名称',
  `user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户id',
  `company_id` bigint NOT NULL COMMENT '机构id',
  `order_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选课类型，70101免费/70102收费',
  `create_date` datetime NOT NULL COMMENT '添加时间',
  `course_price` decimal(10,2) NOT NULL COMMENT '课程价格，单位元',
  `valid_days` int NOT NULL COMMENT '课程有效期(天)',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选课状态，70201成功/70202待支付',
  `validtime_start` datetime DEFAULT NULL COMMENT '开始服务时间，待支付不启用',
  `validtime_end` datetime DEFAULT NULL COMMENT '结束服务时间，待支付不启用',
  `remarks` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `learning`.`course_enrollment` (
  `user_id` varchar(64) NOT NULL,
  `course_id` bigint NOT NULL,
  `course_name` varchar(256) NOT NULL,
  `enrollment_type` varchar(5) NOT NULL COMMENT '70101 免费；70102 收费',
  `status` varchar(5) NOT NULL COMMENT '70201 选课成功；70202 待支付',
  `price` decimal(10,2) NOT NULL,
  `valid_days` int DEFAULT NULL COMMENT '发布快照有效天数；空或零表示不限',
  `expires_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`course_id`),
  KEY `idx_enrollment_created` (`user_id`,`created_at`,`course_id`),
  CONSTRAINT `chk_enrollment_days` CHECK (((`valid_days` is null) or (`valid_days` >= 0))),
  CONSTRAINT `chk_enrollment_price` CHECK ((`price` >= 0)),
  CONSTRAINT `chk_enrollment_status` CHECK ((`status` in (_utf8mb4'70201',_utf8mb4'70202'))),
  CONSTRAINT `chk_enrollment_type` CHECK ((`enrollment_type` in (_utf8mb4'70101',_utf8mb4'70102')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `learning`.`course_tables` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `choose_course_id` bigint NOT NULL COMMENT '选课记录id',
  `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户id',
  `course_id` bigint NOT NULL COMMENT '课程id',
  `company_id` bigint NOT NULL COMMENT '机构id',
  `course_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程名称',
  `course_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '课程类型，70101免费/70102收费',
  `create_date` datetime NOT NULL COMMENT '添加时间',
  `validtime_start` datetime DEFAULT NULL COMMENT '开始服务时间',
  `validtime_end` datetime NOT NULL COMMENT '到期时间',
  `update_date` datetime DEFAULT NULL COMMENT '更新时间',
  `remarks` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `course_tables_unique` (`user_id`,`course_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `learning`.`learn_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` bigint NOT NULL COMMENT '课程id',
  `course_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '课程名称',
  `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户id',
  `learn_date` datetime DEFAULT NULL COMMENT '最近学习时间',
  `learn_length` bigint DEFAULT NULL COMMENT '学习时长',
  `teachplan_id` bigint DEFAULT NULL COMMENT '章节id',
  `teachplan_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '章节名称',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `learn_record_unique` (`course_id`,`user_id`,`teachplan_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `learning`.`learning_course` (
  `id` bigint NOT NULL,
  `event_id` bigint NOT NULL,
  `status` char(5) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `tags` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `payload` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_search_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `learning`.`payment_processed` (
  `order_id` bigint NOT NULL,
  `user_id` varchar(64) NOT NULL,
  `course_id` bigint NOT NULL,
  `payload` json NOT NULL,
  `processed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS `orders` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `orders`;
CREATE TABLE IF NOT EXISTS `orders`.`mq_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息id',
  `message_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息类型代码: course_publish ,  media_test',
  `business_key1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联业务信息',
  `business_key2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联业务信息',
  `business_key3` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联业务信息',
  `execute_num` int unsigned NOT NULL DEFAULT '0' COMMENT '通知次数',
  `state` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '处理状态，0:初始，1:成功',
  `returnfailure_date` datetime DEFAULT NULL COMMENT '回复失败时间',
  `returnsuccess_date` datetime DEFAULT NULL COMMENT '回复成功时间',
  `returnfailure_msg` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '回复失败内容',
  `execute_date` datetime DEFAULT NULL COMMENT '最近通知时间',
  `stage_state1` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '阶段1处理状态, 0:初始，1:成功',
  `stage_state2` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '阶段2处理状态, 0:初始，1:成功',
  `stage_state3` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '阶段3处理状态, 0:初始，1:成功',
  `stage_state4` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '阶段4处理状态, 0:初始，1:成功',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `orders`.`mq_message_history` (
  `id` bigint NOT NULL COMMENT '消息id',
  `message_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息类型代码',
  `business_key1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联业务信息',
  `business_key2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联业务信息',
  `business_key3` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联业务信息',
  `execute_num` int unsigned DEFAULT NULL COMMENT '通知次数',
  `state` tinyint unsigned DEFAULT NULL COMMENT '处理状态，0:初始，1:成功，2:失败；NULL:历史结果未知，不自动重放',
  `returnfailure_date` datetime DEFAULT NULL COMMENT '回复失败时间',
  `returnsuccess_date` datetime DEFAULT NULL COMMENT '回复成功时间',
  `returnfailure_msg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '回复失败内容',
  `execute_date` datetime DEFAULT NULL COMMENT '最近通知时间',
  `stage_state1` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `stage_state2` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `stage_state3` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `stage_state4` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `orders`.`orders` (
  `id` bigint NOT NULL COMMENT '订单号',
  `total_price` decimal(10,2) NOT NULL,
  `create_date` datetime NOT NULL COMMENT '创建时间',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '交易状态',
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `order_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单类型',
  `order_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '订单名称',
  `order_descrip` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单描述',
  `order_detail` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单明细json',
  `out_business_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '外部系统业务id',
  `course_id` bigint DEFAULT NULL,
  `expires_at` datetime DEFAULT NULL,
  `qr_code` text COLLATE utf8mb4_unicode_ci,
  `active_course` bigint GENERATED ALWAYS AS ((case when (`status` in (_utf8mb4'60201',_utf8mb4'60202',_utf8mb4'60205')) then `course_id` else NULL end)) STORED,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `orders_unioue` (`out_business_id`) USING BTREE COMMENT '外部系统的业务id',
  UNIQUE KEY `uk_active_student_course` (`user_id`,`active_course`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `orders`.`orders_goods` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL COMMENT '订单号',
  `goods_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商品id',
  `goods_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '商品类型',
  `goods_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商品名称',
  `goods_price` decimal(10,2) NOT NULL COMMENT '商品交易价，单位元',
  `goods_detail` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '商品详情json',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_orders_goods_order_id` (`order_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `orders`.`pay_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `pay_no` bigint NOT NULL COMMENT '本系统支付交易号',
  `out_pay_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '第三方支付交易流水号',
  `out_pay_channel` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '第三方支付渠道编号',
  `order_id` bigint NOT NULL COMMENT '商品订单号',
  `order_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单名称',
  `total_price` decimal(10,2) NOT NULL,
  `currency` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '币种CNY',
  `create_date` datetime NOT NULL COMMENT '创建时间',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '支付状态',
  `pay_success_time` datetime DEFAULT NULL COMMENT '支付成功时间',
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `pay_order_unioue2` (`pay_no`) USING BTREE COMMENT '本系统支付交易号',
  UNIQUE KEY `pay_order_unioue` (`out_pay_no`) USING BTREE COMMENT '第三方支付订单号',
  KEY `idx_pay_record_order_id` (`order_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `orders`.`payment_event` (
  `order_id` bigint NOT NULL,
  `payload` json NOT NULL,
  `delivered` tinyint NOT NULL DEFAULT '0',
  `attempts` int NOT NULL DEFAULT '0',
  `last_error` varchar(128) DEFAULT NULL,
  `next_attempt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `delivered_at` datetime DEFAULT NULL,
  PRIMARY KEY (`order_id`),
  KEY `idx_payment_event_retry` (`delivered`,`next_attempt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO `system`.`dictionary` (`id`,`name`,`code`,`item_values`) VALUES (12,'公共属性类型','101','[{"code":"10101","desc":"使用态"},{"code":"10102","desc":"删除态"},{"code":"10103","desc":"暂时态"}]');
INSERT IGNORE INTO `system`.`dictionary` (`id`,`name`,`code`,`item_values`) VALUES (13,'对象的审核状态','202','[{"code":"20201","desc":"审核未通过"},{"code":"20202","desc":"未审核"},{"code":"20203","desc":"审核通过"}]');
INSERT IGNORE INTO `system`.`dictionary` (`id`,`name`,`code`,`item_values`) VALUES (14,'资源类型','201','[{"code":"20101","desc":"图片"},{"code":"20102","desc":"视频"},{"code":"20103","desc":"其它"}]');
INSERT IGNORE INTO `system`.`dictionary` (`id`,`name`,`code`,`item_values`) VALUES (15,'课程审核状态','304','[{"code":"30401","desc":"审核未通过"},{"code":"30402","desc":"未提交"},{"code":"30403","desc":"已提交"},{"code":"30404","desc":"审核通过"}]');
INSERT IGNORE INTO `system`.`dictionary` (`id`,`name`,`code`,`item_values`) VALUES (16,'课程收费情况','302','[{"code":"30201","desc":"免费"},{"code":"30202","desc":"收费"}]');
INSERT IGNORE INTO `system`.`dictionary` (`id`,`name`,`code`,`item_values`) VALUES (17,'课程等级','303','[{"code":"30301","desc":"初级"},{"code":"30302","desc":"中级"},{"code":"30303","desc":"高级"}]');
INSERT IGNORE INTO `system`.`dictionary` (`id`,`name`,`code`,`item_values`) VALUES (18,'课程模式状态','301','[{"code":"30101","desc":"录播"},{"code":"30102","desc":"直播"}]');
INSERT IGNORE INTO `system`.`dictionary` (`id`,`name`,`code`,`item_values`) VALUES (19,'课程发布状态','305','[{"code":"30501","desc":"未发布"},{"code":"30502","desc":"已发布"},{"code":"30503","desc":"下线"}]');
INSERT IGNORE INTO `system`.`dictionary` (`id`,`name`,`code`,`item_values`) VALUES (20,'订单交易类型状态','602','[{"code":"60201","desc":"未支付"},{"code":"60202","desc":"已支付"},{"code":"60203","desc":"已关闭"},{"code":"60204","desc":"已退款"},{"code":"60205","desc":"已完成"}]');
INSERT IGNORE INTO `system`.`dictionary` (`id`,`name`,`code`,`item_values`) VALUES (21,'课程作业记录审批状态','401','[{"code":"40101","desc":"未提交"},{"code":"40102","desc":"待批改"},{"code":"40103","desc":"已批改"}]');
INSERT IGNORE INTO `system`.`dictionary` (`id`,`name`,`code`,`item_values`) VALUES (22,'消息通知状态','501','[{"code":"50101","desc":"未通知"},{"code":"50102","desc":"成功"}]');
INSERT IGNORE INTO `system`.`dictionary` (`id`,`name`,`code`,`item_values`) VALUES (23,'支付记录交易状态','603','[{"code":"60301","desc":"未支付"},{"code":"60302","desc":"已支付"},{"code":"60303","desc":"已退款"}]');
INSERT IGNORE INTO `system`.`dictionary` (`id`,`name`,`code`,`item_values`) VALUES (24,'业务订单类型','601','[{"code":"60101","desc":"购买课程"},{"code":"60102","desc":"学习资料"}]');
INSERT IGNORE INTO `system`.`dictionary` (`id`,`name`,`code`,`item_values`) VALUES (25,'第三方支付渠道编号','604','[{"code":"60401","desc":"微信支付"},{"code":"60402","desc":"支付宝"}]');
INSERT IGNORE INTO `system`.`dictionary` (`id`,`name`,`code`,`item_values`) VALUES (26,'选课类型','701','[{"code":"70101","desc":"免费课程"},{"code":"70102","desc":"收费课程"}]');
INSERT IGNORE INTO `system`.`dictionary` (`id`,`name`,`code`,`item_values`) VALUES (27,'选课状态','702','[{"code":"70201","desc":"选课成功"},{"code":"70202","desc":"待支付"}]');
INSERT IGNORE INTO `system`.`dictionary` (`id`,`name`,`code`,`item_values`) VALUES (28,'选课学习资格','703','[{"code":"70301","desc":"正常学习"},{"code":"70302","desc":"没有选课或选课后没有支付"},{"code":"70303","desc":"已过期需要申请续期或重新支付"}]');
INSERT IGNORE INTO `system`.`dictionary` (`id`,`name`,`code`,`item_values`) VALUES (29,'媒资处理状态','203','[{"code": "20300", "desc": "隐藏"}, {"code": "20301", "desc": "待处理"}, {"code": "20302", "desc": "可用"}, {"code": "20303", "desc": "处理失败"}, {"code": "20304", "desc": "处理中"}, {"code": "20305", "desc": "删除中"}]');
INSERT IGNORE INTO `system`.`dictionary` (`id`,`name`,`code`,`item_values`) VALUES (30,'用户类型','102','[{"code":"10201","desc":"学生"},{"code":"10202","desc":"机构"},{"code":"10203","desc":"管理员"}]');
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1','根结点','根结点','0',1,1,0);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-1','前端开发','前端开发','1',1,1,0);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-1-1','HTML/CSS','HTML/CSS','1-1',1,1,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-1-10','其它','其它','1-1',1,10,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-1-2','JavaScript','JavaScript','1-1',1,2,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-1-3','jQuery','jQuery','1-1',1,3,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-1-4','ExtJS','ExtJS','1-1',1,4,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-1-5','AngularJS','AngularJS','1-1',1,5,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-1-6','ReactJS','ReactJS','1-1',1,6,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-1-7','Bootstrap','Bootstrap','1-1',1,7,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-1-8','Node.js','Node.js','1-1',1,8,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-1-9','Vue','Vue','1-1',1,9,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-10','研发管理','研发管理','1',1,10,0);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-10-1','敏捷开发','敏捷开发','1-10',1,1,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-10-2','软件设计','软件设计','1-10',1,2,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-10-3','软件测试','软件测试','1-10',1,3,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-10-4','研发管理','研发管理','1-10',1,4,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-10-5','其它','其它','1-10',1,5,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-11','系统运维','系统运维','1',1,11,0);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-11-1','Linux','Linux','1-11',1,1,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-11-10','其它','其它','1-11',1,10,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-11-2','Windows','Windows','1-11',1,2,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-11-3','UNIX','UNIX','1-11',1,3,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-11-4','Mac OS','Mac OS','1-11',1,4,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-11-5','网络技术','网络技术','1-11',1,5,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-11-6','路由协议','路由协议','1-11',1,6,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-11-7','无线网络','无线网络','1-11',1,7,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-11-8','Ngnix','Ngnix','1-11',1,8,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-11-9','邮件服务器','邮件服务器','1-11',1,9,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-12','产品经理','产品经理','1',1,12,0);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-12-1','交互设计','交互设计','1-12',1,1,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-12-2','产品设计','产品设计','1-12',1,2,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-12-3','原型设计','原型设计','1-12',1,3,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-12-4','用户体验','用户体验','1-12',1,4,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-12-5','需求分析','需求分析','1-12',1,5,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-12-6','其它','其它','1-12',1,6,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-13','企业/办公/职场','企业/办公/职场','1',1,13,0);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-13-1','运营管理','运营管理','1-13',1,1,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-13-2','企业信息化','企业信息化','1-13',1,2,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-13-3','网络营销','网络营销','1-13',1,3,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-13-4','Office/WPS','Office/WPS','1-13',1,4,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-13-5','招聘/面试','招聘/面试','1-13',1,5,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-13-6','电子商务','电子商务','1-13',1,6,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-13-7','CRM','CRM','1-13',1,7,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-13-8','ERP','ERP','1-13',1,8,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-13-9','其它','其它','1-13',1,9,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-14','信息安全','信息安全','1',1,14,0);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-14-1','密码学/加密/破解','密码学/加密/破解','1-14',1,1,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-14-10','其它','其它','1-14',1,10,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-14-2','渗透测试','渗透测试','1-14',1,2,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-14-3','社会工程','社会工程','1-14',1,3,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-14-4','漏洞挖掘与利用','漏洞挖掘与利用','1-14',1,4,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-14-5','云安全','云安全','1-14',1,5,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-14-6','防护加固','防护加固','1-14',1,6,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-14-7','代码审计','代码审计','1-14',1,7,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-14-8','移动安全','移动安全','1-14',1,8,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-14-9','病毒木马','病毒木马','1-14',1,9,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-15','测试目录','测试目录','1',1,15,0);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-15-1','测试目录01','测试目录01','1-15',1,1,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-2','移动开发','移动开发','1',1,2,0);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-2-1','微信开发','微信开发','1-2',1,1,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-2-2','iOS','iOS','1-2',1,2,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-2-3','手游开发','手游开发','1-2',1,3,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-2-4','Swift','Swift','1-2',1,4,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-2-5','Android','Android','1-2',1,5,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-2-6','ReactNative','ReactNative','1-2',1,6,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-2-7','Cordova','Cordova','1-2',1,7,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-2-8','其它','其它','1-2',1,8,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-3','编程开发','编程开发','1',1,3,0);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-3-1','C/C++','C/C++','1-3',1,1,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-3-2','Java','Java','1-3',1,2,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-3-3','.NET','.NET','1-3',1,3,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-3-4','Objective-C','Objective-C','1-3',1,4,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-3-5','Go语言','Go语言','1-3',1,5,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-3-6','Python','Python','1-3',1,6,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-3-7','Ruby/Rails','Ruby/Rails','1-3',1,7,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-3-8','其它','其它','1-3',1,8,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-4','数据库','数据库','1',1,4,0);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-4-1','Oracle','Oracle','1-4',1,1,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-4-2','MySQL','MySQL','1-4',1,2,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-4-3','SQL Server','SQL Server','1-4',1,3,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-4-4','DB2','DB2','1-4',1,4,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-4-5','NoSQL','NoSQL','1-4',1,5,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-4-6','Mongo DB','Mongo DB','1-4',1,6,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-4-7','Hbase','Hbase','1-4',1,7,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-4-8','数据仓库','数据仓库','1-4',1,8,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-4-9','其它','其它','1-4',1,9,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-5','人工智能','人工智能','1',1,5,0);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-5-1','机器学习','机器学习','1-5',1,1,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-5-2','深度学习','深度学习','1-5',1,2,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-5-3','语音识别','语音识别','1-5',1,3,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-5-4','计算机视觉','计算机视觉','1-5',1,4,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-5-5','NLP','NLP','1-5',1,5,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-5-6','强化学习','强化学习','1-5',1,6,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-5-7','其它','其它','1-5',1,7,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-6','云计算/大数据','云计算/大数据','1',1,6,0);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-6-1','Spark','Spark','1-6',1,1,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-6-2','Hadoop','Hadoop','1-6',1,2,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-6-3','OpenStack','OpenStack','1-6',1,3,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-6-4','Docker/K8S','Docker/K8S','1-6',1,4,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-6-5','云计算基础架构','云计算基础架构','1-6',1,5,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-6-6','虚拟化技术','虚拟化技术','1-6',1,6,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-6-7','云平台','云平台','1-6',1,7,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-6-8','ELK','ELK','1-6',1,8,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-6-9','其它','其它','1-6',1,9,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-7','UI设计','UI设计','1',1,7,0);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-7-1','Photoshop','Photoshop','1-7',1,1,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-7-10','InDesign','InDesign','1-7',1,10,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-7-11','Pro/Engineer','Pro/Engineer','1-7',1,11,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-7-12','Cinema 4D','Cinema 4D','1-7',1,12,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-7-13','3D Studio','3D Studio','1-7',1,13,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-7-14','After Effects（AE）','After Effects（AE）','1-7',1,14,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-7-15','原画设计','原画设计','1-7',1,15,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-7-16','动画制作','动画制作','1-7',1,16,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-7-17','Dreamweaver','Dreamweaver','1-7',1,17,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-7-18','Axure','Axure','1-7',1,18,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-7-19','其它','其它','1-7',1,19,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-7-2','3Dmax','3Dmax','1-7',1,2,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-7-3','Illustrator','Illustrator','1-7',1,3,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-7-4','Flash','Flash','1-7',1,4,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-7-5','Maya','Maya','1-7',1,5,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-7-6','AUTOCAD','AUTOCAD','1-7',1,6,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-7-7','UG','UG','1-7',1,7,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-7-8','SolidWorks','SolidWorks','1-7',1,8,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-7-9','CorelDraw','CorelDraw','1-7',1,9,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-8','游戏开发','游戏开发','1',1,8,0);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-8-1','Cocos','Cocos','1-8',1,1,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-8-2','Unity3D','Unity3D','1-8',1,2,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-8-3','Flash','Flash','1-8',1,3,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-8-4','SpriteKit 2D','SpriteKit 2D','1-8',1,4,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-8-5','Unreal','Unreal','1-8',1,5,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-8-6','其它','其它','1-8',1,6,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-9','智能硬件/物联网','智能硬件/物联网','1',1,9,0);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-9-1','无线通信','无线通信','1-9',1,1,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-9-10','物联网技术','物联网技术','1-9',1,10,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-9-11','其它','其它','1-9',1,11,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-9-2','电子工程','电子工程','1-9',1,2,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-9-3','Arduino','Arduino','1-9',1,3,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-9-4','体感技术','体感技术','1-9',1,4,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-9-5','智能硬件','智能硬件','1-9',1,5,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-9-6','驱动/内核开发','驱动/内核开发','1-9',1,6,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-9-7','单片机/工控','单片机/工控','1-9',1,7,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-9-8','WinCE','WinCE','1-9',1,8,1);
INSERT IGNORE INTO `class`.`course_category` (`id`,`name`,`label`,`parentid`,`is_show`,`orderby`,`is_leaf`) VALUES ('1-9-9','嵌入式','嵌入式','1-9',1,9,1);
INSERT IGNORE INTO `users`.`role` (`id`,`role_name`,`role_code`,`description`,`create_time`,`update_time`,`status`) VALUES ('17','学生','student',NULL,'2026-10-01T00:00','2026-10-01T00:00','1');
INSERT IGNORE INTO `users`.`role` (`id`,`role_name`,`role_code`,`description`,`create_time`,`update_time`,`status`) VALUES ('18','老师','teacher',NULL,'2026-10-01T00:00','2026-10-01T00:00','1');
INSERT IGNORE INTO `users`.`role` (`id`,`role_name`,`role_code`,`description`,`create_time`,`update_time`,`status`) VALUES ('20','教学管理员','teachmanager',NULL,'2026-10-01T00:00','2026-10-01T00:00','1');
INSERT IGNORE INTO `users`.`role` (`id`,`role_name`,`role_code`,`description`,`create_time`,`update_time`,`status`) VALUES ('6','管理员','admin',NULL,'2026-10-01T00:00','2026-10-01T00:00','1');
INSERT IGNORE INTO `users`.`role` (`id`,`role_name`,`role_code`,`description`,`create_time`,`update_time`,`status`) VALUES ('8','超级管理员','super',NULL,'2026-10-01T00:00','2026-10-01T00:00','1');
INSERT IGNORE INTO `users`.`role` (`id`,`role_name`,`role_code`,`description`,`create_time`,`update_time`,`status`) VALUES ('reviewer','课程审核员','reviewer','只读提交快照并记录审核结论','2026-10-01T03:33:22',NULL,'1');
SET FOREIGN_KEY_CHECKS=1;
