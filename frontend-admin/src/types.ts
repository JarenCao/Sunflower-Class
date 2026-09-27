/* 机构端数据契约：描述课程表单、分页、分类树、教学计划及媒资响应。 */
/** 课程详情模型，包含基础字段、营销字段与状态编码。 */
export interface Course {
  id: number
  name: string
  description: string
  users: string
  tags: string
  mt: string
  st: string
  grade: string
  teachmode: string
  pic: string
  charge: string
  price: number
  originalPrice: number
  auditStatus: string
  status: string
  companyId?: number
  createDate?: string
}

/** 保存课程的输入模型，排除服务端管理的状态及机构字段，新建时可不传 id。 */
export interface CourseInput extends Omit<
  Course,
  'id' | 'auditStatus' | 'status' | 'companyId' | 'createDate'
> {
  id?: number
}

export interface PageResult<T> {
  items: T[]
  count: number
  page: number
  pageSize: number
}
export interface RestResponse<T> {
  code: number
  msg: string
  result: T
}
/** 课程分类节点，children 保存下一级分类。 */
export interface Category {
  id: string
  name: string
  children?: Category[]
}
/** 章或小节节点，兼容接口中的父节点字段命名并携带绑定媒资。 */
export interface Teachplan {
  id: number
  pname: string
  parentId?: number
  parentid?: number
  grade?: number
  orderby?: number
  teachPlanTreeNodes?: Teachplan[]
  mediaFilename?: string
  teachplanMedia?: { mediaId: string; mediaFilename: string }
}
/** 媒资列表与上传结果模型，状态及文件类型使用后端业务编码。 */
export interface MediaFile {
  id: string
  filename: string
  fileType?: string
  status?: string
  url?: string
  createDate?: string
}
