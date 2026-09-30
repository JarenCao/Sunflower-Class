package com.sunflower_class.base.model;

/** 统一业务字典编码，与数据库字典保持一致；详细映射见 docs/STATUS_CODES.md。 */
public final class BusinessCodes {

    /**
     * 禁止实例化常量容器；业务代码直接通过类名引用状态编码。
     */
    private BusinessCodes() {
    }

    // 课程教学模式：录播、直播。
    public static final String TEACH_RECORDED = "30101";
    public static final String TEACH_LIVE = "30102";

    // 收费方式：免费、收费。
    public static final String CHARGE_FREE = "30201";
    public static final String CHARGE_PAID = "30202";

    // 课程等级：初级、中级、高级。
    public static final String LEVEL_BEGINNER = "30301";
    public static final String LEVEL_INTERMEDIATE = "30302";
    public static final String LEVEL_ADVANCED = "30303";

    // 课程审核状态与发布状态分别维护，不共用状态字段。
    public static final String AUDIT_REJECTED = "30401";
    public static final String AUDIT_DRAFT = "30402";
    public static final String AUDIT_PENDING = "30403";
    public static final String AUDIT_APPROVED = "30404";

    // 课程发布状态：未发布、已发布、已下线。
    public static final String COURSE_DRAFT = "30501";
    public static final String COURSE_PUBLISHED = "30502";
    public static final String COURSE_OFFLINE = "30503";

    // 文件类型：图片、视频、其他。
    public static final String FILE_IMAGE = "20101";
    public static final String FILE_VIDEO = "20102";
    public static final String FILE_OTHER = "20103";

    // 媒资审核状态：审核通过。
    public static final String MEDIA_AUDIT_APPROVED = "20203";

    // 媒资处理状态：普通图片上传后可用，视频转码成功后可用。
    public static final String PROCESS_HIDDEN = "20300";
    public static final String PROCESS_WAITING = "20301";
    public static final String PROCESS_READY = "20302";
    public static final String PROCESS_FAILED = "20303";
    public static final String PROCESS_RUNNING = "20304";

    // 教学计划记录状态：正常、删除。
    public static final int RECORD_ACTIVE = 10101;
    public static final int RECORD_DELETED = 10102;
}
