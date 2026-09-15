<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../api/request'
import ItemCard from '../components/ItemCard.vue'
import { fullTime } from '../utils/format'

const router = useRouter()

const profile = ref(null)
const items = ref([])
const total = ref(0)
const loading = ref(true)

// 三个数字：总发布 / 未认领 / 已认领。也是筛选入口（点一下就筛）
const stats = ref({ total: 0, unclaimed: 0, claimed: 0 })
// 当前按哪个状态在筛：'' | 'UNCLAIMED' | 'CLAIMED'
const statusFilter = ref('')
const typeFilter = ref('')

const query = ref({ page: 1, size: 12, upordown: 'DESC' })

const avatarText = computed(() => (profile.value?.nickname || profile.value?.username || '用')[0])

const STAT_ITEMS = [
  { key: '', label: '全部发布' },
  { key: 'UNCLAIMED', label: '未认领' },
  { key: 'CLAIMED', label: '已认领' },
]

function statValue(key) {
  if (key === '') return stats.value.total
  return key === 'UNCLAIMED' ? stats.value.unclaimed : stats.value.claimed
}

async function loadProfile() {
  const res = await request.get('/user/me')
  profile.value = res.data
}

/**
 * 三个统计数字。
 * 用 size=1 分别查三次 —— 我们只要 total，不需要真的把记录拉回来，
 * 每条响应因此只有一个元素。三次请求都很轻，比让后端新加一个统计接口划算。
 */
async function loadStats() {
  const [all, unclaimed, claimed] = await Promise.all([
    request.get('/item/my', { params: { page: 1, size: 1 } }),
    request.get('/item/my', { params: { page: 1, size: 1, status: 'UNCLAIMED' } }),
    request.get('/item/my', { params: { page: 1, size: 1, status: 'CLAIMED' } }),
  ])
  stats.value = {
    total: all.data.total,
    unclaimed: unclaimed.data.total,
    claimed: claimed.data.total,
  }
}

