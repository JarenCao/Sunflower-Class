<!-- 媒资中心：分页查询素材、上传文件并轮询当前页视频的处理状态。 -->
<script setup lang="ts">
import { PAGE_SIZE } from '../api'
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listMedia, uploadMedia } from '../api'
import type { MediaFile } from '../types'

const percent = ref(0)
const phase = ref('')
// 保存当前上传的取消控制器，离开页面时可终止尚未完成的请求。
let abort: AbortController | undefined
const page = ref(1)
const count = ref(0)
const rows = ref<MediaFile[]>([])
const search = ref('')
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
// 搜索条件变化后从第一页重新查询。
function searchFromFirstPage() {
  page.value = 1
  return refresh()
}

// 只刷新当前页，不因后台轮询改变用户的搜索条件或页码。
async function refresh() {
  try {
    const result = await listMedia(page.value, search.value)
    rows.value = result.items
    count.value = result.count
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}
// 每次选文件创建独立上传任务，进度回调同时更新百分比和处理阶段。
async function onFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  uploading.value = true
  abort = new AbortController()
  percent.value = 0
  try {
    await uploadMedia(
      file,
      // 上传阶段回调：同步进度数值和阶段提示，不将上传完成等同于转码完成。
      (p, label) => {
        percent.value = p
        phase.value = label
      },
      abort.signal,
    )
    ElMessage.success('文件已保存到服务器')
    await refresh()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    uploading.value = false
    // 清空文件选择框，使用户可以再次选择同一文件继续上传。
    if (fileInput.value) fileInput.value.value = ''
  }
}
// 仅在当前页存在待处理视频时刷新状态，离开页面后释放定时器。
let poll: ReturnType<typeof setInterval> | undefined
// 进入页面先加载媒资，再启动定时状态检查。
onMounted(() => {
  refresh()
  // 每十秒检查当前页是否有待处理或处理中的视频；上传期间暂停刷新。
  poll = setInterval(() => {
    if (
      rows.value.some(
        // 仅视频且处于待处理或处理中时需要继续轮询。
        (m) => m.fileType === '20102' && ['20301', '20304'].includes(m.status || ''),
      ) &&
      !uploading.value
    )
      refresh()
  }, 10000)
})
// 组件卸载时停止轮询并取消上传请求，服务端已保存的分片仍可用于续传。
onUnmounted(() => {
  clearInterval(poll)
  abort?.abort()
})
</script>

<template>
  <div class="page-heading compact">
    <div>
      <div class="eyebrow">LIBRARY · 媒资中心</div>
      <h1>媒资中心</h1>
      <p>集中管理课程封面、视频与学习资料。</p>
    </div>
    <button
      class="primary-link"
      :disabled="uploading"
      @click="fileInput?.click()"
    >
      {{ uploading ? '上传中…' : '↑ 上传文件' }}
    </button>
    <input
      ref="fileInput"
      type="file"
      hidden
      @change="onFile"
    />
  </div>
  <!-- 上传入口：普通素材直接上传，视频使用分片上传和服务端续传。 -->
  <div class="upload-banner">
    <div class="upload-mark">↑</div>
    <div>
      <h2>让好内容有一个家</h2>
      <p>文件保存到服务器。中断后重新选择同一个文件，可根据服务端分片记录续传。</p>
    </div>
    <button
      :disabled="uploading"
      @click="fileInput?.click()"
    >
      选择文件
    </button>
  </div>
  <div
    v-if="uploading"
    class="panel"
    style="padding: 20px; margin-bottom: 20px"
  >
    <p>{{ phase }}</p>
    <el-progress :percentage="percent" />
    <el-button @click="abort?.abort()">停止上传</el-button>
  </div>
  <div class="panel">
    <div class="panel-toolbar">
      <div class="toolbar-title">
        <h2>文件列表</h2>
        <span>{{ count }} 个文件</span>
      </div>
      <div class="toolbar-actions">
        <el-input
          v-model="search"
          placeholder="搜索文件名"
          clearable
          style="width: 230px"
          @keyup.enter="searchFromFirstPage"
          @clear="searchFromFirstPage"
        />
        <el-button @click="searchFromFirstPage">查询</el-button>
      </div>
    </div>
    <!-- 媒资列表：展示文件类型、处理状态和资源信息。 -->
    <el-table
      :data="rows"
      empty-text="暂无媒资，上传一个文件试试"
    >
      <el-table-column
        label="文件名称"
        min-width="280"
      >
        <template #default="{ row }">
          <div class="table-course">
            <span class="table-course-icon media-icon">
              {{ row.filename.endsWith('.mp4') ? '▶' : '▧' }}
            </span>
            <div>
              <strong>{{ row.filename }}</strong>
              <small>{{ row.id }}</small>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="处理状态"
        width="120"
      >
        <template #default="{ row }">
          <span
            class="pill"
            :class="row.status === '20302' ? 'pill-green' : 'pill-blue'"
          >
            {{
              row.status === '20300'
                ? '已隐藏'
                : row.status === '20303'
                  ? '处理失败'
                  : row.status === '20304'
                    ? '正在转码'
                    : row.status === '20302'
                      ? row.fileType === '20102'
                        ? '转码完成'
                        : '已上传'
                      : '等待处理'
            }}
          </span>
        </template>
      </el-table-column>
      <el-table-column
        prop="createDate"
        label="上传时间"
        width="150"
      />
      <el-table-column
        label="用途"
        width="120"
      >
        <template #default="{ row }">
          {{ row.filename.endsWith('.mp4') ? '课程视频' : '课程素材' }}
        </template>
      </el-table-column>
      <el-table-column
        label="查看"
        width="110"
      >
        <template #default="{ row }">
          <a
            :href="`/api/media/files/${row.id}/content`"
            target="_blank"
            rel="noopener"
          >
            打开文件
          </a>
        </template>
      </el-table-column>
    </el-table>
    <!-- 媒资分页：总条数由服务端返回，每次切换重新加载当前页。 -->
    <el-pagination
      v-model:current-page="page"
      :page-size="PAGE_SIZE"
      :total="count"
      layout="prev, pager, next, total"
      @current-change="refresh"
    />
  </div>
</template>
