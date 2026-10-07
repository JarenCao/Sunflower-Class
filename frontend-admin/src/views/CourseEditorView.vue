<!-- 课程编辑：依次完成基础及营销信息、章节编排和媒资绑定；新课程先保存再编排。 -->
<script setup lang="ts">
import { errorMessage } from '../../../frontend-shared/error-message'
import { PAGE_SIZE } from '../api'
import { computed, nextTick, onUnmounted, watch, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listCourseTeachers,
  saveCourseTeacher,
  deleteCourseTeacher,
  addTeachplan,
  bindMedia,
  deleteTeachplan,
  getCourse,
  getTeachplan,
  listCategories,
  listMedia,
  moveTeachplan,
  saveCourse,
  uploadMedia,
} from '../api'
import type {
  Category,
  CourseInput,
  CourseTeacher,
  CourseTeacherInput,
  MediaFile,
  Teachplan,
} from '../types'

const route = useRoute()
const router = useRouter()
// 同一组件承载新建和编辑页面：new 转成 0，已保存课程使用实际编号。
const id = computed(() => (route.params.id === 'new' ? 0 : Number(route.params.id)))
const activeTab = ref('info')
const teachers = ref<CourseTeacher[]>([])
const teacherId = ref<number>()
const teacherOpen = ref(false)
const teacherSaving = ref(false)
const teacherInput = ref<CourseTeacherInput>({
  teacherName: '',
  position: '',
  introduction: '',
  photograph: '',
})
// 师资介绍不创建登录账号；编辑后读取服务端审核状态。
function editTeacher(teacher?: CourseTeacher) {
  teacherId.value = teacher?.id
  teacherInput.value = teacher
    ? { ...teacher }
    : { teacherName: '', position: '', introduction: '', photograph: '' }
  teacherOpen.value = true
}
async function refreshTeachers() {
  const courseId = id.value
  const result = await listCourseTeachers(courseId)
  if (id.value === courseId) teachers.value = result
}
async function saveTeacher() {
  if (!canEdit.value || teacherSaving.value) return
  if (!teacherInput.value.teacherName.trim() || !teacherInput.value.introduction.trim())
    return ElMessage.warning('请填写讲师姓名和介绍')
  teacherSaving.value = true
  try {
    await saveCourseTeacher(id.value, teacherInput.value, teacherId.value)
    teacherOpen.value = false
    await refreshTeachers()
    await refreshCourseStatus()
    ElMessage.success('讲师介绍已保存')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    teacherSaving.value = false
  }
}
async function removeTeacher(teacher: CourseTeacher) {
  if (!canEdit.value) return
  try {
    await ElMessageBox.confirm(`删除讲师“${teacher.teacherName}”的课程介绍？`, '删除课程讲师', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteCourseTeacher(id.value, teacher.id)
    await refreshTeachers()
    await refreshCourseStatus()
    ElMessage.success('讲师介绍已删除')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}
async function teacherPhoto(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !canEdit.value) return
  try {
    const uploaded = await uploadMedia(file)
    if (uploaded) teacherInput.value.photograph = `/api/media/files/${uploaded.id}/content`
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    input.value = ''
  }
}
const saving = ref(false)
const planActionId = ref<number | null>(null)
const courseStatus = ref('30501')
const courseAuditStatus = ref('30402')
const courseLoaded = ref(false)
// 审核中和已发布课程只允许查看；审核通过后的修改会撤销旧结论。
const canEdit = computed(
  () =>
    (!id.value || courseLoaded.value) &&
    courseStatus.value !== '30502' &&
    courseAuditStatus.value !== '30403',
)
const categories = ref<Category[]>([])
const plans = ref<Teachplan[]>([])
const plansLoading = ref(false)
const planSaving = ref(false)
const media = ref<MediaFile[]>([])
const planName = ref('')
const planParent = ref(0)
const bindPlanId = ref<number | null>(null)
const selectedMedia = ref('')
const mediaFileInput = ref<HTMLInputElement | null>(null)
const mediaUploading = ref(false)
const mediaUploadPercent = ref(0)
const mediaUploadPhase = ref('')
let mediaUploadAbort: AbortController | undefined
// 默认采用初级、录播、免费课程的字典编码，提交时保留编码而非中文名称。
const emptyForm: CourseInput = {
  name: '',
  description: '',
  users: '',
  tags: '',
  mt: '',
  st: '',
  grade: '30301',
  teachmode: '30101',
  pic: '',
  charge: '30201',
  price: 0,
  originalPrice: 0,
  validDays: 0,
}
const form = ref<CourseInput>({ ...emptyForm })
const mediaPage = ref(1)
const mediaCount = ref(0)
// 媒资翻页后清空选择，防止误绑定上一页选中的资源。
async function refreshMedia() {
  try {
    const result = await listMedia(mediaPage.value)
    media.value = result.items
    mediaCount.value = result.count
    selectedMedia.value = ''
    return true
  } catch (error) {
    ElMessage.error(errorMessage(error))
    return false
  }
}
// 在课程编辑页复用媒资中心上传流程；视频须等待转码完成后才允许绑定。
async function uploadCourseMedia(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file || !canEdit.value) return
  mediaUploading.value = true
  mediaUploadPercent.value = 0
  mediaUploadPhase.value = ''
  mediaUploadAbort = new AbortController()
  try {
    const uploaded = await uploadMedia(
      file,
      // 上传进度及阶段沿用媒资中心的回调，不能把上传完成误认为转码完成。
      (percent, phase) => {
        mediaUploadPercent.value = percent
        mediaUploadPhase.value = phase
      },
      mediaUploadAbort.signal,
    )
    mediaPage.value = 1
    if (!(await refreshMedia())) return
    // 普通文件上传接口返回媒资编号，可直接选中；视频合并接口只返回处理结果。
    if (uploaded && media.value.some((item) => item.id === uploaded.id)) {
      selectedMedia.value = uploaded.id
      ElMessage.success('文件已上传并选中，请选择小节后绑定')
    } else {
      ElMessage.success(
        mediaUploadPhase.value === '文件已存在'
          ? '文件已存在，请从媒资列表中选择'
          : '视频已上传，转码完成后可绑定',
      )
    }
  } catch (error) {
    if ((error as Error).name === 'AbortError') ElMessage.info('已停止上传')
    else ElMessage.error(errorMessage(error))
  } finally {
    mediaUploading.value = false
    mediaUploadAbort = undefined
    // 清空文件选择，以便失败后重新选择同一文件续传。
    if (mediaFileInput.value) mediaFileInput.value.value = ''
  }
}

