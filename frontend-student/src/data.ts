/* 学员端数据适配层：读取搜索与学习服务的发布副本，转换为课程展示模型。 */
import axios from 'axios'
// 页面展示的小节模型；时长和试看标记允许后续接口补充。
export interface Lesson {
  title: string
  duration: string
  free?: boolean
}
export interface Course {
  id: number
  title: string
  subtitle: string
  category: string
  level: string
  price: number
  originalPrice: number
  learners: string
  lessons: number
  hours: string
  theme: string
  symbol: string
  instructor: string
  tags: string[]
  pic?: string
  chapters: { title: string; lessons: Lesson[] }[]
}
interface Plan {
  pname: string
  teachPlanTreeNodes?: Plan[]
}
// 公开课程接口的响应结构，字段名与服务端发布快照保持一致。
interface PublishedCourse {
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
  (e) =>
    Promise.reject(
      new Error(
        e.response?.data?.errMessage || e.response?.data?.msg || '课程服务暂不可用，请稍后重试',
      ),
    ),
)
/** 将发布快照中的目录 JSON 和五位业务编码转换为展示模型。 */
function adapt(source: PublishedCourse): Course {
  let plans: Plan[] = []
  try {
    plans = JSON.parse(source.teachplan || '[]')
  } catch {
    throw new Error('课程目录数据异常')
  }
  // 发布快照的一级节点作为章，子节点作为小节；没有子节点时返回空列表。
  const chapters = plans.map((p) => ({
    title: p.pname,
    // 子节点转换为小节，服务端未提供时长时保留空值。
    lessons: (p.teachPlanTreeNodes || []).map((c) => ({ title: c.pname, duration: '' })),
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
    originalPrice: Number(source.originalPrice || 0),
    // 接口尚未提供学习人数、总时长和讲师信息，使用明确占位文案。
    learners: '—',
    // 累加各章的小节数量，作为课程总节数。
    lessons: chapters.reduce((total, chapter) => total + chapter.lessons.length, 0),
    hours: '时长待补充',
    theme: 'blue',
    symbol: '✳',
    instructor: '暂无讲师信息',
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
