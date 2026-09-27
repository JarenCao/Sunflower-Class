package com.sunflower_class.base.model;

/** ��λҵ���ֵ��룬�����ݿ��ֵ䱣��һ�£���ϸӳ��� docs/STATUS_CODES.md�� */
public final class BusinessCodes {

    /**
     * 禁止实例化常量容器；业务代码直接通过类名引用状态编码。
     */
    private BusinessCodes() {}

    // �γ̽�ѧģʽ
    public static final String TEACH_RECORDED = "30101";
    public static final String TEACH_LIVE = "30102";

    // �շѷ�ʽ
    public static final String CHARGE_FREE = "30201";
    public static final String CHARGE_PAID = "30202";

    // �γ̵ȼ�
    public static final String LEVEL_BEGINNER = "30301";
    public static final String LEVEL_INTERMEDIATE = "30302";
    public static final String LEVEL_ADVANCED = "30303";

    // �γ����״̬���뷢��״̬�ֱ�ά��
    public static final String AUDIT_REJECTED = "30401";
    public static final String AUDIT_DRAFT = "30402";
    public static final String AUDIT_PENDING = "30403";
    public static final String AUDIT_APPROVED = "30404";

    // �γ̷���״̬
    public static final String COURSE_DRAFT = "30501";
    public static final String COURSE_PUBLISHED = "30502";
    public static final String COURSE_OFFLINE = "30503";

    // ��Դ����
    public static final String FILE_IMAGE = "20101";
    public static final String FILE_VIDEO = "20102";
    public static final String FILE_OTHER = "20103";

    // ý�����״̬
    public static final String MEDIA_AUDIT_APPROVED = "20203";

    // ý�ʴ���״̬����ͨͼƬ�ϴ�����ã���Ƶת��ɹ������
    public static final String PROCESS_HIDDEN = "20300";
    public static final String PROCESS_WAITING = "20301";
    public static final String PROCESS_READY = "20302";
    public static final String PROCESS_FAILED = "20303";
    public static final String PROCESS_RUNNING = "20304";

    // ��ѧ�ƻ���¼״̬
    public static final int RECORD_ACTIVE = 10101;
    public static final int RECORD_DELETED = 10102;
}
