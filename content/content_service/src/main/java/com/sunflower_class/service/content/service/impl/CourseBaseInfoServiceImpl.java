package com.sunflower_class.service.content.service.impl;

import static com.sunflower_class.base.model.BusinessCodes.AUDIT_APPROVED;
import static com.sunflower_class.base.model.BusinessCodes.AUDIT_DRAFT;
import static com.sunflower_class.base.model.BusinessCodes.AUDIT_PENDING;
import static com.sunflower_class.base.model.BusinessCodes.CHARGE_FREE;
import static com.sunflower_class.base.model.BusinessCodes.CHARGE_PAID;
import static com.sunflower_class.base.model.BusinessCodes.COURSE_DRAFT;
import static com.sunflower_class.base.model.BusinessCodes.COURSE_OFFLINE;
import static com.sunflower_class.base.model.BusinessCodes.COURSE_PUBLISHED;
import static com.sunflower_class.base.model.BusinessCodes.LEVEL_ADVANCED;
import static com.sunflower_class.base.model.BusinessCodes.LEVEL_BEGINNER;
import static com.sunflower_class.base.model.BusinessCodes.LEVEL_INTERMEDIATE;
import static com.sunflower_class.base.model.BusinessCodes.TEACH_LIVE;
import static com.sunflower_class.base.model.BusinessCodes.TEACH_RECORDED;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunflower_class.base.exception.GlobalException;
import com.sunflower_class.base.model.PageParams;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.base.security.CurrentUser;
import com.sunflower_class.base.utils.MediaObjectLock;
import com.sunflower_class.model.dto.AddCourseDto;
import com.sunflower_class.model.dto.CourseBaseInfoDto;
import com.sunflower_class.model.dto.CourseTeacherDto;
import com.sunflower_class.model.dto.EditCourseDto;
import com.sunflower_class.model.dto.QueryCourseParamsDto;
import com.sunflower_class.model.po.CourseBase;
import com.sunflower_class.model.po.CourseCategory;
import com.sunflower_class.model.po.CourseMarket;
import com.sunflower_class.model.po.CoursePublish;
import com.sunflower_class.model.po.CourseTeacher;
import com.sunflower_class.model.po.Teachplan;
import com.sunflower_class.model.po.TeachplanMedia;
import com.sunflower_class.service.content.mapper.CourseBaseMapper;
import com.sunflower_class.service.content.mapper.CourseCategoryMapper;
import com.sunflower_class.service.content.mapper.CourseMarketMapper;
import com.sunflower_class.service.content.mapper.CoursePublishMapper;
import com.sunflower_class.service.content.mapper.CoursePublishPreMapper;
import com.sunflower_class.service.content.mapper.CourseTeacherMapper;
import com.sunflower_class.service.content.mapper.TeachplanMapper;
import com.sunflower_class.service.content.mapper.TeachplanMediaMapper;
import com.sunflower_class.service.content.service.AssociationMediaService;
import com.sunflower_class.service.content.service.CourseBaseInfoService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * 课程基础与营销信息的业务实现，负责参数校验、机构条件、分页组合及保存。
 */
@Slf4j
@Service
@Transactional
public class CourseBaseInfoServiceImpl implements CourseBaseInfoService {

    @Autowired
    private DataSource coverLockSource;

    @Autowired
    private TransactionTemplate coverTransactions;

    @Autowired
    private DiscoveryClient coverDiscovery;

    @Autowired
    private AssociationMediaService coverMedia;

