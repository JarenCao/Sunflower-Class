/* 学员端数据适配层：读取搜索与学习服务的发布副本，转换为课程展示模型。 */
import axios from 'axios'
import { errorMessage } from '../../frontend-shared/error-message'
import { csrfHeaders } from './auth'
// 页面展示的小节模型；时长和试看标记允许后续接口补充。
export interface Lesson {
  id: number
  title: string
  duration: string
  free?: boolean
}
export interface CourseTeacher {
  id: number
  teacherName: string
  position: string
  introduction: string
  photograph?: string
}
export interface Course {
  teachers: CourseTeacher[]
  id: number
  title: string
  subtitle: string
  category: string
  level: string
  price: number
  charge: string
  originalPrice: number
  learners: string
  lessons: number
  hours: string
  theme: string
  instructor: string
  tags: string[]
  pic?: string
  chapters: { title: string; lessons: Lesson[] }[]
}
interface Plan {
  isPreview?: string
  id: number
  pname: string
  teachPlanTreeNodes?: Plan[]
}
// 公开课程接口的响应结构，字段名与服务端发布快照保持一致。
interface PublishedCourse {
  teachers?: string
  id: number
  name: string
  description?: string
  mtName?: string
  stName?: string
  grade?: string
  price?: number
  originalPrice?: number
  charge?: string
  tags?: string
  pic?: string
  teachplan?: string
}
const client = axios.create({ baseURL: '/api', timeout: 15000 })
client.interceptors.response.use(
  // 保留成功响应，由具体请求读取 data 并转换课程模型。
  (r) => r,
  // 将接口失败转成统一异常提示，交给页面决定如何展示。
  (e) => Promise.reject(new Error(errorMessage(e))),
)
/** 将发布快照中的目录 JSON 和五位业务编码转换为展示模型。 */
function adapt(source: PublishedCourse): Course {
  let plans: Plan[] = []
  let teachers: CourseTeacher[] = []
  try {
    plans = JSON.parse(source.teachplan || '[]')
    teachers = JSON.parse(source.teachers || '[]')
    if (!Array.isArray(teachers)) throw new Error('课程师资数据异常')
  } catch {
    throw new Error('课程目录数据异常')
  }
  // 发布快照的一级节点作为章，子节点作为小节；没有子节点时返回空列表。
  const chapters = plans.map((p) => ({
    title: p.pname,
    // 子节点转换为小节，服务端未提供时长时保留空值。
    lessons: (p.teachPlanTreeNodes || []).map((c) => ({
      id: c.id,
      title: c.pname,
      duration: '',
      free: c.isPreview === '1',
    })),
  }))
  return {
    id: source.id,
    title: source.name,
    subtitle: source.description || '',
    category: source.mtName || source.stName || '课程',
    level:
      ({ '30301': '初级', '30302': '中级', '30303': '高级' } as Record<string, string>)[
        source.grade || ''
      ] || '不限',
    price: source.charge === '30201' ? 0 : Number(source.price || 0),
    charge: source.charge || '',
    originalPrice: Number(source.originalPrice || 0),
    // 接口尚未提供学习人数、总时长和讲师信息，使用明确占位文案。
    learners: '—',
    // 累加各章的小节数量，作为课程总节数。
    lessons: chapters.reduce((total, chapter) => total + chapter.lessons.length, 0),
    hours: '时长待补充',
    theme: 'blue',
    // 讲师来自正式发布快照，草稿修改不会提前影响公开展示。
    instructor: teachers.map((teacher) => teacher.teacherName).join('、') || '暂无讲师信息',
    teachers,
    tags: (source.tags || '').split(',').filter(Boolean),
    pic: source.pic,
    chapters,
  }
}
/** 首页从搜索服务读取已发布课程，保留原有展示模型。 */
export async function getCourses(): Promise<Course[]> {
  const result: Course[] = []
  let page = 1
  while (true) {
    const { data } = await client.get('/search/courses', {
      params: { pageNo: page, pageSize: 100 },
    })
    result.push(...data.items.map(adapt))
    // 达到服务端总条数或读到空页时结束，避免继续请求无数据的页码。
    if (!data.items.length || result.length >= data.count) return result
    page++
  }
}
// 详情同样通过统一转换函数处理，保持列表与详情的字段含义一致。
export async function getCourse(id: number): Promise<Course> {
  // 详情与目录分别读取对应服务的真实副本；任一失败都不能用旧数据或演示目录替代。
  const [detail, directory] = await Promise.all([
    client.get(`/search/courses/${id}`),
    client.get(`/learning/courses/${id}/directory`),
  ])
  return adapt({ ...detail.data, teachplan: directory.data.teachplan })
}

