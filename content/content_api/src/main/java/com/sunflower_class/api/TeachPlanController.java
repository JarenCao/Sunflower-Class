package com.sunflower_class.api;

import com.sunflower_class.base.model.RestResponse;
import com.sunflower_class.model.dto.AddTeachPlanDto;
import com.sunflower_class.model.dto.BindTeachplanMediaDto;
import com.sunflower_class.model.dto.TeachPlanDto;
import com.sunflower_class.service.content.service.AssociationMediaService;
import com.sunflower_class.service.content.service.TeachPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教学计划编排接口，提供目录查询、章或小节保存与媒资绑定。
 */
@Slf4j
@CrossOrigin(origins = "*")
@RestController
@Tag(name = "课程计划", description = "课程计划相关接口")
public class TeachPlanController {

    @Autowired
    private TeachPlanService teachPlanService;

    @Autowired
    private AssociationMediaService associationMediaService;

    /**
     * 按课程编号返回章、小节和关联媒资组成的教学计划树。
     */
    @Operation(summary = "查询课程计划树", description = "根据课程ID查询课程计划树形结构")
    @GetMapping("techplan/{id}/tree-nodes")
    public List<TeachPlanDto> getTreeNodes(@PathVariable Long id) {
        return teachPlanService.findTeachPlanTree(id);
    }

    /**
     * 接收教学计划表单；有编号时更新已有计划，无编号时新建章或小节。
     */
    @Operation(summary = "创建或修改课程计划", description = "创建或修改课程计划（章节/小节/课时）")
    @PostMapping("/teachplan")
    public void addTeachPlan(@RequestBody @Valid AddTeachPlanDto addTeachPlanDto) {
        teachPlanService.saveTeachPlan(addTeachPlanDto);
    }

    /**
     * 将媒资编号和文件名绑定到指定教学计划，返回统一业务响应。
     */
    @Operation(summary = "绑定课程计划与媒资文件")
    @PostMapping("/teachplan/media/bind")
    public RestResponse bindTeachplanMedia(
        @RequestBody BindTeachplanMediaDto bindTeachplanMediaDto
    ) {
        associationMediaService.associationMedia(bindTeachplanMediaDto);
        return RestResponse.success();
    }
}
