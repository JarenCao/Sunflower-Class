package com.sunflower_class.service.content.service.impl;

import static com.sunflower_class.base.model.BusinessCodes.AUDIT_APPROVED;
import static com.sunflower_class.base.model.BusinessCodes.AUDIT_DRAFT;
import static com.sunflower_class.base.model.BusinessCodes.AUDIT_PENDING;
import static com.sunflower_class.base.model.BusinessCodes.COURSE_DRAFT;
import static com.sunflower_class.base.model.BusinessCodes.COURSE_OFFLINE;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunflower_class.base.exception.GlobalException;
import com.sunflower_class.base.model.BusinessCodes;
import com.sunflower_class.base.security.CurrentUser;
import com.sunflower_class.model.dto.AddTeachPlanDto;
import com.sunflower_class.model.dto.TeachPlanDto;
import com.sunflower_class.model.po.CourseBase;
import com.sunflower_class.model.po.Teachplan;
import com.sunflower_class.model.po.TeachplanMedia;
import com.sunflower_class.service.content.mapper.CourseBaseMapper;
import com.sunflower_class.service.content.mapper.CoursePublishPreMapper;
import com.sunflower_class.service.content.mapper.TeachplanMapper;
import com.sunflower_class.service.content.mapper.TeachplanMediaMapper;
import com.sunflower_class.service.content.service.TeachPlanService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 维护课程章与小节，校验课程归属并处理删除、同级排序及关联清理。
 */
@Slf4j
@Service
public class TeachPlanServiceImpl implements TeachPlanService {

    @Autowired
    TeachplanMapper teachplanMapper;

    @Autowired
    private TeachplanMediaMapper teachplanMediaMapper;

    @Autowired
    private CourseBaseMapper courseBaseMapper;

    @Autowired
    private CoursePublishPreMapper coursePublishPreMapper;

    /** 锁定课程行，串行化同一课程的目录修改，并校验机构与发布状态。 */
    private CourseBase requireEditableCourse(Long courseId) {
        if (courseId == null || courseId <= 0) GlobalException.cast("课程编号无效");
        CourseBase course = courseBaseMapper.selectOne(
            new LambdaQueryWrapper<CourseBase>().eq(CourseBase::getId, courseId).last("FOR UPDATE")
        );
        if (course == null) GlobalException.cast("课程不存在");
        if (!CurrentUser.companyId().equals(course.getCompanyId())) GlobalException.cast(
            "不能修改非本机构课程"
        );
        if (
            !COURSE_DRAFT.equals(course.getStatus()) && !COURSE_OFFLINE.equals(course.getStatus())
        ) {
            GlobalException.cast("已发布或状态异常的课程不能修改教学计划");
        }
        if (AUDIT_PENDING.equals(course.getAuditStatus())) {
            GlobalException.cast("审核中的课程不能修改教学计划");
        }
        return course;
    }

    /** 教学计划发生实际变更后，撤销旧审核结论和旧预发布快照。 */
    private void invalidateApproval(CourseBase course) {
        if (!AUDIT_APPROVED.equals(course.getAuditStatus())) return;
        course.setAuditStatus(AUDIT_DRAFT);
        course.setChangeDate(LocalDateTime.now());
        if (courseBaseMapper.updateById(course) != 1) GlobalException.cast("课程审核状态更新失败");
        coursePublishPreMapper.deleteById(course.getId());
    }

    /** 按课程及父节点读取同级节点，保证排序号相同时仍按编号稳定排列。 */
    private List<Teachplan> siblings(Long courseId, Long parentId) {
        return teachplanMapper.selectList(
            new LambdaQueryWrapper<Teachplan>()
                .eq(Teachplan::getCourseId, courseId)
                .eq(Teachplan::getParentid, parentId)
                .orderByAsc(Teachplan::getOrderby, Teachplan::getId)
        );
    }

