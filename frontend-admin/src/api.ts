/* 机构端接口适配层：封装内容与媒资请求、响应错误及文件分片上传。 */
import axios from 'axios'
import SparkMD5 from 'spark-md5'
import type {
  Category,
  Course,
  CourseInput,
  MediaFile,
  PageResult,
  RestResponse,
  Teachplan,
} from './types'

// 管理列表统一每页十条；首页摘要可以单独指定数量。
export const PAGE_SIZE = 10

// 内容与媒资使用不同服务前缀；上传请求允许更长的等待时间。
const http = axios.create({ baseURL: '/api/content', timeout: 30000 })
const mediaHttp = axios.create({ baseURL: '/api/media', timeout: 120000 })
// 兼容后端不同异常响应结构，将可读错误交给页面统一提示。
for (const client of [http, mediaHttp]) {
  client.interceptors.response.use(
    // 成功响应保持 Axios 原结构，业务码由需要的调用方单独检查。
    (response) => response,
    // 网络或 HTTP 异常转为 Error，优先采用服务端的可读提示。
    (error) =>
      Promise.reject(
        new Error(
          error.response?.data?.msg ||
            error.response?.data?.errMessage ||
            error.response?.data?.message ||
            error.message ||
            '请求失败',
        ),
      ),
  )
}
// HTTP 成功不等于业务成功，统一检查后端响应码。
function check<T>(data: RestResponse<T>): T {
  if (data.code !== 0) throw new Error(data.msg)
  return data.result
}
// 分页参数放在查询字符串中，名称和状态筛选条件放在请求体中。
export async function listCourses(
  page = 1,
  pageSize = PAGE_SIZE,
  courseName = '',
  auditStatus = '',
  publishStatus = '',
): Promise<PageResult<Course>> {
  return (
    await http.post(
      '/course/list',
      { courseName, auditStatus, publishStatus },
      { params: { pageNo: page, pageSize } },
    )
  ).data
}
// 编辑页读取课程详情，返回数据包含基础信息和营销信息。
export async function getCourse(id: number): Promise<Course> {
  return (await http.get(`/course/${id}`)).data
}
// 已有 id 时更新课程；没有 id 时创建课程，由服务端返回保存结果。
export async function saveCourse(input: CourseInput): Promise<Course> {
  return (await http.request({ url: '/course', method: input.id ? 'PUT' : 'POST', data: input }))
    .data
}
/** 将后端分类树字段转换成页面使用的 children 结构。 */
export async function listCategories(): Promise<Category[]> {
  type Node = { id: string; name: string; childrenTreeNodes?: Node[] }
  // 递归转换单个分类节点，其子节点按相同规则映射。
  const map = (node: Node): Category => ({
    id: node.id,
    name: node.name,
    children: (node.childrenTreeNodes || []).map(map),
  })
  return (await http.get<Node[]>('/category/node', { params: { id: '1' } })).data.map(map)
}
// 沿用后端的 techplan 路径拼写，获取课程的章节与小节树。
export async function getTeachplan(courseId: number): Promise<Teachplan[]> {
  return (await http.get(`/techplan/${courseId}/tree-nodes`)).data
}
// parentId 为 0 表示章，否则表示小节；传入 id 时复用此接口修改名称。
export async function addTeachplan(
  courseId: number,
  pname: string,
  parentId = 0,
  id?: number,
): Promise<void> {
  await http.post('/teachplan', {
    id,
    courseId,
    pname,
    parentId,
    grade: parentId ? 2 : 1,
    mediaType: '1',
    isPreview: '0',
  })
}

// 删除章时后端同步删除小节和绑定；媒资文件本身继续保留。
export async function deleteTeachplan(id: number): Promise<void> {
  await http.delete(`/teachplan/${id}`)
}