/** 全部课程由搜索服务完成关键词、分类及分页，避免全量下载后本地筛选。 */
export async function searchCourses(pageNo: number, q: string, category: string) {
  const { data } = await client.get('/search/courses', {
    params: { pageNo, pageSize: 10, q, category: category === '全部课程' ? '' : category },
  })
  return { items: (data.items as PublishedCourse[]).map(adapt), count: Number(data.count) }
}

/** 分类不依赖当前搜索页的数据，确保分页后仍可切换所有分类。 */
export async function getCourseCategories(): Promise<string[]> {
  return (await client.get('/search/categories')).data
}

/** 选课记录和资格全部来自当前学员的真实接口，不以价格或本地缓存推断资格。 */
export interface CourseEnrollment {
  pic?: string | null
  courseId: number
  name: string
  enrollmentType?: string | null
  status?: string | null
  price?: number | null
  createdAt?: string | null
  expiresAt?: string | null
  qualification: string
  courseAvailable: boolean
  renewable: boolean
}
export async function getEnrollment(id: number): Promise<CourseEnrollment> {
  return (await client.get(`/learning/enrollments/${id}`)).data
}
export async function enrollCourse(id: number): Promise<CourseEnrollment> {
  return (await client.post(`/learning/enrollments/${id}`, null, { headers: await csrfHeaders() }))
    .data
}
export async function getMyCourses(
  pageNo: number,
): Promise<{ items: CourseEnrollment[]; count: number }> {
  return (await client.get('/learning/enrollments', { params: { pageNo, pageSize: 10 } })).data
}

/** 订单封面复用公开课程详情，课程下架或图片不可用时由页面显示占位。 */
export async function getOrderCourseCover(courseId: number): Promise<string | undefined> {
  return (await client.get(`/search/courses/${courseId}`)).data.pic || undefined
}

/** 每次取得地址都重新由后端验证资格与发布小节，不保存签名地址。 */
export async function getPlayback(
  courseId: number,
  lessonId: number,
): Promise<{ url: string; expiresAt: string }> {
  return (await client.get(`/media/playback/${courseId}/${lessonId}`)).data
}

/** 订单金额、状态和学习开通结果均读取真实服务，订单号保留字符串精度。 */
export interface CourseOrder {
  id: string
  courseId: number
  courseName: string
  price: number
  status: string
  createdAt: string
  expiresAt: string
  learningActivated: boolean
}
export async function createCourseOrder(courseId: number): Promise<CourseOrder> {
  return (
    await client.post(`/orders/purchases/${courseId}`, null, { headers: await csrfHeaders() })
  ).data
}
export async function getCourseOrders(
  pageNo: number,
  status?: string,
): Promise<{ items: CourseOrder[]; count: number }> {
  return (await client.get('/orders/purchases', { params: { pageNo, pageSize: 10, status } })).data
}
export async function getCourseOrder(id: string): Promise<CourseOrder> {
  return (await client.get(`/orders/purchases/${encodeURIComponent(id)}`)).data
}
export async function payCourseOrder(id: string): Promise<string> {
  return (
    await client.post(`/orders/purchases/${encodeURIComponent(id)}/pay`, null, {
      headers: await csrfHeaders(),
    })
  ).data.qrCode
}
export async function refreshCourseOrder(id: string): Promise<CourseOrder> {
  return (
    await client.post(`/orders/purchases/${encodeURIComponent(id)}/refresh`, null, {
      headers: await csrfHeaders(),
    })
  ).data
}

/** 免费续期规则由服务端最新发布快照决定，前端不自行增加有效期。 */
export async function renewCourse(id: number): Promise<CourseEnrollment> {
  return (
    await client.post(`/learning/enrollments/${id}/renew`, null, { headers: await csrfHeaders() })
  ).data
}

/** 试学只携带课程与小节编号，服务端独立核对正式发布标记。 */
export async function getTrialPlayback(
  courseId: number,
  lessonId: number,
): Promise<{ url: string; expiresAt: string }> {
  return (await client.get(`/media/trial/${courseId}/${lessonId}`)).data
}