    /** 删除或移动后统一生成连续排序号，避免历史序号留下空洞。 */
    private void renumber(List<Teachplan> nodes) {
        for (int index = 0; index < nodes.size(); index++) {
            Teachplan node = nodes.get(index);
            if (!Objects.equals(node.getOrderby(), index + 1)) {
                node.setOrderby(index + 1);
                node.setChangeDate(LocalDateTime.now());
                teachplanMapper.updateById(node);
            }
        }
    }

    // 仅查询同一课程、同一父节点下的最大排序号，新节点追加到同级末尾。
    private Integer getTeachPlanMax(Long courseId, Long parentId) {
        LambdaQueryWrapper<Teachplan> wrapper = new LambdaQueryWrapper<>();
        wrapper
            .eq(Teachplan::getCourseId, courseId)
            .eq(Teachplan::getParentid, parentId)
            .orderByDesc(Teachplan::getOrderby)
            .last("LIMIT 1");
        Teachplan max = teachplanMapper.selectOne(wrapper);
        return max == null ? 0 : max.getOrderby();
    }

    /**
     * 通过映射查询返回指定课程的教学计划树，用于编排和发布快照。
     */
    @Override
    public List<TeachPlanDto> findTeachPlanTree(Long id) {
        // 编排目录只向当前配置的机构开放，避免根据课程编号读取其他机构的未发布计划。
        CourseBase course = courseBaseMapper.selectById(id);
        if (course == null || !CurrentUser.companyId().equals(course.getCompanyId())) {
            GlobalException.cast("课程不存在或不属于当前机构");
        }
        return teachplanMapper.queryTreeNodes(id);
    }