// 离开课程编辑页时取消未完成请求，已保存的服务端分片仍可继续使用。
onUnmounted(() => mediaUploadAbort?.abort())
// 封面地址变化时重置失败标记，让新上传的图片重新尝试加载。
const coverFailed = ref(false)
watch(
  // 监听表单中的封面地址。
  () => form.value.pic,
  // 新地址需要重新尝试加载，不沿用旧图片的失败标记。
  () => {
    coverFailed.value = false
  },
)
// 未保存的新课程没有编号，不能查询或创建从属教学计划。
async function refreshPlans() {
  const courseId = id.value
  if (!courseId) return
  const coursePlans = await getTeachplan(courseId)
  // 保存操作返回时可能已切换课程，旧课程的目录不能写入当前页面。
  if (id.value === courseId) plans.value = coursePlans
}
// 目录或媒资变更后读取服务端状态，及时显示重新审核的要求。
async function refreshCourseStatus() {
  const courseId = id.value
  if (!courseId) return
  const course = await getCourse(courseId)
  if (id.value !== courseId) return
  courseStatus.value = course.status
  courseAuditStatus.value = course.auditStatus
}
// 课程编号变化时立即清空上一门课的数据，避免旧章节和父节点被带到新课程。
watch(
  id,
  // 异步请求完成前仍可能再次切换课程，失效的响应不能覆盖当前课程。
  async (courseId, _previousId, onCleanup) => {
    let expired = false
    onCleanup(() => {
      expired = true
    })
    activeTab.value = 'info'
    form.value = { ...emptyForm }
    courseLoaded.value = false
    courseStatus.value = '30501'
    courseAuditStatus.value = '30402'
    teachers.value = []
    teacherOpen.value = false
    plans.value = []
    planName.value = ''
    planParent.value = 0
    bindPlanId.value = null
    selectedMedia.value = ''
    media.value = []
    mediaCount.value = 0
    mediaPage.value = 1
    plansLoading.value = Boolean(courseId)
    try {
      const categoryResult = await listCategories()
      if (expired) return
      categories.value = categoryResult
      if (courseId) {
        // 教学计划独立读取，避免基础信息加载失败时误显示为空目录。
        const coursePlans = await getTeachplan(courseId)
        if (expired) return
        plans.value = coursePlans
        plansLoading.value = false
        const course = await getCourse(courseId)
        if (expired) return
        form.value = course
        courseStatus.value = course.status
        courseAuditStatus.value = course.auditStatus
        courseLoaded.value = true
        await refreshTeachers()
        await refreshMedia()
      }
    } catch (error) {
      if (!expired) ElMessage.error(errorMessage(error))
    } finally {
      if (!expired) plansLoading.value = false
    }
  },
  { immediate: true },
)
// 先校验名称，再保存完整表单；新建成功后切换到实际课程编号对应的编辑页。
async function save() {
  if (!canEdit.value || saving.value) return
  if (!form.value.name.trim()) return ElMessage.warning('请填写课程名称')
  saving.value = true
  const creating = !id.value
  try {
    const result = await saveCourse(form.value)
    form.value = result
    courseStatus.value = result.status
    courseAuditStatus.value = result.auditStatus
    if (creating) {
      // 先取得草稿编号才能关联教学计划；保存不会触发提交审核或发布。
      await router.replace(`/courses/${result.id}`)
      await nextTick()
      activeTab.value = 'plan'
      ElMessage.success('草稿已保存，请继续添加教学计划和媒资，完成后再提交审核')
    } else {
      ElMessage.success('基本信息已保存；完善教学计划和媒资后再提交审核')
    }
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    saving.value = false
  }
}
// 保存章或小节后重新读取目录，以服务端返回的编号和排序为准。
async function addPlan() {
  // 提交期间禁止重复点击，避免一次操作创建两个同名章节。
  if (!planName.value.trim() || !id.value || plansLoading.value || planSaving.value) return
  planSaving.value = true
  try {
    await addTeachplan(id.value, planName.value.trim(), planParent.value)
    planName.value = ''
    await refreshPlans()
    await refreshCourseStatus()
    ElMessage.success('教学计划已保存')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    planSaving.value = false
  }
}
// 确认当前页选中的媒资存在，绑定成功后刷新目录并关闭当前选择。
async function bind() {
  // 按媒资编号匹配当前页记录，缺少记录时不发起绑定。
  const item = media.value.find((m) => m.id === selectedMedia.value)
  if (!bindPlanId.value || !item) return
  try {
    await bindMedia(bindPlanId.value, item)
    await refreshPlans()
    await refreshCourseStatus()
    ElMessage.success('媒资已绑定')
    selectedMedia.value = ''
    bindPlanId.value = null
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}
// 修改名称沿用计划保存接口；保留原有父节点，避免改变目录层级。
async function rename(plan: Teachplan) {
  try {
    const result = await ElMessageBox.prompt('输入新的名称', '修改教学计划', {
      inputValue: plan.pname,
      inputPattern: /\S/,
      inputErrorMessage: '名称不能为空',
    })
    await addTeachplan(
      id.value,
      result.value.trim(),
      plan.parentid || plan.parentId || 0,
      plan.id,
      plan.isPreview || '0',
    )
    await refreshPlans()
    await refreshCourseStatus()
  } catch (error) {
    if (error instanceof Error) ElMessage.error(errorMessage(error))
  }
}

// 试学设置与目录编辑共用保存接口，必须重新审核发布后才对学员生效。
async function toggleTrial(plan: Teachplan) {
  if (!canEdit.value || planActionId.value !== null) return
  planActionId.value = plan.id
  try {
    await addTeachplan(
      id.value,
      plan.pname,
      plan.parentid || plan.parentId || 0,
      plan.id,
      plan.isPreview === '1' ? '0' : '1',
    )
    await refreshPlans()
    await refreshCourseStatus()
    ElMessage.success('试学设置已保存，审核发布后生效')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    planActionId.value = null
  }
}
// 删除前明确说明章会连同所有小节及关联一起删除；取消时保持页面原状。
async function removePlan(plan: Teachplan) {
  try {
    await ElMessageBox.confirm(
      plan.grade === 1
        ? `删除章节“${plan.pname}”及其全部小节？媒资文件会保留。`
        : `删除小节“${plan.pname}”及其媒资绑定？媒资文件会保留。`,
      '删除教学计划',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  planActionId.value = plan.id
  try {
    await deleteTeachplan(plan.id)
    await refreshPlans()
    await refreshCourseStatus()
    ElMessage.success('教学计划已删除')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    planActionId.value = null
  }
}

// 每次移动后重新读取服务端顺序，界面索引不自行推测排序结果。
async function movePlan(plan: Teachplan, direction: 'up' | 'down') {
  planActionId.value = plan.id
  try {
    await moveTeachplan(plan.id, direction)
    await refreshPlans()
    await refreshCourseStatus()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    planActionId.value = null
  }
}
// 上传只更新表单中的封面地址，点击保存后才写入课程信息。
async function cover(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  try {
    const uploaded = await uploadMedia(file)
    if (uploaded?.url) {
      coverFailed.value = false
      form.value.pic = '/api/media' + '/files/' + uploaded.id + '/content'
      ElMessage.success('封面已上传，请保存课程')
    }
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}
// 将两级目录展开为选择列表，同时保留章和小节各自的数据。
const flatPlans = computed(() => plans.value.flatMap((p) => [p, ...(p.teachPlanTreeNodes || [])]))
</script>

<template>
  <div class="page-heading compact">
    <div>
      <div class="eyebrow">COURSES · 课程编辑</div>
      <h1>{{ id ? '编辑课程' : '创建课程' }}</h1>
      <p>先填写基本信息，再编排章节与关联学习资源。</p>
    </div>
    <RouterLink
      to="/courses"
      class="soft-link"
    >
      ← 返回课程列表
    </RouterLink>
  </div>
  <div class="editor-layout">
    <div class="panel editor-panel">
      <!-- 编辑步骤：先保存课程，再开放教学计划和媒资关联。 -->
      <div class="editor-tabs">
        <button
          :class="{ selected: activeTab === 'info' }"
          @click="activeTab = 'info'"
        >
          01 基本信息
        </button>
        <button
          :class="{ selected: activeTab === 'plan' }"
          :disabled="!id"
          @click="activeTab = 'plan'"
        >
          02 教学计划
        </button>
        <button
          :class="{ selected: activeTab === 'media' }"
          :disabled="!id"
          @click="activeTab = 'media'"
        >
          03 关联媒资
        </button>
        <button
          :class="{ selected: activeTab === 'teachers' }"
          :disabled="!id"
          @click="activeTab = 'teachers'"
        >
          04 课程师资
        </button>
      </div>
      <div
        v-if="id && courseLoaded && courseAuditStatus === '30402'"
        class="notice-strip"
      >
        当前课程为未提交草稿，可以继续添加教学计划、媒资和讲师。完善后再到课程列表提交审核。
      </div>
      <div
        v-if="activeTab === 'teachers'"
        class="editor-body"
      >
        <div class="form-intro">
          <h2>课程师资</h2>
          <p>维护本课程讲师的姓名、职位和授课经历。</p>
        </div>
        <p
          v-if="!canEdit"
          class="notice-strip"
        >
          课程已发布或审核中，暂不能修改师资。
        </p>
        <p
          v-else-if="courseAuditStatus === '30404'"
          class="notice-strip"
        >
          修改师资后需重新提交审核。
        </p>
        <el-button
          type="primary"
          :disabled="!canEdit"
          @click="editTeacher()"
        >
          添加讲师
        </el-button>
        <el-table
          :data="teachers"
          empty-text="尚未添加课程讲师"
        >
          <el-table-column
            prop="teacherName"
            label="姓名"
          />
          <el-table-column
            prop="position"
            label="职位"
          />
          <el-table-column
            prop="introduction"
            label="介绍"
            show-overflow-tooltip
          />
          <el-table-column label="操作">
            <template #default="{ row }">
              <el-button
                link
                :disabled="!canEdit"
                @click="editTeacher(row)"
              >
                编辑
              </el-button>
              <el-button
                link
                type="danger"
                :disabled="!canEdit"
                @click="removeTeacher(row)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div
        v-if="activeTab === 'info'"
        class="editor-body"
      >
        <div class="form-intro">
          <h2>课程基本信息</h2>
          <p>清晰的课程介绍，能帮助学员更快找到适合自己的学习内容。</p>
        </div>
        <div
          v-if="!canEdit"
          class="notice-strip"
        >
          {{
            !courseLoaded
              ? plansLoading
                ? '正在加载课程信息…'
                : '课程信息加载失败，请刷新后重试。'
              : courseStatus === '30502'
                ? '课程已发布，请先下架再编辑。'
                : '课程审核中，暂不能编辑。'
          }}
        </div>
        <div
          v-else-if="courseAuditStatus === '30404'"
          class="notice-strip"
        >
          修改课程会撤销审核通过状态，保存后需重新提交审核。
        </div>
        <div
          v-if="!id"
          class="notice-strip"
        >
          先保存基础信息为草稿，再添加教学计划、关联媒资和讲师。全部完善后，在课程列表单独提交审核。
        </div>
        <el-form
          :model="form"
          label-position="top"
          :disabled="!canEdit"
        >
          <div class="form-grid">
            <el-form-item
              label="课程名称"
              class="span-2"
            >
              <el-input
                v-model="form.name"
                placeholder="例如：Java 全栈工程师成长路线"
              />
            </el-form-item>
            <!-- 切换一级分类时清空二级分类，避免保留不属于新父级的选择。 -->
            <el-form-item label="一级分类">
              <el-select
                v-model="form.mt"
                placeholder="请选择"
                @change="form.st = ''"
              >
                <el-option
                  v-for="cat in categories"
                  :key="cat.id"
                  :value="cat.id"
                  :label="cat.name"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="二级分类">
              <el-select
                v-model="form.st"
                placeholder="请选择"
              >
                <el-option
                  v-for="cat in categories.find((c) => c.id === form.mt)?.children || []"
                  :key="cat.id"
                  :value="cat.id"
                  :label="cat.name"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="课程等级">
              <el-select v-model="form.grade">
                <el-option
                  label="初级"
                  value="30301"
                />
                <el-option
                  label="中级"
                  value="30302"
                />
                <el-option
                  label="高级"
                  value="30303"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="教学模式">
              <el-select v-model="form.teachmode">
                <el-option
                  label="录播"
                  value="30101"
                />
                <el-option
                  label="直播"
                  value="30102"
                />
              </el-select>
            </el-form-item>
            <el-form-item
              label="适用人群"
              class="span-2"
            >
              <el-input
                v-model="form.users"
                placeholder="这门课程适合谁？"
              />
            </el-form-item>
            <el-form-item
              label="课程标签"
              class="span-2"
            >
              <el-input
                v-model="form.tags"
                placeholder="多个标签用英文逗号分隔"
              />
            </el-form-item>
            <el-form-item
              label="封面图片地址"
              class="span-2"
            >
              <el-input
                v-model="form.pic"
                placeholder="图片地址"
              />
              <div class="cover-upload">
                <input
                  type="file"
                  accept="image/*"
                  aria-label="上传课程封面"
                  :disabled="!canEdit"
                  @change="cover"
                />
                <p>选择图片上传后，请保存课程。</p>
                <img
                  v-if="form.pic && !coverFailed"
                  :key="form.pic"
                  :src="form.pic"
                  alt="课程封面"
                  @error="coverFailed = true"
                />
                <div
                  v-else
                  class="cover-placeholder"
                  role="status"
                >
                  {{
                    coverFailed
                      ? '封面加载失败，请检查图片地址或重新上传。'
                      : '尚未设置封面，请选择图片上传。'
                  }}
                </div>
              </div>
            </el-form-item>
            <el-form-item label="收费方式">
              <el-select v-model="form.charge">
                <el-option
                  label="免费课程"
                  value="30201"
                />
                <el-option
                  label="收费课程"
                  value="30202"
                />
              </el-select>
            </el-form-item>
            <!-- 0表示长期有效；免费续期按最新发布的天数重新计算。 -->
            <el-form-item label="学习有效天数（0为长期有效）">
              <el-input-number
                v-model="form.validDays"
                :min="0"
                :precision="0"
              />
            </el-form-item>
            <el-form-item label="课程价格">
              <el-input-number
                v-model="form.price"
                :min="0"
                :disabled="form.charge === '30201'"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item
              label="课程介绍"
              class="span-2"
            >
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="5"
                placeholder="写一段简洁而具体的课程介绍"
              />
            </el-form-item>
          </div>
        </el-form>
        <!-- 保存基础与营销表单；封面上传成功后也需要此步骤写入课程。 -->
        <div class="form-actions">
          <el-button @click="router.push('/courses')">取消</el-button>
          <el-button
            type="primary"
            :loading="saving"
            :disabled="!canEdit"
            @click="save"
          >
            {{ id ? '保存基本信息' : '保存草稿并继续' }}
          </el-button>
        </div>
      </div>
      <div
        v-else-if="activeTab === 'plan'"
        class="editor-body"
      >
        <div class="form-intro">
          <h2>教学计划</h2>
          <p>用章节和小节组织学习内容，让进度一目了然。</p>
        </div>
        <div
          v-if="!canEdit"
          class="notice-strip"
        >
          {{
            !courseLoaded
              ? plansLoading
                ? '正在加载课程信息…'
                : '课程信息加载失败，请刷新后重试。'
              : courseStatus === '30502'
                ? '课程已发布，请先下架再修改教学计划。'
                : '课程审核中，暂不能修改教学计划。'
          }}
        </div>
        <div class="plan-add">
          <el-select
            v-model="planParent"
            style="width: 180px"
            :disabled="!canEdit || plansLoading"
          >
            <el-option
              label="新增章节"
              :value="0"
            />
            <el-option
              v-for="plan in plans"
              :key="plan.id"
              :label="`加入 · ${plan.pname}`"
              :value="plan.id"
            />
          </el-select>
          <el-input
            v-model="planName"
            placeholder="输入章节或小节名称"
            :disabled="!canEdit || plansLoading"
            @keyup.enter="addPlan"
          />
          <el-button
            type="primary"
            :disabled="!canEdit || plansLoading || planSaving"
            :loading="planSaving"
            @click="addPlan"
          >
            添加
          </el-button>
        </div>
        <div
          v-loading="plansLoading"
          class="plan-list"
        >
          <div
            v-for="(plan, index) in plans"
            :key="plan.id"
            class="plan-item"
          >
            <div>
              <span class="plan-index">{{ String(index + 1).padStart(2, '0') }}</span>
              <strong>{{ plan.pname }}</strong>
              <el-button
                link
                :disabled="!canEdit || planActionId !== null"
                @click="rename(plan)"
              >
                改名
              </el-button>
              <el-button
                link
                :disabled="index === 0 || !canEdit || planActionId !== null"
                @click="movePlan(plan, 'up')"
              >
                上移
              </el-button>
              <el-button
                link
                :disabled="index === plans.length - 1 || !canEdit || planActionId !== null"
                @click="movePlan(plan, 'down')"
              >
                下移
              </el-button>
              <el-button
                link
                type="danger"
                :disabled="!canEdit || planActionId !== null"
                @click="removePlan(plan)"
              >
                删除
              </el-button>
            </div>
            <div
              v-for="(child, childIndex) in plan.teachPlanTreeNodes || []"
              :key="child.id"
              class="plan-child"
            >
              <span>↳</span>
              {{ child.pname }}
              <small v-if="child.isPreview === '1'">试学</small>
              <el-button
                link
                :disabled="!canEdit || planActionId !== null"
                @click="toggleTrial(child)"
              >
                {{ child.isPreview === '1' ? '取消试学' : '设为试学' }}
              </el-button>
              <small v-if="child.teachplanMedia">· {{ child.teachplanMedia.mediaFilename }}</small>
              <el-button
                link
                :disabled="!canEdit || planActionId !== null"
                @click="rename(child)"
              >
                改名
              </el-button>
              <el-button
                link
                :disabled="childIndex === 0 || !canEdit || planActionId !== null"
                @click="movePlan(child, 'up')"
              >
                上移
              </el-button>
              <el-button
                link
                :disabled="
                  childIndex === (plan.teachPlanTreeNodes || []).length - 1 ||
                  !canEdit ||
                  planActionId !== null
                "
                @click="movePlan(child, 'down')"
              >
                下移
              </el-button>
              <el-button
                link
                type="danger"
                :disabled="!canEdit || planActionId !== null"
                @click="removePlan(child)"
              >
                删除
              </el-button>
            </div>
          </div>
          <div
            v-if="!plans.length && !plansLoading"
            class="empty-card"
          >
            暂无教学计划，添加一个章节开始编排。
          </div>
        </div>
      </div>
      <div
        v-else-if="activeTab === 'media'"
        class="editor-body"
      >
        <div class="form-intro">
          <h2>关联媒资</h2>
          <p>上传新媒资，或从已有文件中选择，再绑定到教学计划小节。</p>
        </div>
        <!-- 就地上传复用媒资中心接口；已发布或审核中课程不允许在这里新增绑定。 -->
        <div class="media-upload-inline">
          <div>
            <strong>上传新媒资</strong>
            <p>图片上传后可直接选择；视频需等待转码完成后才能绑定。</p>
          </div>
          <div class="media-upload-actions">
            <el-button
              :disabled="mediaUploading"
              @click="refreshMedia"
            >
              刷新列表
            </el-button>
            <el-button
              :disabled="!canEdit || mediaUploading"
              @click="mediaFileInput?.click()"
            >
              {{ mediaUploading ? '上传中…' : '选择文件上传' }}
            </el-button>
          </div>
          <input
            ref="mediaFileInput"
            type="file"
            hidden
            :disabled="!canEdit || mediaUploading"
            aria-label="上传关联媒资"
            @change="uploadCourseMedia"
          />
        </div>
        <div
          v-if="mediaUploading"
          class="media-upload-progress"
        >
          <span>{{ mediaUploadPhase || '准备上传' }}</span>
          <el-progress :percentage="mediaUploadPercent" />
          <el-button @click="mediaUploadAbort?.abort()">停止上传</el-button>
        </div>
        <div
          v-if="!canEdit"
          class="notice-strip"
        >
          {{
            courseStatus === '30502'
              ? '课程已发布，请先下架再关联媒资。'
              : '课程审核中，暂不能关联媒资。'
          }}
        </div>
        <div class="plan-add">
          <el-select
            v-model="bindPlanId"
            placeholder="选择小节"
            style="width: 220px"
            :disabled="!canEdit || mediaUploading"
          >
            <el-option
              v-for="plan in flatPlans.filter((p) => (p.grade || 2) > 1)"
              :key="plan.id"
              :label="plan.pname"
              :value="plan.id"
            />
          </el-select>
          <el-select
            v-model="selectedMedia"
            placeholder="选择媒资文件"
            :disabled="!canEdit || mediaUploading"
          >
            <el-option
              v-for="item in media"
              :key="item.id"
              :label="item.status === '20302' ? item.filename : `${item.filename}（暂不可绑定）`"
              :value="item.id"
              :disabled="item.status !== '20302'"
            />
          </el-select>
          <el-button
            type="primary"
            :disabled="!canEdit || mediaUploading || !bindPlanId || !selectedMedia"
            @click="bind"
          >
            绑定
          </el-button>
        </div>
        <el-pagination
          v-model:current-page="mediaPage"
          :page-size="PAGE_SIZE"
          :total="mediaCount"
          layout="total, prev, pager, next"
          @current-change="refreshMedia"
        />
        <div class="notice-strip">绑定前会核对媒资归属及处理状态；视频转码完成后才能绑定。</div>
      </div>
    </div>
    <!-- 侧栏提示当前编辑阶段及课程完善要点。 -->
    <aside class="editor-aside">
      <div class="aside-tip">
        <span>✦ 编辑指南</span>
        <h3>
          一门完整课程
          <br />
          从这里开始。
        </h3>
        <p>
          保存基础信息只创建草稿。继续添加教学计划、绑定视频和讲师，全部完善后再到课程列表提交审核。
        </p>
        <div class="aside-steps">
          <span>① 完善课程信息</span>
          <span>② 编排章节与小节</span>
          <span>③ 上传并关联媒资</span>
          <span>④ 提交课程审核</span>
        </div>
      </div>
    </aside>
  </div>
  <el-dialog
    v-model="teacherOpen"
    :title="teacherId ? '编辑课程讲师' : '添加课程讲师'"
    width="560px"
  >
    <el-form
      label-position="top"
      :disabled="!canEdit || teacherSaving"
    >
      <el-form-item label="讲师姓名">
        <el-input
          v-model="teacherInput.teacherName"
          maxlength="60"
        />
      </el-form-item>
      <el-form-item label="讲师职位">
        <el-input
          v-model="teacherInput.position"
          maxlength="255"
        />
      </el-form-item>
      <el-form-item label="讲师介绍">
        <el-input
          v-model="teacherInput.introduction"
          type="textarea"
          :rows="4"
          maxlength="1024"
        />
      </el-form-item>
      <el-form-item label="讲师照片地址">
        <el-input
          v-model="teacherInput.photograph"
          maxlength="1024"
        />
      </el-form-item>
      <input
        type="file"
        accept="image/*"
        aria-label="上传讲师照片"
        :disabled="!canEdit || teacherSaving"
        @change="teacherPhoto"
      />
    </el-form>
    <template #footer>
      <el-button @click="teacherOpen = false">取消</el-button>
      <el-button
        type="primary"
        :loading="teacherSaving"
        :disabled="!canEdit"
        @click="saveTeacher"
      >
        保存讲师
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.media-upload-inline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 18px 20px;
  border: 1px solid #e1e8ef;
  border-radius: 10px;
  background: #f7fafc;
}
.media-upload-inline strong {
  color: #304260;
  font-size: 14px;
}
.media-upload-inline p {
  margin: 6px 0 0;
  color: #8392a3;
  font-size: 12px;
}
.media-upload-actions {
  display: flex;
  flex: none;
}
.media-upload-progress {
  display: grid;
  grid-template-columns: 1fr 2fr auto;
  align-items: center;
  gap: 14px;
  margin-top: 14px;
  color: #738396;
  font-size: 12px;
}
.cover-upload {
  width: 100%;
  margin-top: 12px;
}
.cover-upload p {
  margin: 8px 0 12px;
  color: #8590a0;
  font-size: 12px;
}
.cover-upload img {
  display: block;
  width: 200px;
  height: 112px;
  object-fit: contain;
  border: 1px solid #e8edf3;
  border-radius: 8px;
  background: #f7f9fc;
}
.cover-placeholder {
  max-width: 360px;
  padding: 22px 16px;
  border: 1px dashed #d6deea;
  border-radius: 8px;
  background: #f7f9fc;
  color: #78869a;
  font-size: 12px;
  line-height: 1.8;
}
</style>
