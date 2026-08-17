package com.sunflower_class.service.content.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunflower_class.base.exception.GlobalException;
import com.sunflower_class.base.model.PageParams;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.model.dto.AddCourseDto;
import com.sunflower_class.model.dto.CourseBaseInfoDto;
import com.sunflower_class.model.dto.EditCourseDto;
import com.sunflower_class.model.dto.QueryCourseParamsDto;
import com.sunflower_class.model.po.CourseBase;
import com.sunflower_class.model.po.CourseCategory;
import com.sunflower_class.model.po.CourseMarket;
import com.sunflower_class.service.content.mapper.CourseBaseMapper;
import com.sunflower_class.service.content.mapper.CourseCategoryMapper;
import com.sunflower_class.service.content.mapper.CourseMarketMapper;
import com.sunflower_class.service.content.service.CourseBaseInfoService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
public class CourseBaseInfoServiceImpl implements CourseBaseInfoService {

    @Autowired
    private CourseBaseMapper courseBaseMapper;

    @Autowired
    private CourseMarketMapper courseMarketMapper;

    @Autowired
    private CourseCategoryMapper categoryMapper;

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
     * 校验课程信息（新增和编辑共用）
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

        validateCharge(dto);
    }

    /**
     * 校验收费类型
     */
    private void validateCharge(AddCourseDto dto) {
        String charge = dto.getCharge();
        java.math.BigDecimal price = dto.getPrice();

        if (StringUtils.isBlank(charge)) {
            GlobalException.cast("收费类型不能为空");
        }

        if ("30202".equals(charge)) {
            if (price == null) {
                GlobalException.cast("收费课程价格不能为空");
            }
            if (price.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                GlobalException.cast("收费课程价格必须大于0");
            }
        }

        if ("30201".equals(charge)) {
            if (price != null && price.compareTo(java.math.BigDecimal.ZERO) > 0) {
                log.warn("免费课程价格应为0，当前价格：{}，将自动设置为0", price);
                dto.setPrice(java.math.BigDecimal.ZERO);
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
    public PageResult<CourseBase> queryCourseBasePage(PageParams pageParams, QueryCourseParamsDto courseParamsDto) {
        // 参数空值处理
        if (pageParams == null) {
            pageParams = new PageParams();
        }
        if (courseParamsDto == null) {
            courseParamsDto = new QueryCourseParamsDto();
        }

        // 构建查询条件
        LambdaQueryWrapper<CourseBase> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.like(StringUtils.isNotBlank(courseParamsDto.getCourseName()),
                CourseBase::getName, courseParamsDto.getCourseName());

        lambdaQueryWrapper.eq(StringUtils.isNotBlank(courseParamsDto.getAuditStatus()),
                CourseBase::getAuditStatus, courseParamsDto.getAuditStatus());
                
        lambdaQueryWrapper.eq(StringUtils.isNotBlank(courseParamsDto.getPublishStatus()),
                CourseBase::getStatus, courseParamsDto.getPublishStatus());

        // 分页查询
        Page<CourseBase> page = new Page<>(pageParams.getPageNo(), pageParams.getPageSize());
        Page<CourseBase> selectPage = courseBaseMapper.selectPage(page, lambdaQueryWrapper);

        // 返回结果
        List<CourseBase> items = selectPage.getRecords();
        Long total = selectPage.getTotal();
        log.info("查询课程列表完成，总记录数：{}，当前页：{}，每页大小：{}",
                total, page.getCurrent(), page.getSize());

        return new PageResult<CourseBase>(items, total, page.getCurrent(), page.getSize());
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
        CourseBase courseBase = Optional.ofNullable(courseBaseMapper.selectById(id))
                .orElseThrow(() -> {
                    log.error("课程不存在，课程ID：{}", id);
                    GlobalException.cast("课程不存在");
                    return null;
                });

        // 查询课程营销信息
        CourseMarket courseMarket = Optional.ofNullable(courseMarketMapper.selectById(id))
                .orElseThrow(() -> {
                    log.error("课程营销信息不存在，课程ID：{}", id);
                    GlobalException.cast("课程营销信息不存在");
                    return null;
                });

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
    @Transactional(rollbackFor = Exception.class)
    public CourseBaseInfoDto createCourseBase(AddCourseDto addCourseDto) {
        // 参数校验
        validateCourseInfo(addCourseDto);

        // 保存课程基础信息
        CourseBase courseBase = new CourseBase();
        BeanUtils.copyProperties(addCourseDto, courseBase);
        courseBase.setCreateDate(LocalDateTime.now());
        courseBase.setChangeDate(LocalDateTime.now());
        courseBase.setAuditStatus("30402"); // 默认未提交
        courseBase.setStatus("30501"); // 默认未发布

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
    @Transactional(rollbackFor = Exception.class)
    public CourseBaseInfoDto updateCourseBaseInfo(Long companyId, EditCourseDto editCourseDto) {
        // 参数校验
        if (editCourseDto == null || editCourseDto.getId() == null) {
            log.error("更新课程信息失败：参数为空或ID为空");
            GlobalException.cast("课程ID不能为空");
        }

        Long id = editCourseDto.getId();

        // 验证课程是否存在
        CourseBase existingCourse = courseBaseMapper.selectById(id);
        if (existingCourse == null) {
            log.error("课程不存在，课程ID：{}", id);
            GlobalException.cast("课程不存在，ID：" + id);
        }

        // 验证机构权限
        if (companyId == null || !companyId.equals(existingCourse.getCompanyId())) {
            log.error("无权限修改课程，公司ID：{}，课程所属公司ID：{}", companyId, existingCourse.getCompanyId());
            GlobalException.cast("不能修改非本机构课程");
        }

        // 校验更新数据
        validateCourseInfo(editCourseDto);

        // 更新课程基础信息
        CourseBase courseBase = new CourseBase();
        BeanUtils.copyProperties(editCourseDto, courseBase);
        courseBase.setId(id);
        courseBase.setChangeDate(LocalDateTime.now());
        // 保持审核状态和发布状态不变
        courseBase.setAuditStatus(existingCourse.getAuditStatus());
        courseBase.setStatus(existingCourse.getStatus());

        int updateResult = courseBaseMapper.updateById(courseBase);
        if (updateResult == 0) {
            log.error("课程基础信息更新失败，课程ID：{}", id);
            GlobalException.cast("课程基础信息更新失败");
        }
        log.info("课程基础信息更新成功，课程ID：{}", id);

        // 保存或更新营销信息
        CourseMarket courseMarket = saveOrUpdateCourseMarket(id, editCourseDto);

        // 构建返回结果
        CourseBaseInfoDto resultDto = buildResultDto(courseBase, courseMarket);

        log.info("课程更新成功，课程ID：{}，课程名称：{}", id, resultDto.getName());
        return resultDto;
    }

}