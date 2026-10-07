<!-- 真实课程订单：支付平台确认后才显示已支付，学习服务回执后才提示开通。 -->
<script setup lang="ts">
import { errorMessage } from '../../../frontend-shared/error-message'
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import QRCode from 'qrcode'
import {
  createCourseOrder,
  getCourseOrders,
  getOrderCourseCover,
  getCourseOrder,
  payCourseOrder,
  refreshCourseOrder,
} from '../data'
import type { CourseOrder } from '../data'
const route = useRoute(),
  router = useRouter()
const orders = ref<CourseOrder[]>([]),
  selected = ref<CourseOrder>(),
  notice = ref(''),
  qr = ref(''),
  busy = ref(false)
const page = ref(1),
  count = ref(0)
const loaded = ref(false),
  resultStatus = ref('')
const loading = ref(true),
  listError = ref(''),
  filterStatus = ref('')
const drawer = ref<HTMLDialogElement>()
const covers = ref<Record<number, string | undefined>>({})
const failedCovers = ref<Set<number>>(new Set())
const tabs = [
  { value: '', label: '全部订单' },
  { value: '60201', label: '待支付' },
  { value: '60202', label: '已支付' },
  { value: '60205', label: '已完成' },
  { value: '60203', label: '已关闭' },
  { value: '60204', label: '已退款' },
]
let listVersion = 0
// 使用浏览器原生对话框处理焦点约束和 Escape，避免新增弹层依赖。
watch(selected, async (order) => {
  await nextTick()
  if (order && !drawer.value?.open) drawer.value?.showModal()
  if (!order && drawer.value?.open) drawer.value.close()
})
async function closeDetails() {
  // 关闭时立即作废在途详情请求，避免响应稍后重新打开抽屉。
  requestVersion++
  stop()
  qr.value = ''
  selected.value = undefined
  await router.replace('/orders')
}
async function filter(value: string) {
  if (value === filterStatus.value) return
  filterStatus.value = value
  page.value = 1
  await load()
}
async function loadCovers(rows: CourseOrder[]) {
  // 当前页相同课程只查询一次，封面失败不影响真实订单与金额展示。
  await Promise.all(
    [...new Set(rows.map((order) => order.courseId))].map(async (id) => {
      if (Object.prototype.hasOwnProperty.call(covers.value, id)) return
      try {
        covers.value[id] = await getOrderCourseCover(id)
      } catch {
        covers.value[id] = undefined
      }
    }),
  )
}
function formatTime(value: string) {
  return value.replace('T', ' ')
}
function money(value: number) {
  return Number(value).toFixed(2)
}
let timer: ReturnType<typeof setTimeout> | undefined
let closed = false
let requestVersion = 0
function stop() {
  if (timer) clearTimeout(timer)
  timer = undefined
}
function status(order: CourseOrder) {
  return (
    (
      {
        '60201': '待支付',
        '60202': '已支付',
        '60203': '已关闭',
        '60204': '已退款',
        '60205': '已完成',
      } as Record<string, string>
    )[order.status] || '状态异常'
  )
}
async function load() {
  const version = ++listVersion
  loading.value = true
  listError.value = ''
  try {
    const data = await getCourseOrders(page.value, filterStatus.value || undefined)
    if (closed || version !== listVersion) return
    orders.value = data.items
    resultStatus.value = filterStatus.value
    count.value = data.count
    void loadCovers(data.items)
  } catch (error) {
    if (closed || version !== listVersion) return
    listError.value = errorMessage(error)
    orders.value = []
    count.value = 0
  } finally {
    if (version === listVersion) {
      loading.value = false
      loaded.value = true
    }
  }
}
async function select(order: CourseOrder) {
  if (busy.value) return
  requestVersion++
  stop()
  qr.value = ''
  selected.value = order
  notice.value = ''
  await router.replace({ path: '/orders', query: { id: order.id } })
}
/** 只在二维码展示期间轮询，离开页面取消；二维码自身不能证明支付成功。 */
function poll() {
  stop()
  if (!closed && qr.value && selected.value?.status === '60201')
    timer = setTimeout(async () => {
      await refresh(false)
      poll()
    }, 5000)
}
async function pay() {
  if (!selected.value || busy.value) return
  const payingId = selected.value.id
  busy.value = true
  notice.value = ''
  try {
    const image = await QRCode.toDataURL(await payCourseOrder(payingId), {
      width: 240,
      margin: 2,
    })
    // 路由切换后旧支付响应不得把另一张二维码挂到新订单上。
    if (closed || selected.value?.id !== payingId) return
    qr.value = image
    poll()
  } catch (error) {
    notice.value = errorMessage(error)
    qr.value = ''
  } finally {
    busy.value = false
  }
}
async function refresh(manual = true) {
  if (!selected.value || busy.value) return
  const refreshingId = selected.value.id
  busy.value = true
  if (manual) notice.value = ''
  try {
    const order = await refreshCourseOrder(refreshingId)
    if (closed || selected.value?.id !== refreshingId) return
    selected.value = order
    if (selected.value.status !== '60201') {
      stop()
      qr.value = ''
    }
    await load()
  } catch (error) {
    notice.value = errorMessage(error)
  } finally {
    busy.value = false
  }
}
async function changePage(next: number) {
  page.value = next
  await load()
}
async function routeOrder() {
  const version = ++requestVersion
  stop()
  qr.value = ''
  notice.value = ''
  const id = route.query.id,
    course = route.query.course
  // 列表点击已经打开对应订单，路由同步只更新最新数据，不关闭后重开。
  if (typeof id !== 'string' || selected.value?.id !== id) selected.value = undefined
  try {
    if (typeof id === 'string') {
      const order = await getCourseOrder(id)
      if (closed || version !== requestVersion) return
      selected.value = order
      void loadCovers([order])
    } else if (typeof course === 'string') {
      const order = await createCourseOrder(Number(course))
      if (closed || version !== requestVersion) return
      selected.value = order
      await router.replace({ path: '/orders', query: { id: order.id } })
      // 新建订单才需要更新列表，查看或关闭详情不重新加载列表。
      await load()
    }
  } catch (error) {
    if (closed || version !== requestVersion) return
    selected.value = undefined
    notice.value = errorMessage(error)
  }
}
onMounted(() => {
  void load()
  void routeOrder()
})
watch(() => [route.query.id, route.query.course], routeOrder)
onUnmounted(() => {
  closed = true
  requestVersion++
  listVersion++
  stop()
})
</script>
<template>
  <div class="inner-banner orders-banner">
    <div class="container">
      <span class="section-kicker">MY ORDERS</span>
      <h1>我的订单</h1>
      <p>每一份学习计划，都从这里开始。</p>
    </div>
  </div>
  <div class="container inner-content orders-content">
    <div class="orders-heading">
      <div>
        <h2>课程订单</h2>
        <p>查看购买记录，继续你的学习旅程。</p>
      </div>
      <span>共 {{ count }} 笔订单</span>
    </div>
    <div
      class="order-tabs"
      role="group"
      aria-label="订单状态筛选"
    >
      <button
        v-for="tab in tabs"
        :key="tab.value"
        :class="{ active: filterStatus === tab.value }"
        :aria-pressed="filterStatus === tab.value"
        @click="filter(tab.value)"
      >
        {{ tab.label }}
      </button>
    </div>
    <p
      v-if="notice && !selected"
      class="integration-note"
      role="alert"
    >
      {{ notice }}
    </p>
    <!-- 首次进入才显示骨架；筛选时保留内容，避免骨架与空状态反复替换。 -->
    <div
      class="order-query-status"
      role="status"
    >
      <span v-if="loading && loaded">正在查询订单…</span>
    </div>
    <div
      v-if="loading && !loaded"
      class="orders-loading"
      role="status"
      aria-label="正在加载订单"
    >
      <div
        v-for="index in 3"
        :key="index"
        class="skeleton-order"
      >
        <span></span>
        <div></div>
      </div>
    </div>
    <div
      v-else-if="listError"
      class="order-empty"
      role="alert"
    >
      <h3>订单暂时加载失败</h3>
      <p>{{ listError }}</p>
      <button
        class="gold-button"
        @click="load"
      >
        重新加载
      </button>
    </div>
    <div
      v-else-if="!orders.length"
      class="order-empty"
    >
      <span
        class="empty-order-icon"
        aria-hidden="true"
      >
        ▤
      </span>
      <h3>{{ resultStatus ? '暂无此状态的订单' : '还没有课程订单' }}</h3>
      <p>
        {{
          resultStatus ? '切换其他状态，查看你的购买记录。' : '找到感兴趣的课程，开始下一段学习。'
        }}
      </p>
      <button
        v-if="resultStatus"
        class="gold-button"
        @click="filter('')"
      >
        查看全部订单
      </button>
      <RouterLink
        v-else
        to="/courses"
        class="gold-button"
      >
        去发现课程 →
      </RouterLink>
    </div>
    <div
      v-else
      class="orders-list"
    >
      <article
        v-for="order in orders"
        :key="order.id"
        class="order-card"
      >
        <div class="order-card-header">
          <span>下单时间 {{ formatTime(order.createdAt) }}</span>
          <span class="order-number">订单号 {{ order.id }}</span>
          <span
            class="status-badge"
            :class="`status-${order.status}`"
          >
            {{ status(order) }}
          </span>
        </div>
        <div class="order-card-body">
          <div class="order-cover">
            <img
              v-if="covers[order.courseId] && !failedCovers.has(order.courseId)"
              :src="covers[order.courseId]"
              :alt="`${order.courseName}的封面`"
              loading="lazy"
              @error="failedCovers.add(order.courseId)"
            />
            <span v-else>暂无封面</span>
          </div>
          <div class="order-course">
            <h3>{{ order.courseName }}</h3>
            <span class="course-kind">在线课程</span>
            <p v-if="order.status === '60201'">支付截止 {{ formatTime(order.expiresAt) }}</p>
            <p v-else-if="order.status === '60202' || order.status === '60205'">
              {{ order.learningActivated ? '学习资格已开通' : '支付已确认，学习资格同步中' }}
            </p>
            <p v-else-if="order.status === '60203'">订单已关闭，可前往课程详情重新选购。</p>
            <p v-else-if="order.status === '60204'">订单已退款</p>
          </div>
          <div class="order-amount">
            <span>订单金额</span>
            <strong>
              <small>¥</small>
              {{ money(order.price) }}
            </strong>
          </div>
          <div class="order-actions">
            <button
              v-if="order.status === '60201'"
              class="gold-button"
              :disabled="busy"
              @click="select(order)"
            >
              继续支付
            </button>
            <RouterLink
              v-else-if="order.learningActivated && ['60202', '60205'].includes(order.status)"
              class="gold-button"
              to="/my-courses"
            >
              去学习
            </RouterLink>
            <button
              class="detail-button"
              :disabled="busy"
              @click="select(order)"
            >
              订单详情 →
            </button>
          </div>
        </div>
      </article>
    </div>
    <div
      v-if="loaded && !listError && count > 0"
      class="pagination order-pages"
    >
      <button
        :disabled="loading || page <= 1"
        @click="changePage(page - 1)"
      >
        上一页
      </button>
      <span>第 {{ page }} / {{ Math.ceil(count / 10) }} 页</span>
      <button
        :disabled="loading || page * 10 >= count"
        @click="changePage(page + 1)"
      >
        下一页
      </button>
    </div>
  </div>
  <dialog
    ref="drawer"
    class="order-drawer"
    aria-labelledby="order-detail-title"
    @cancel.prevent="closeDetails"
    @click.self="closeDetails"
  >
    <section
      v-if="selected"
      class="drawer-content"
    >
      <header class="drawer-header">
        <div>
          <span class="section-kicker">ORDER DETAILS</span>
          <h2 id="order-detail-title">订单详情</h2>
        </div>
        <button
          class="close-drawer"
          aria-label="关闭订单详情"
          @click="closeDetails"
        >
          ×
        </button>
      </header>
      <div class="drawer-course">
        <div class="order-cover">
          <img
            v-if="covers[selected.courseId] && !failedCovers.has(selected.courseId)"
            :src="covers[selected.courseId]"
            :alt="`${selected.courseName}的封面`"
            @error="failedCovers.add(selected.courseId)"
          />
          <span v-else>暂无封面</span>
        </div>
        <h3>{{ selected.courseName }}</h3>
      </div>
      <div class="drawer-status">
        <span
          class="status-badge"
          :class="`status-${selected.status}`"
        >
          {{ status(selected) }}
        </span>
        <strong>¥{{ money(selected.price) }}</strong>
      </div>
      <dl class="order-facts">
        <div>
          <dt>订单编号</dt>
          <dd>{{ selected.id }}</dd>
        </div>
        <div>
          <dt>下单时间</dt>
          <dd>{{ formatTime(selected.createdAt) }}</dd>
        </div>
        <div v-if="selected.status === '60201'">
          <dt>支付截止</dt>
          <dd>{{ formatTime(selected.expiresAt) }}</dd>
        </div>
        <div>
          <dt>支付方式</dt>
          <dd>支付宝沙箱</dd>
        </div>
      </dl>
      <p
        v-if="notice"
        class="integration-note"
        role="alert"
      >
        {{ notice }}
      </p>
      <div
        v-if="selected.status === '60201'"
        class="payment-section"
      >
        <h3>扫码支付，开启学习</h3>
        <p>请确认课程与金额，使用支付宝沙箱客户端扫码。</p>
        <div
          v-if="qr"
          class="payment-qr"
        >
          <img
            :src="qr"
            alt="支付宝沙箱订单支付二维码"
          />
          <p>等待支付确认…付款后将自动更新状态。</p>
        </div>
        <button
          class="gold-button"
          :disabled="busy"
          @click="pay"
        >
          {{ busy ? '处理中…' : qr ? '重新获取二维码' : '生成支付二维码' }}
        </button>
        <button
          class="detail-button"
          :disabled="busy"
          @click="refresh(true)"
        >
          刷新支付状态
        </button>
      </div>
      <div
        v-else-if="['60202', '60205'].includes(selected.status)"
        class="learning-result"
      >
        <h3>{{ selected.learningActivated ? '学习资格已开通' : '支付成功，正在开通学习资格' }}</h3>
        <p>
          {{
            selected.learningActivated
              ? '前往我的学习查看课程及当前有效期。'
              : '请稍后刷新，学习服务确认后即可查看。'
          }}
        </p>
        <RouterLink
          v-if="selected.learningActivated"
          class="gold-button"
          to="/my-courses"
        >
          前往我的学习 →
        </RouterLink>
        <button
          v-else
          class="gold-button"
          :disabled="busy"
          @click="refresh(true)"
        >
          刷新开通状态
        </button>
      </div>
      <div
        v-else
        class="closed-result"
      >
        <p>{{ selected.status === '60204' ? '此订单已退款。' : '此订单已关闭。' }}</p>
        <RouterLink :to="`/courses/${selected.courseId}`">查看课程详情 →</RouterLink>
      </div>
    </section>
  </dialog>