// 只在同一父节点内移动一位，由后端重新编号并校验机构归属。
export async function moveTeachplan(id: number, direction: 'up' | 'down'): Promise<void> {
  await http.put(`/teachplan/${id}/move`, null, { params: { direction } })
}
// 媒资列表按文件名筛选，每页数量与其他管理列表保持一致。
export async function listMedia(page = 1, filename = ''): Promise<PageResult<MediaFile>> {
  return (
    await mediaHttp.post('/files', { filename }, { params: { pageNo: page, pageSize: PAGE_SIZE } })
  ).data
}
/** 普通素材直接上传；视频通过服务端分片记录实现续传，不使用本地存储。 */
export async function uploadMedia(
  file: File,
  // 未传进度处理函数时使用空回调，上传流程仍正常执行。
  progress: (percent: number, phase: string) => void = () => {},
  signal?: AbortSignal,
): Promise<MediaFile | undefined> {
  if (!file.size) throw new Error('不能上传空文件')
  const chunkSize = 5 * 1024 * 1024
  if (!file.type.startsWith('video/') && !/\.(mp4|avi|mov|mkv|webm)$/i.test(file.name)) {
    const body = new FormData()
    body.append('filedata', file)
    return (
      await mediaHttp.post('/upload/coursefile', body, {
        signal,
        // 普通文件按已传字节计算进度；未知总长度时使用所选文件大小。
        onUploadProgress: (event) =>
          progress(Math.round((event.loaded / (event.total || file.size)) * 100), '上传中'),
      })
    ).data
  }
  const chunks = Math.ceil(file.size / chunkSize)
  // 分段计算整文件 MD5，避免一次读入完整大文件。
  const hash = new SparkMD5.ArrayBuffer()
  for (let i = 0; i < chunks; i++) {
    signal?.throwIfAborted()
    hash.append(await file.slice(i * chunkSize, (i + 1) * chunkSize).arrayBuffer())
    progress(Math.round(((i + 1) / chunks) * 100), '计算文件校验值')
  }
  const fileMd5 = hash.end()
  // 封装分片接口的查询参数、取消信号和业务码校验，返回具体业务结果。
  const post = async <T>(path: string, params: Record<string, unknown>) =>
    check<T>((await mediaHttp.post(path, null, { params, signal })).data)
  // 先检查完整文件，已经上传成功时不再重复传输分片。
  if (await post<boolean>('/upload/checkfile', { fileMd5 })) {
    progress(100, '文件已存在')
    return
  }
  // 重新选择同一文件时，跳过已保存在服务器上的分片。
  for (let chunk = 0; chunk < chunks; chunk++) {
    signal?.throwIfAborted()
    if (!(await post<boolean>('/upload/checkchunk', { fileMd5, chunk }))) {
      const body = new FormData()
      body.append('file', file.slice(chunk * chunkSize, (chunk + 1) * chunkSize), file.name)
      body.append('fileMd5', fileMd5)
      body.append('chunk', String(chunk))
      check((await mediaHttp.post('/upload/uploadchunk', body, { signal })).data)
    }
    progress(Math.round(((chunk + 1) / chunks) * 100), '上传分片（可重新选文件续传）')
  }
  // 分片传输完成后仍需服务端合并和校验；此时进度 100% 不代表视频转码完成。
  progress(100, '合并与校验中')
  await post('/upload/mergechunks', { fileMd5, fileName: file.name, chunkTotal: chunks })
  progress(100, '上传完成')
}
// 将选中的媒资编号和文件名关联到指定教学计划。
export async function bindMedia(teachplanId: number, media: MediaFile): Promise<void> {
  check(
    (
      await http.post('/teachplan/media/bind', {
        teachplanId,
        mediaId: media.id,
        fileName: media.filename,
      })
    ).data,
  )
}
// 提交审核只改变审核流程状态，不等同于审核通过或课程发布。
export async function submitAudit(courseId: number): Promise<void> {
  check((await http.post(`/courseaudit/commit/${courseId}`)).data)
}
// 请求服务端发布课程；是否允许发布由后端审核状态校验决定。
export async function publishCourse(courseId: number): Promise<void> {
  check((await http.post(`/coursepublish/${courseId}`)).data)
}

// 下架课程时同步撤销学员端公开快照；后端会校验当前机构与发布状态。
export async function offlineCourse(courseId: number): Promise<void> {
  check((await http.put(`/coursepublish/${courseId}/offline`)).data)
}

// 删除未发布或已下架课程及其关系记录，媒资文件仍由媒资服务保留。
export async function deleteCourse(courseId: number): Promise<void> {
  await http.delete(`/course/${courseId}`)
}