async function loadItems() {
  loading.value = true
  try {
    const res = await request.get('/item/my', {
      params: { ...query.value, status: statusFilter.value, type: typeFilter.value },
    })
    items.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

/** 点统计数字 = 按这个状态筛（再点一次取消） */
function pickStatus(key) {
  statusFilter.value = statusFilter.value === key ? '' : key
  query.value.page = 1
  loadItems()
}

function pickType(value) {
  typeFilter.value = value
  query.value.page = 1
  loadItems()
}

onMounted(async () => {
  await Promise.all([loadProfile(), loadStats()])
  await loadItems()
})
</script>

<template>
  <div class="lf-page">
    <!-- ── 资料卡 ─────────────────────────────────────────── -->
    <div class="lf-panel hero">
      <div class="hero-bg" />

      <div class="hero-body">
        <div class="avatar">{{ avatarText }}</div>

        <div class="who">
          <h1 class="name">
            {{ profile?.nickname || profile?.username || '—' }}
          </h1>
          <div class="sub">
            <span><el-icon><User /></el-icon>@{{ profile?.username }}</span>
            <span v-if="profile?.phone"><el-icon><Phone /></el-icon>{{ profile.phone }}</span>
            <span v-if="profile?.createdAt">
              <el-icon><Calendar /></el-icon>{{ fullTime(profile.createdAt).slice(0, 10) }} 加入
            </span>
          </div>
        </div>

        <el-button class="publish-btn" type="primary" @click="router.push('/publish')">
          <el-icon><Plus /></el-icon>发布物品
        </el-button>
      </div>

      <!-- 统计条：三个数字同时兼任筛选入口 -->
      <div class="stats">
        <button
          v-for="s in STAT_ITEMS"
          :key="s.key"
          class="stat"
          :class="{ active: statusFilter === s.key }"
          @click="pickStatus(s.key)"
        >
          <span class="stat-num">{{ statValue(s.key) }}</span>
          <span class="stat-label">{{ s.label }}</span>
        </button>
      </div>
    </div>

    <!-- ── 我的发布 ───────────────────────────────────────── -->
    <div class="section-head">
      <h2 class="section-title">我的发布</h2>

      <el-radio-group v-model="typeFilter" @change="pickType(typeFilter)">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="LOST">寻物</el-radio-button>
        <el-radio-button value="FOUND">招领</el-radio-button>
      </el-radio-group>
    </div>

    <div v-if="loading" class="grid">
      <div v-for="i in 4" :key="i" class="lf-panel sk-card">
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

    <!-- 空状态分两种：一条都没发过 / 筛出来是空的，提示语不一样 -->
    <div v-else-if="items.length === 0" class="lf-panel empty">
      <el-empty
        :description="
          stats.total === 0 ? '你还没有发布过物品' : '没有符合当前筛选条件的记录'
        "
      >
        <el-button v-if="stats.total === 0" type="primary" @click="router.push('/publish')">
          去发布第一条
        </el-button>
        <el-button v-else @click="(statusFilter = ''), (typeFilter = ''), pickType('')">
          清空筛选
        </el-button>
      </el-empty>
    </div>

    <div v-else class="grid">
      <ItemCard
        v-for="item in items"
        :key="item.id"
        :item="item"
        @open="(id) => router.push(`/items/${id}`)"
      />
    </div>

    <el-pagination
      v-if="!loading && total > 0"
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :total="total"
      :page-sizes="[12, 24, 48]"
      layout="prev, pager, next, sizes, total"
      @current-change="loadItems"
      @size-change="loadItems"
    />
  </div>
</template>

<style scoped>
/* ── 资料卡 ───────────────────────────────────────────────── */
.hero {
  position: relative;
  overflow: hidden;
  margin-bottom: 28px;
}

/* 顶部一条渐变带，让资料卡不至于是一块纯白 */
.hero-bg {
  height: 92px;
  background: linear-gradient(135deg, #4f46e5 0%, #7c74ee 55%, #8b7bf0 100%);
}

.hero-body {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 0 24px 20px;
  margin-top: -34px;
}

.avatar {
  width: 76px;
  height: 76px;
  flex-shrink: 0;
  border-radius: 20px;
  display: grid;
  place-items: center;
  font-size: 30px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, var(--el-color-primary), #7c74ee);
  /* 白圈把头像从渐变带上「拎」出来 */
  border: 4px solid var(--lf-surface);
  box-shadow: 0 4px 12px rgba(16, 24, 40, 0.12);
}

.who {
  flex: 1;
  min-width: 0;
  padding-top: 30px;
}

.name {
  font-size: 21px;
  font-weight: 600;
  letter-spacing: -0.2px;
}

.sub {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 12.5px;
  color: var(--lf-text-sub);
}

.sub span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.publish-btn {
  flex-shrink: 0;
  align-self: flex-end;
}

/* ── 统计条 ───────────────────────────────────────────────── */
.stats {
  display: flex;
  border-top: 1px solid var(--lf-border);
}

.stat {
  flex: 1;
  padding: 16px 0;
  border: none;
  background: none;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  transition: background 0.15s;
  font-family: inherit;
}

.stat:hover {
  background: #fafbfc;
}

.stat.active {
  background: var(--el-color-primary-light-9);
  /* 用内阴影当选中下划线，不会把布局撑高 */
  box-shadow: inset 0 -2px 0 var(--el-color-primary);
}

.stat-num {
  font-size: 20px;
  font-weight: 700;
  color: var(--lf-text);
  font-variant-numeric: tabular-nums;
}

.stat.active .stat-num {
  color: var(--el-color-primary);
}

.stat-label {
  font-size: 12.5px;
  color: var(--lf-text-sub);
}

/* ── 区块标题 ─────────────────────────────────────────────── */
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.section-title {
  font-size: 17px;
  font-weight: 600;
}

/* ── 网格 / 骨架 / 空状态 ─────────────────────────────────── */
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

.sk-card {
  overflow: hidden;
}

.empty {
  padding: 24px 0;
}

/* ── 窄屏 ─────────────────────────────────────────────────── */
@media (max-width: 640px) {
  .hero-body {
    flex-wrap: wrap;
    padding: 0 16px 16px;
  }

  .who {
    padding-top: 26px;
  }

  .publish-btn {
    width: 100%;
    align-self: stretch;
  }
}
</style>
