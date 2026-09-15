<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../api/request'
import ItemCard from '../components/ItemCard.vue'

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
