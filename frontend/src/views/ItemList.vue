<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../api/request'
import { relativeTime, categoryStyle } from '../utils/format'

const router = useRouter()

const items = ref([])
const total = ref(0)
const loading = ref(true)

// 查询条件（分页 + 筛选 + 排序）
const query = ref({
  page: 1,
  size: 12, // 卡片是 4 列布局，12 条正好铺满 3 行
  type: '',        // LOST / FOUND
  status: '',      // 未认领 / 已认领
  category: '',    // 电子产品/证件/衣物/书籍/其他
  keyword: '',     // 标题关键词
  upordown: 'DESC', // 排序：DESC 最新在前 / ASC 最旧在前
})

const categories = ['电子产品', '证件', '衣物', '书籍', '其他']

async function load() {
  loading.value = true
  try {
    const res = await request.get('/item', { params: query.value })
    items.value = res.data.records
    total.value = res.data.total
  } finally {
    // 必须放 finally：请求失败时如果不关掉 loading，页面会一直卡在骨架屏上，
    // 用户以为在加载，其实早就失败了
    loading.value = false
  }
}

// 点搜索 / 切筛选：重置到第 1 页再查（不重置的话第 3 页筛选完可能直接空白）
function search() {
  query.value.page = 1
  load()
}

function reset() {
  query.value.type = ''
  query.value.status = ''
  query.value.category = ''
  query.value.keyword = ''
  query.value.upordown = 'DESC'
  search()
}

function goDetail(id) {
  router.push(`/items/${id}`)
}

onMounted(load)
</script>

<template>
  <div class="lf-page">
    <div class="lf-page-head">
      <h1 class="lf-page-title">物品列表</h1>
      <p class="lf-page-sub">
        <template v-if="loading">正在加载…</template>
        <template v-else>共找到 <b>{{ total }}</b> 条信息</template>
      </p>
    </div>

    <!-- 筛选栏 -->
    <div class="lf-panel filters">
      <el-input
        v-model="query.keyword"
        placeholder="搜索物品标题，比如：黑色钱包"
        clearable
        class="kw"
        @keyup.enter="search"
        @clear="search"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>

      <el-select v-model="query.type" placeholder="类型" clearable class="sel">
        <el-option label="寻物（丢了东西）" value="LOST" />
        <el-option label="招领（捡到东西）" value="FOUND" />
      </el-select>

      <el-select v-model="query.category" placeholder="分类" clearable class="sel">
        <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
      </el-select>

      <el-select v-model="query.status" placeholder="状态" clearable class="sel">
        <el-option label="未认领" value="UNCLAIMED" />
        <el-option label="已认领" value="CLAIMED" />
      </el-select>

      <el-select v-model="query.upordown" class="sel">
        <el-option label="最新在前" value="DESC" />
        <el-option label="最旧在前" value="ASC" />
      </el-select>

      <div class="filter-actions">
        <el-button type="primary" @click="search">
          <el-icon><Search /></el-icon>搜索
        </el-button>
        <el-button @click="reset">
          <el-icon><Refresh /></el-icon>重置
        </el-button>
      </div>
    </div>

    <!-- 加载中：骨架屏。比一个转圈的 spinner 好，因为页面结构不会跳 -->
    <div v-if="loading" class="grid">
      <div v-for="i in 8" :key="i" class="lf-panel sk-card">
        <el-skeleton animated>
          <template #template>
            <el-skeleton-item variant="image" style="width: 100%; height: 120px" />
            <div style="padding: 14px">
              <el-skeleton-item variant="h3" style="width: 70%" />
              <el-skeleton-item variant="text" style="margin-top: 12px; width: 50%" />
            </div>
          </template>
        </el-skeleton>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else-if="items.length === 0" class="lf-panel empty">
      <el-empty description="没有找到符合条件的物品">
        <el-button type="primary" @click="reset">清空筛选条件</el-button>
      </el-empty>
    </div>

    <!-- 卡片网格 -->
    <div v-else class="grid">
      <div
        v-for="item in items"
        :key="item.id"
        class="lf-panel card"
        @click="goDetail(item.id)"
      >
        <div class="cover">
          <img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.title" />
          <!-- 没图时用「彩色 emoji + 渐变底」占位 ——
               别用 Element 的线性图标，放大到 40px 会显得又细又灰，
               看着像图片加载失败（踩过） -->
          <div v-else class="cover-ph" :style="{ background: categoryStyle(item.category).bg }">
            <span class="cover-emoji">{{ categoryStyle(item.category).emoji }}</span>
          </div>
          <span class="badge" :class="item.type === 'LOST' ? 'lost' : 'found'">
            {{ item.type === 'LOST' ? '寻物' : '招领' }}
          </span>
        </div>

        <div class="body">
          <div class="card-title" :title="item.title">{{ item.title }}</div>

          <div class="meta">
            <span class="meta-item">
              <el-icon><LocationInformation /></el-icon>
              <span class="ellipsis">{{ item.location || '地点未填写' }}</span>
            </span>
            <span class="meta-item">
              <el-icon><Collection /></el-icon>{{ item.category || '未分类' }}
            </span>
          </div>

          <div class="foot">
            <!-- 用「圆点 + 文字」而不是 el-tag：el-tag 的 light 底色在白卡上
                 几乎看不见，只剩一行橙色字，反而不像状态标记 -->
            <span class="status" :class="item.status === 'UNCLAIMED' ? 'open' : 'done'">
              <i class="dot" />{{ item.status === 'UNCLAIMED' ? '未认领' : '已认领' }}
            </span>
            <span class="time">{{ relativeTime(item.createdAt) }}</span>
          </div>
        </div>
      </div>
    </div>

    <el-pagination
      v-if="!loading && total > 0"
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :total="total"
      :page-sizes="[12, 24, 48]"
      layout="prev, pager, next, sizes, total"
      @current-change="load"
      @size-change="search"
    />
  </div>