    /** 解析本地图片并持锁提交课程引用，避免并发删除后仍写入失效封面。 */
    private <T> T saveWithCover(String pic, Supplier<T> save) {
        if (pic == null || pic.isBlank()) return coverTransactions.execute(transaction ->
            save.get()
        );
        List<ServiceInstance> instances = coverDiscovery.getInstances("media-api");
        if (instances.isEmpty()) GlobalException.cast("媒资服务不可用，暂不能核对封面");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        Map info = RestClient.builder()
            .baseUrl(instances.getFirst().getUri().toString())
            .requestFactory(factory)
            .build()
            .get()
            .uri(builder -> builder.path("/media/files/cover-info").queryParam("url", pic).build())
            .header("Authorization", "Bearer " + CurrentUser.jwt().getTokenValue())
            .retrieve()
            .body(Map.class);
        if (info != null && Boolean.TRUE.equals(info.get("managed"))) {
            String id = String.valueOf(info.get("id"));
            try (MediaObjectLock lock = new MediaObjectLock(coverLockSource, id)) {
                coverMedia.requireReadyMedia(id);
                return coverTransactions.execute(transaction -> save.get());
            }
        }
        return coverTransactions.execute(transaction -> save.get());
    }

    @Autowired
    private CourseBaseMapper courseBaseMapper;

    @Autowired
    private CourseMarketMapper courseMarketMapper;

    @Autowired
    private CourseCategoryMapper categoryMapper;

    @Autowired
    private CoursePublishMapper coursePublishMapper;

    @Autowired
    private CoursePublishPreMapper coursePublishPreMapper;

    @Autowired
    private CourseTeacherMapper courseTeacherMapper;

    @Autowired
    private TeachplanMapper teachplanMapper;

    @Autowired
    private TeachplanMediaMapper teachplanMediaMapper;

    /**
     * 保存或更新课程营销信息
     */
    private CourseMarket saveOrUpdateCourseMarket(Long courseId, Object source) {
        // 构建营销信息
        CourseMarket courseMarket = new CourseMarket();
        BeanUtils.copyProperties(source, courseMarket);
        courseMarket.setId(courseId);

        // 查询是否存在
        CourseMarket existing = courseMarketMapper.selectById(courseId);

        if (existing == null) {
            // 不存在则插入
            int result = courseMarketMapper.insert(courseMarket);
            if (result == 0) {
                log.error("营销信息插入失败，课程ID：{}", courseId);
                GlobalException.cast("营销信息添加失败");
            }
            log.info("营销信息插入成功，课程ID：{}", courseId);
            return courseMarket;
        } else {
            // 存在则更新（排除id字段）
            BeanUtils.copyProperties(courseMarket, existing, "id");
            int result = courseMarketMapper.updateById(existing);
            if (result == 0) {
                log.error("营销信息更新失败，课程ID：{}", courseId);
                GlobalException.cast("营销信息更新失败");
            }
            log.info("营销信息更新成功，课程ID：{}", courseId);
            return existing;
        }
    }

    /**
     * 设置课程分类名称
     */
    private void setCategoryNames(CourseBaseInfoDto dto) {
        if (dto == null) {
            return;
        }
        try {
            String st = dto.getSt();
            String mt = dto.getMt();

            if (StringUtils.isNotBlank(st)) {
                CourseCategory stCategory = categoryMapper.selectById(st);
                if (stCategory != null) {
                    dto.setStName(stCategory.getName());
                }
            }

            if (StringUtils.isNotBlank(mt)) {
                CourseCategory mtCategory = categoryMapper.selectById(mt);
                if (mtCategory != null) {
                    dto.setMtName(mtCategory.getName());
                }
            }
        } catch (Exception e) {
            log.warn("设置分类名称失败，st：{}，mt：{}", dto.getSt(), dto.getMt(), e);
        }
    }

    /**
     * 构建返回结果 DTO
     */
    private CourseBaseInfoDto buildResultDto(CourseBase courseBase, CourseMarket courseMarket) {
        CourseBaseInfoDto resultDto = new CourseBaseInfoDto();
        BeanUtils.copyProperties(courseBase, resultDto);
        BeanUtils.copyProperties(courseMarket, resultDto);
        setCategoryNames(resultDto);
        return resultDto;
    }