    /**
     * 校验章或小节层级，新建时追加同级排序号，更新时按编号修改并记录变更时间。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveTeachPlan(AddTeachPlanDto addTeachPlanDto) {
        if (addTeachPlanDto == null) {
            throw new GlobalException("教学计划参数不能为空");
        }

        CourseBase course = requireEditableCourse(addTeachPlanDto.getCourseId());

        Teachplan teachPlan = new Teachplan();
        BeanUtils.copyProperties(addTeachPlanDto, teachPlan);
        // 请求字段 parentId 与实体 parentid 命名不同，需要显式赋值。
        teachPlan.setParentid(addTeachPlanDto.getParentId());
        if (
            addTeachPlanDto.getGrade() == null ||
            addTeachPlanDto.getGrade() < 1 ||
            addTeachPlanDto.getGrade() > 2
        ) {
            GlobalException.cast("请选择章节或小节层级");
        }
        String preview =
            addTeachPlanDto.getIsPreview() == null ? "0" : addTeachPlanDto.getIsPreview();
        if (
            (!"0".equals(preview) && !"1".equals(preview)) ||
            (addTeachPlanDto.getGrade() == 1 && !"0".equals(preview))
        ) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "只有小节可以设置试学，标记只能为0或1"
            );
        }
        teachPlan.setIsPreview(preview);
        // 校验层级只能是章或小节后，再转换为数据库实体使用的 short 类型。
        teachPlan.setGrade(addTeachPlanDto.getGrade().shortValue());
        teachPlan.setStatus(BusinessCodes.RECORD_ACTIVE);

        Long id = addTeachPlanDto.getId();

        // 是否携带计划编号决定新增或更新；新增分配排序号，更新记录修改时间。
        if (id == null) {
            if (teachPlan.getParentid() == null) GlobalException.cast("父节点编号不能为空");
            if (teachPlan.getGrade() == 1 && teachPlan.getParentid() != 0) {
                GlobalException.cast("章节必须位于课程根节点");
            }
            if (teachPlan.getGrade() == 2) {
                Teachplan parent = teachplanMapper.selectById(teachPlan.getParentid());
                if (
                    parent == null ||
                    !addTeachPlanDto.getCourseId().equals(parent.getCourseId()) ||
                    parent.getGrade() != 1
                ) {
                    GlobalException.cast("小节必须属于本课程的章节");
                }
            }
            Integer countMax = getTeachPlanMax(
                addTeachPlanDto.getCourseId(),
                addTeachPlanDto.getParentId()
            );
            teachPlan.setCreateDate(LocalDateTime.now());
            teachPlan.setOrderby(countMax + 1);
            teachplanMapper.insert(teachPlan);
            log.info("教学计划添加成功，ID：{}", teachPlan.getId());
        } else {
            Teachplan existing = teachplanMapper.selectById(id);
            if (existing == null) {
                throw new GlobalException("要更新的教学计划不存在，ID：" + id);
            }
            if (
                !addTeachPlanDto.getCourseId().equals(existing.getCourseId()) ||
                !Objects.equals(teachPlan.getParentid(), existing.getParentid()) ||
                !Objects.equals(teachPlan.getGrade(), existing.getGrade())
            ) {
                GlobalException.cast("不能更改教学计划所属课程或层级");
            }
            // 更新名称与试学标记，保留排序号、状态及绑定关系；仍需重新审核发布。
            existing.setPname(teachPlan.getPname());
            existing.setIsPreview(teachPlan.getIsPreview());
            existing.setChangeDate(LocalDateTime.now());
            teachplanMapper.updateById(existing);
            log.info("教学计划更新成功，ID：{}", id);
        }
        invalidateApproval(course);
    }

    /** 删除节点及其直接子节点的关系记录，媒资文件本身仍可供其他课程使用。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTeachPlan(Long teachplanId) {
        Teachplan node = teachplanMapper.selectById(teachplanId);
        if (node == null) GlobalException.cast("教学计划不存在");
        CourseBase course = requireEditableCourse(node.getCourseId());
        // 等待课程行锁期间节点可能已被另一请求删除，锁后重新读取避免重复操作。
        node = teachplanMapper.selectById(teachplanId);
        if (node == null) GlobalException.cast("教学计划不存在");
        List<Teachplan> peers = siblings(node.getCourseId(), node.getParentid());
        List<Teachplan> children = teachplanMapper.selectList(
            new LambdaQueryWrapper<Teachplan>()
                .eq(Teachplan::getParentid, teachplanId)
                .eq(Teachplan::getCourseId, node.getCourseId())
        );
        List<Long> ids = new ArrayList<>();
        ids.add(teachplanId);
        children.forEach(child -> ids.add(child.getId()));

        // 作业关联可能没有 course_id，以计划编号删除更可靠。
        teachplanMapper.deleteRelatedWork(teachplanId);
        teachplanMediaMapper.delete(
            new LambdaQueryWrapper<TeachplanMedia>().in(TeachplanMedia::getTeachplanId, ids)
        );
        teachplanMapper.delete(new LambdaQueryWrapper<Teachplan>().in(Teachplan::getId, ids));
        peers.removeIf(peer -> peer.getId().equals(teachplanId));
        renumber(peers);
        invalidateApproval(course);
    }

    /** 仅与相邻同级节点交换位置；边界节点保持原顺序。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveTeachPlan(Long teachplanId, String direction) {
        if (!"up".equals(direction) && !"down".equals(direction)) {
            GlobalException.cast("排序方向只能是 up 或 down");
        }
        Teachplan node = teachplanMapper.selectById(teachplanId);
        if (node == null) GlobalException.cast("教学计划不存在");
        CourseBase course = requireEditableCourse(node.getCourseId());
        node = teachplanMapper.selectById(teachplanId);
        if (node == null) GlobalException.cast("教学计划不存在");
        List<Teachplan> peers = siblings(node.getCourseId(), node.getParentid());
        int position = -1;
        for (int index = 0; index < peers.size(); index++) {
            if (peers.get(index).getId().equals(teachplanId)) position = index;
        }
        if (position < 0) GlobalException.cast("教学计划不属于当前课程");
        int target = position + ("up".equals(direction) ? -1 : 1);
        if (target < 0 || target >= peers.size()) return;
        Collections.swap(peers, position, target);
        renumber(peers);
        invalidateApproval(course);
    }
}