</template>

<style scoped>
/* ── 筛选栏 ───────────────────────────────────────────────── */
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 16px;
  margin-bottom: 20px;
}

.kw {
  flex: 1 1 240px;
  min-width: 200px;
}

.sel {
  flex: 0 0 150px;
  width: 150px;
}

.filter-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

/* ── 网格 ─────────────────────────────────────────────────── */
/* auto-fill + minmax 让它自动决定每行几个：
   宽屏 4 个、平板 2-3 个、手机 1 个，不用写一堆媒体查询 */
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

/* ── 卡片 ─────────────────────────────────────────────────── */
.card {
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.card:hover {
  transform: translateY(-3px);
  box-shadow: var(--lf-shadow-hover);
  border-color: var(--el-color-primary-light-7);
}

.cover {
  position: relative;
  height: 130px;
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cover-ph {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
}

/* emoji 要单独设行高和字号：默认行高会让它在格子里偏上，
   font-size 给足才撑得住 130px 的色块 */
.cover-emoji {
  font-size: 46px;
  line-height: 1;
  filter: drop-shadow(0 2px 6px rgba(16, 24, 40, 0.12));
}

/* 类型角标压在图片左上角 */
.badge {
  position: absolute;
  top: 10px;
  left: 10px;
  padding: 2px 9px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  color: #fff;
  backdrop-filter: blur(4px);
}

.badge.lost {
  background: rgba(245, 108, 108, 0.92);
}

.badge.found {
  background: rgba(16, 185, 129, 0.92);
}

.body {
  padding: 14px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
  /* 标题最多两行，超出打省略号 —— 不限制的话长标题会把卡片撑得高矮不一 */
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 42px;
}

.meta {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12.5px;
  color: var(--lf-text-sub);
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
}

.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.foot {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--lf-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* 状态：小圆点带一圈光晕，比纯文字更容易被扫到 */
.status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  font-weight: 500;
}

.status .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.status.open {
  color: #d97706;
}

.status.open .dot {
  background: var(--lf-warning);
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.16);
}

.status.done {
  color: var(--lf-text-muted);
}

.status.done .dot {
  background: #cbd5e1;
}

.time {
  font-size: 12px;
  color: var(--lf-text-muted);
}

/* ── 骨架屏 / 空状态 ──────────────────────────────────────── */
.sk-card {
  overflow: hidden;
}

.empty {
  padding: 24px 0;
}

/* ── 窄屏 ─────────────────────────────────────────────────── */
@media (max-width: 640px) {
  .filter-actions {
    margin-left: 0;
    width: 100%;
  }

  .sel {
    flex: 1 1 calc(50% - 6px);
    width: auto;
  }
}
</style>