    /**
     * 校验课程信息
     */
    private void validateCourseInfo(AddCourseDto dto) {
        if (dto == null) {
            GlobalException.cast("课程信息不能为空");
        }

        if (StringUtils.isBlank(dto.getName())) {
            GlobalException.cast("课程名称不能为空");
        }
        if (StringUtils.isBlank(dto.getMt())) {
            GlobalException.cast("大分类不能为空");
        }
        if (StringUtils.isBlank(dto.getSt())) {
            GlobalException.cast("小分类不能为空");
        }
        if (StringUtils.isBlank(dto.getGrade())) {
            GlobalException.cast("课程等级不能为空");
        }
        if (StringUtils.isBlank(dto.getTeachmode())) {
            GlobalException.cast("教学模式不能为空");
        }
        if (StringUtils.isBlank(dto.getUsers())) {
            GlobalException.cast("适用人群不能为空");
        }
        if (StringUtils.isBlank(dto.getPic())) {
            GlobalException.cast("课程图片不能为空");
        }

        if (
            !Set.of(LEVEL_BEGINNER, LEVEL_INTERMEDIATE, LEVEL_ADVANCED).contains(dto.getGrade())
        ) GlobalException.cast("课程等级编码无效");
        if (!Set.of(TEACH_RECORDED, TEACH_LIVE).contains(dto.getTeachmode())) GlobalException.cast(
            "教学模式编码无效"
        );
        validateCharge(dto);
    }

    /**
     * 校验收费类型
     */
    private void validateCharge(AddCourseDto dto) {
        String charge = dto.getCharge();
        BigDecimal price = dto.getPrice();

        if (StringUtils.isBlank(charge)) {
            GlobalException.cast("收费类型不能为空");
        }

        if (!Set.of(CHARGE_FREE, CHARGE_PAID).contains(charge)) GlobalException.cast(
            "收费类型编码无效"
        );
        if (CHARGE_PAID.equals(charge)) {
            if (price == null) {
                GlobalException.cast("收费课程价格不能为空");
            }
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                GlobalException.cast("收费课程价格必须大于0");
            }
        }