</template>
<style scoped>
/* 横向订单卡片沿用学员端配色，金额和操作保持独立区域。 */
.orders-banner {
  padding: 42px 0 46px;
}
.orders-banner h1 {
  font-size: 36px;
}
.orders-content {
  padding-top: 36px;
}
.orders-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.orders-heading h2 {
  margin: 0;
  font-size: 24px;
}
.orders-heading p {
  color: #83908a;
  font-size: 13px;
}
.orders-heading > span {
  color: #7d8780;
  font-size: 13px;
}
.order-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 22px 0 28px;
  border-bottom: 1px solid #eae7de;
}
.order-tabs button {
  padding: 12px 16px;
  border: 0;
  border-bottom: 3px solid transparent;
  background: transparent;
  color: #7b8580;
}
.order-tabs button.active {
  border-bottom-color: #e5b438;
  color: #26373b;
  font-weight: 700;
}
.order-query-status {
  min-height: 24px;
  margin-top: -16px;
  margin-bottom: 8px;
  color: #83908a;
  font-size: 12px;
}
.orders-list {
  display: grid;
  gap: 20px;
}
.order-card {
  border: 1px solid #ebe7dd;
  background: #fff;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 4px 18px #26373b05;
}
.order-card-header {
  display: flex;
  gap: 24px;
  align-items: center;
  padding: 14px 22px;
  background: #faf9f5;
  border-bottom: 1px solid #f0ede5;
  font-size: 12px;
  color: #8a928a;
  flex-wrap: wrap;
}
.order-card-header .status-badge {
  margin-left: auto;
}
.order-card-body {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr) 110px 126px;
  gap: 22px;
  align-items: center;
  padding: 24px 22px;
}
.order-cover {
  width: 150px;
  height: 98px;
  background: #eef1e6;
  color: #8b9589;
  border-radius: 9px;
  overflow: hidden;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  font-size: 12px;
}
.order-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.order-course h3 {
  margin: 0 0 10px;
  font-size: 17px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.course-kind {
  font-size: 11px;
  background: #f8f4e8;
  color: #987b38;
  padding: 4px 8px;
  border-radius: 4px;
}
.order-course p {
  margin: 12px 0 0;
  font-size: 12px;
  color: #8a938a;
  line-height: 1.6;
}
.order-amount > span {
  display: block;
  color: #91998f;
  font-size: 11px;
  margin-bottom: 7px;
}
.order-amount strong {
  color: #26373b;
  font-size: 23px;
  white-space: nowrap;
}
.order-amount small {
  font-size: 14px;
  margin-right: 3px;
}
.order-actions {
  display: grid;
  justify-items: stretch;
  gap: 12px;
  text-align: center;
}
.order-actions .gold-button {
  padding: 10px 14px;
  font-size: 13px;
  justify-content: center;
}
.detail-button {
  border: 0;
  background: transparent;
  color: #8f732f;
  font-size: 12px;
  padding: 8px 0;
}
.status-badge {
  display: inline-block;
  padding: 5px 10px;
  background: #f0f1ed;
  color: #859080;
  border-radius: 5px;
  font-size: 12px;
  white-space: nowrap;
}
.status-60201 {
  background: #fff0d7;
  color: #ae761e;
}
.status-60202,
.status-60205 {
  background: #e8f4ec;
  color: #39774d;
}
.status-60204 {
  background: #eef0f7;
  color: #657396;
}
.order-pages {
  display: flex;
  gap: 16px;
  justify-content: center;
  margin: 28px 0;
  align-items: center;
  font-size: 13px;
}
.order-pages button {
  padding: 8px 14px;
  border: 1px solid #e8e4d9;
  background: #fff;
  border-radius: 6px;
}
.order-empty {
  text-align: center;
  padding: 55px 20px;
  border: 1px dashed #e8e4d9;
  border-radius: 14px;
  background: #fff;
}
.order-empty p {
  color: #8a938a;
  font-size: 13px;
  margin-bottom: 26px;
}
.empty-order-icon {
  font-size: 32px;
  color: #b8a675;
}
.skeleton-order {
  display: flex;
  gap: 24px;
  padding: 30px;
  margin-bottom: 20px;
  border: 1px solid #eeeae2;
  border-radius: 14px;
}
.skeleton-order span {
  width: 150px;
  height: 98px;
  background: #efeee8;
  border-radius: 9px;
}
.skeleton-order div {
  flex: 1;
  background: linear-gradient(
    #efeee8 0 18px,
    transparent 18px 35px,
    #f7f6f1 35px 55px,
    transparent 55px
  );
}
/* 原生 dialog 提供背景遮罩与键盘焦点控制；手机使用全屏详情。 */
.order-drawer {
  position: fixed;
  inset: 0 0 0 auto;
  margin: 0;
  padding: 0;
  border: 0;
  width: min(480px, 100vw);
  max-width: 100vw;
  height: 100dvh;
  max-height: 100dvh;
  background: #fffdf8;
  color: #26373b;
  box-shadow: -20px 0 50px #26373b20;
}
.order-drawer::backdrop {
  background: #26373b65;
}
.drawer-content {
  padding: 28px;
  min-height: 100%;
}
.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 24px;
  border-bottom: 1px solid #eae7de;
}
.drawer-header h2 {
  margin: 9px 0 0;
  font-size: 24px;
}
.close-drawer {
  border: 0;
  background: #f0eee6;
  border-radius: 50%;
  width: 34px;
  height: 34px;
  font-size: 24px;
  color: #7e877c;
}
.drawer-course {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 26px 0;
}
.drawer-course .order-cover {
  width: 112px;
  height: 78px;
}
.drawer-course h3 {
  font-size: 17px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.drawer-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px;
  background: #f5f3e9;
  border-radius: 10px;
}
.drawer-status strong {
  font-size: 26px;
}
.order-facts {
  margin: 25px 0;
  font-size: 12px;
}
.order-facts > div {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  margin: 18px 0;
}
.order-facts dt {
  color: #8a938a;
  white-space: nowrap;
}
.order-facts dd {
  margin: 0;
  text-align: right;
  overflow-wrap: anywhere;
}
.payment-section,
.learning-result,
.closed-result {
  border-top: 1px solid #eae7de;
  padding-top: 20px;
}
.payment-section h3,
.learning-result h3 {
  font-size: 18px;
}
.payment-section p,
.learning-result p,
.closed-result p {
  font-size: 13px;
  color: #839080;
  line-height: 1.8;
}
.payment-section > button {
  display: block;
  width: 100%;
  margin-top: 12px;
}
.payment-qr {
  padding: 15px;
  margin: 18px 0;
  background: #fff;
  border: 1px solid #eae7de;
  border-radius: 10px;
  text-align: center;
}
.payment-qr img {
  width: 220px;
  max-width: 100%;
}
.closed-result a {
  color: #927538;
  font-size: 13px;
}
@media (max-width: 760px) {
  .order-card-body {
    grid-template-columns: 100px minmax(0, 1fr);
    gap: 16px;
    padding: 18px;
  }
  .order-cover {
    width: 100px;
    height: 78px;
  }
  .order-amount {
    grid-column: 1;
  }
  .order-actions {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    flex-wrap: wrap;
  }
  .order-card-header {
    padding: 12px 18px;
    gap: 10px;
  }
  .order-number {
    order: 3;
    width: 100%;
    overflow-wrap: anywhere;
  }
  .order-tabs button {
    padding: 12px 9px;
    font-size: 13px;
  }
  .order-drawer {
    width: 100vw;
  }
}
</style>