        if (CHARGE_FREE.equals(charge)) {
            if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                log.warn("免费课程价格应为0，当前价格：{}，将自动设置为0", price);
                dto.setPrice(BigDecimal.ZERO);
            }
        }
    }

    /**
     * 分页查询课程列表
     *
     * @param pageParams      分页参数（页码、每页大小）
     * @param courseParamsDto 查询条件（课程名称、审核状态、发布状态）
     * @return 分页结果对象（包含数据列表和分页信息）
     */
    @Override
    public PageResult<CourseBaseInfoDto> queryCourseBasePage(
        PageParams pageParams,
        QueryCourseParamsDto courseParamsDto
    ) {
        // 参数空值处理
        if (pageParams == null) {
            pageParams = new PageParams();
        }
        if (courseParamsDto == null) {
            courseParamsDto = new QueryCourseParamsDto();
        }

        // 构建查询条件
        LambdaQueryWrapper<CourseBase> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(CourseBase::getCompanyId, CurrentUser.companyId());
        lambdaQueryWrapper.orderByDesc(CourseBase::getCreateDate, CourseBase::getId);
        lambdaQueryWrapper.like(
            StringUtils.isNotBlank(courseParamsDto.getCourseName()),
            CourseBase::getName,
            courseParamsDto.getCourseName()
        );

        lambdaQueryWrapper.eq(
            StringUtils.isNotBlank(courseParamsDto.getAuditStatus()),
            CourseBase::getAuditStatus,
            courseParamsDto.getAuditStatus()
        );

        lambdaQueryWrapper.eq(
            StringUtils.isNotBlank(courseParamsDto.getPublishStatus()),
            CourseBase::getStatus,
            courseParamsDto.getPublishStatus()
        );

        // 分页查询
        Page<CourseBase> page = new Page<>(pageParams.getPageNo(), pageParams.getPageSize());
        Page<CourseBase> selectPage = courseBaseMapper.selectPage(page, lambdaQueryWrapper);

        // 返回结果
        List<CourseBase> records = selectPage.getRecords();
        // 按当前页课程 ID 批量补充收费信息，避免逐条查询；缺失记录保持为空。
        Map<Long, CourseMarket> markets = records.isEmpty()
            ? Map.of()
            : courseMarketMapper
                  .selectList(
                      new LambdaQueryWrapper<CourseMarket>().in(
                          CourseMarket::getId,
                          records.stream().map(CourseBase::getId).toList()
                      )
                  )
                  .stream()
                  .collect(
                      // 课程编号作为键，完整营销记录作为值，供当前页课程快速关联。
                      Collectors.toMap(CourseMarket::getId, market -> market)
                  );
        List<CourseBaseInfoDto> items = records
            .stream()
            // 逐条转换基础记录，仅在存在营销数据时补充收费字段。
            .map(record -> {
                CourseBaseInfoDto item = new CourseBaseInfoDto();
                BeanUtils.copyProperties(record, item);
                CourseMarket market = markets.get(record.getId());
                if (market != null) {
                    item.setCharge(market.getCharge());
                    item.setPrice(market.getPrice());
                    item.setOriginalPrice(market.getOriginalPrice());
                }
                return item;
            })
            .toList();
        Long total = selectPage.getTotal();
        log.info(
            "查询课程列表完成，总记录数：{}，当前页：{}，每页大小：{}",
            total,
            page.getCurrent(),
            page.getSize()
        );

        return new PageResult<CourseBaseInfoDto>(items, total, page.getCurrent(), page.getSize());
    }

    /**
     * 根据ID查询课程详情
     *
     * @param id 课程ID
     * @return 完整的课程信息 DTO
     */
    @Override
    public CourseBaseInfoDto getCourseById(Long id) {
        // 参数校验
        if (id == null || id <= 0) {
            log.error("课程ID无效：{}", id);
            GlobalException.cast("课程ID无效");
        }

        // 查询课程基础信息
        CourseBase courseBase = Optional.ofNullable(courseBaseMapper.selectById(id)).orElseThrow(
            // 基础记录缺失时抛出课程不存在异常，而不是返回空详情。
            () -> {
                log.error("课程不存在，课程ID：{}", id);
                GlobalException.cast("课程不存在");
                return null;
            }
        );

        // 编辑详情只能读取当前身份所属机构的课程。
        if (!CurrentUser.companyId().equals(courseBase.getCompanyId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "课程不属于当前机构");
        }

        // 旧测试课程可能没有营销记录；提供可编辑的免费课程默认值，保存时再补齐记录。
        CourseMarket courseMarket = courseMarketMapper.selectById(id);
        if (courseMarket == null) {
            log.warn("课程缺少营销信息，使用默认值展示，课程ID：{}", id);
            courseMarket = new CourseMarket();
            courseMarket.setId(id);
            courseMarket.setCharge(CHARGE_FREE);
            courseMarket.setPrice(BigDecimal.ZERO);
            courseMarket.setOriginalPrice(BigDecimal.ZERO);
        }

        // 构建返回结果
        CourseBaseInfoDto resultDto = buildResultDto(courseBase, courseMarket);

        log.info("查询课程成功，课程ID：{}，课程名称：{}", id, resultDto.getName());
        return resultDto;
    }

    /**
     * 创建课程
     *
     * @param addCourseDto 新增课程信息 DTO
     * @return 完整的课程信息 DTO（包含基础信息 + 营销信息 + 分类名称）
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CourseBaseInfoDto createCourseBase(AddCourseDto addCourseDto) {
        return saveWithCover(addCourseDto.getPic(), () -> createWithinTransaction(addCourseDto));
    }

    /** 封面锁内原子保存基础信息与营销信息。 */
    private CourseBaseInfoDto createWithinTransaction(AddCourseDto addCourseDto) {
        // 参数校验
        validateCourseInfo(addCourseDto);

        // 保存课程基础信息
        CourseBase courseBase = new CourseBase();
        BeanUtils.copyProperties(addCourseDto, courseBase);
        courseBase.setCreateDate(LocalDateTime.now());
        courseBase.setCompanyId(CurrentUser.companyId());
        courseBase.setChangeDate(LocalDateTime.now());
        courseBase.setAuditStatus(AUDIT_DRAFT); // 默认未提交
        courseBase.setStatus(COURSE_DRAFT); // 默认未发布

        int insertResult = courseBaseMapper.insert(courseBase);
        if (insertResult == 0) {
            log.error("课程基础信息插入失败，课程名称：{}", courseBase.getName());
            GlobalException.cast("课程基础信息添加失败");
        }
        log.info("课程基础信息添加成功，课程ID：{}", courseBase.getId());

        // 保存营销信息
        CourseMarket courseMarket = saveOrUpdateCourseMarket(courseBase.getId(), addCourseDto);

        // 构建返回结果
        CourseBaseInfoDto resultDto = buildResultDto(courseBase, courseMarket);

        log.info("课程创建成功，课程ID：{}，课程名称：{}", courseBase.getId(), resultDto.getName());
        return resultDto;
    }

    /**
     * 更新课程信息
     *
     * @param companyId     当前登录用户的机构ID（用于权限校验）
     * @param editCourseDto 编辑课程信息 DTO（包含课程ID和要更新的字段）
     * @return 更新后的完整课程信息 DTO
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CourseBaseInfoDto updateCourseBaseInfo(Long companyId, EditCourseDto editCourseDto) {
        return saveWithCover(editCourseDto.getPic(), () ->
            updateWithinTransaction(companyId, editCourseDto)
        );
    }

    /** 封面锁内更新并撤销失效审核结论，原事务规则保持不变。 */
    private CourseBaseInfoDto updateWithinTransaction(Long companyId, EditCourseDto editCourseDto) {
        // 参数校验
        if (editCourseDto == null || editCourseDto.getId() == null) {
            log.error("更新课程信息失败：参数为空或ID为空");
            GlobalException.cast("课程ID不能为空");
        }

        Long id = editCourseDto.getId();

        // 锁定课程，避免编辑与提审、发布交错提交。
        CourseBase existingCourse = courseBaseMapper.selectOne(
            new LambdaQueryWrapper<CourseBase>().eq(CourseBase::getId, id).last("FOR UPDATE")
        );
        if (existingCourse == null) {
            log.error("课程不存在，课程ID：{}", id);
            GlobalException.cast("课程不存在，ID：" + id);
        }

        // 验证机构权限
        if (companyId == null || !companyId.equals(existingCourse.getCompanyId())) {
            log.error(
                "无权限修改课程，公司ID：{}，课程所属公司ID：{}",
                companyId,
                existingCourse.getCompanyId()
            );
            GlobalException.cast("不能修改非本机构课程");
        }
        if (COURSE_PUBLISHED.equals(existingCourse.getStatus())) {
            GlobalException.cast("已发布课程请先下架再编辑");
        }
        if (
            !COURSE_DRAFT.equals(existingCourse.getStatus()) &&
            !COURSE_OFFLINE.equals(existingCourse.getStatus())
        ) {
            GlobalException.cast("课程发布状态异常，暂不能编辑");
        }
        if (AUDIT_PENDING.equals(existingCourse.getAuditStatus())) {
            GlobalException.cast("审核中的课程不能编辑");
        }

        // 校验更新数据
        validateCourseInfo(editCourseDto);

        // 更新课程基础信息
        CourseBase courseBase = new CourseBase();
        BeanUtils.copyProperties(editCourseDto, courseBase);
        courseBase.setId(id);
        courseBase.setChangeDate(LocalDateTime.now());
        // 内容变化会使旧审核结论失效；发布状态始终由发布/下架流程维护。
        boolean resetApproval = AUDIT_APPROVED.equals(existingCourse.getAuditStatus());
        courseBase.setAuditStatus(resetApproval ? AUDIT_DRAFT : existingCourse.getAuditStatus());
        courseBase.setStatus(existingCourse.getStatus());

        int updateResult = courseBaseMapper.updateById(courseBase);
        if (updateResult == 0) {
            log.error("课程基础信息更新失败，课程ID：{}", id);
            GlobalException.cast("课程基础信息更新失败");
        }
        log.info("课程基础信息更新成功，课程ID：{}", id);

        // 保存或更新营销信息
        CourseMarket courseMarket = saveOrUpdateCourseMarket(id, editCourseDto);
        if (resetApproval) {
            coursePublishPreMapper.deleteById(id);
        }

        // 构建返回结果
        CourseBaseInfoDto resultDto = buildResultDto(courseBase, courseMarket);

        log.info("课程更新成功，课程ID：{}，课程名称：{}", id, resultDto.getName());
        return resultDto;
    }

    /**
     * 锁定课程并校验机构及发布状态，再按关联关系从子表到主表删除。
     * 仅解除媒资绑定，不删除可能被其他课程复用的媒资文件。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCourse(Long companyId, Long courseId) {
        if (companyId == null || courseId == null || courseId <= 0) {
            GlobalException.cast("课程或机构编号无效");
        }

        // 行锁防止同一课程被两个删除请求同时处理。
        CourseBase course = courseBaseMapper.selectOne(
            new LambdaQueryWrapper<CourseBase>().eq(CourseBase::getId, courseId).last("FOR UPDATE")
        );
        if (course == null) {
            GlobalException.cast("课程不存在");
        }
        if (!companyId.equals(course.getCompanyId())) {
            GlobalException.cast("不能删除非本机构课程");
        }
        if (COURSE_PUBLISHED.equals(course.getStatus())) {
            GlobalException.cast("已发布课程请先下架再删除");
        }
        if (
            !COURSE_DRAFT.equals(course.getStatus()) && !COURSE_OFFLINE.equals(course.getStatus())
        ) {
            GlobalException.cast("课程发布状态异常，暂不能删除");
        }

        // 同时检查公开快照，避免基础状态异常时误删仍对学员可见的课程。
        CoursePublish published = coursePublishMapper.selectById(courseId);
        if (published != null && COURSE_PUBLISHED.equals(published.getStatus())) {
            GlobalException.cast("已发布课程请先下架再删除");
        }

        // 删除课程范围内的关系记录；媒资文件本身由媒资服务管理，不能在此删除。
        teachplanMediaMapper.delete(
            new LambdaQueryWrapper<TeachplanMedia>().eq(TeachplanMedia::getCourseId, courseId)
        );
        courseBaseMapper.deleteTeachplanWork(courseId);
        teachplanMapper.delete(
            new LambdaQueryWrapper<Teachplan>().eq(Teachplan::getCourseId, courseId)
        );
        courseTeacherMapper.delete(
            new LambdaQueryWrapper<CourseTeacher>().eq(CourseTeacher::getCourseId, courseId)
        );
        courseBaseMapper.deleteCourseAudit(courseId);
        coursePublishPreMapper.deleteById(courseId);
        coursePublishMapper.deleteById(courseId);
        courseMarketMapper.deleteById(courseId);

        if (courseBaseMapper.deleteById(courseId) != 1) {
            GlobalException.cast("课程删除失败");
        }
        log.info("课程及关联记录删除完成，课程ID：{}，机构ID：{}", courseId, companyId);
    }

    /** 师资读写复用课程行锁，避免和提审、发布交错保存。 */
    private CourseBase requireTeacherCourse(Long courseId, boolean editing) {
        if (courseId == null || courseId <= 0) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "课程编号无效"
        );
        LambdaQueryWrapper<CourseBase> query = new LambdaQueryWrapper<CourseBase>().eq(
            CourseBase::getId,
            courseId
        );
        if (editing) query.last("FOR UPDATE");
        CourseBase course = courseBaseMapper.selectOne(query);
        if (
            course == null || !CurrentUser.companyId().equals(course.getCompanyId())
        ) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "课程不存在或不属于当前机构");
        if (
            editing &&
            ((!COURSE_DRAFT.equals(course.getStatus()) &&
                !COURSE_OFFLINE.equals(course.getStatus())) ||
                AUDIT_PENDING.equals(course.getAuditStatus()))
        ) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "已发布或审核中的课程不能修改师资，请先下架或等待审核完成"
            );
        }
        return course;
    }

    @Override
    public List<CourseTeacher> listCourseTeachers(Long courseId) {
        requireTeacherCourse(courseId, false);
        return courseTeacherMapper.selectList(
            new LambdaQueryWrapper<CourseTeacher>()
                .eq(CourseTeacher::getCourseId, courseId)
                .orderByAsc(CourseTeacher::getId)
        );
    }

    @Override
    // 与现有createCourseBase/updateCourseBaseInfo一致：saveWithCover内部TransactionTemplate保存全部写入，提交后才释放照片锁。
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CourseTeacher saveCourseTeacher(Long courseId, Long teacherId, CourseTeacherDto input) {
        String photograph = input.getPhotograph() == null ? "" : input.getPhotograph().trim();
        // 图片只允许站内媒资或HTTP(S)地址；站内图片沿用封面的核对与删除互斥锁。
        if (
            !photograph.isEmpty() &&
            !photograph.startsWith("/api/media/files/") &&
            !photograph.matches("https?://[^\s]+")
        ) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "照片地址须为HTTP(S)地址或站内媒资地址"
        );
        return saveWithCover(photograph, () -> {
            CourseBase course = requireTeacherCourse(courseId, true);
            CourseTeacher teacher =
                teacherId == null ? new CourseTeacher() : courseTeacherMapper.selectById(teacherId);
            if (
                teacher == null || (teacherId != null && !courseId.equals(teacher.getCourseId()))
            ) throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "讲师记录不存在或不属于本课程"
            );
            String name = input.getTeacherName().trim();
            LambdaQueryWrapper<CourseTeacher> duplicate = new LambdaQueryWrapper<CourseTeacher>()
                .eq(CourseTeacher::getCourseId, courseId)
                .eq(CourseTeacher::getTeacherName, name);
            if (teacherId != null) duplicate.ne(CourseTeacher::getId, teacherId);
            if (courseTeacherMapper.selectCount(duplicate) > 0) throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "本课程已有同名讲师"
            );
            teacher.setCourseId(courseId);
            teacher.setTeacherName(name);
            teacher.setPosition(input.getPosition() == null ? "" : input.getPosition().trim());
            teacher.setIntroduction(input.getIntroduction().trim());
            teacher.setPhotograph(photograph);
            if (teacherId == null) {
                teacher.setCreateDate(LocalDateTime.now());
                if (courseTeacherMapper.insert(teacher) != 1) throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "讲师保存失败"
                );
            } else if (
                courseTeacherMapper.updateById(teacher) != 1
            ) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "讲师保存失败");
            invalidateTeacherApproval(course);
            return teacher;
        });
    }

    /** 师资也是审核内容，修改后重新提交；正式快照仅由发布流程替换。 */
    private void invalidateTeacherApproval(CourseBase course) {
        if (AUDIT_APPROVED.equals(course.getAuditStatus())) {
            course.setAuditStatus(AUDIT_DRAFT);
            coursePublishPreMapper.deleteById(course.getId());
        }
        course.setChangeDate(LocalDateTime.now());
        if (courseBaseMapper.updateById(course) != 1) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "课程状态更新失败"
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCourseTeacher(Long courseId, Long teacherId) {
        CourseBase course = requireTeacherCourse(courseId, true);
        CourseTeacher teacher = courseTeacherMapper.selectById(teacherId);
        if (
            teacher == null || !courseId.equals(teacher.getCourseId())
        ) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "讲师记录不存在或不属于本课程");
        if (courseTeacherMapper.deleteById(teacherId) != 1) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "讲师删除失败"
        );
        invalidateTeacherApproval(course);
    }

    /** 全局引用检查只返回数量；查询失败向上抛出，不能当成没有引用。 */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> mediaReferences(String id, String url) {
        long bindings = courseBaseMapper.countMediaBindings(id);
        long covers = courseBaseMapper.countMediaCovers("%" + id + "%", url);
        long published = courseBaseMapper.countPublishedMediaReferences(id, "%" + id + "%", url);
        long audit = courseBaseMapper.countAuditMediaReferences(id, "%" + id + "%", url);
        return Map.of(
            "bindings",
            bindings,
            "covers",
            covers,
            "published",
            published,
            "audit",
            audit
        );
    }
}
