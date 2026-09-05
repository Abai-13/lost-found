<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../api/request'

const router = useRouter()

const items = ref([])
const total = ref(0)

// 查询条件（分页 + 筛选 + 排序）
const query = ref({
  page: 1,
  size: 10,
  type: '',        // LOST / FOUND
  status:'',      // 未认领 / 已认领
  category: '',    // 电子产品/证件/衣物/书籍/其他
  keyword: '',     // 标题关键词
  upordown: 'DESC', // 排序：DESC 最新在前 / ASC 最旧在前
})

// 分类下拉的选项
const categories = ['电子产品', '证件', '衣物', '书籍', '其他']

// 调后端拿列表数据
// 后端返回 res.data = { records, total, page, size }
async function load() {
  const res = await request.get('/item', { params: query.value })
  items.value = res.data.records
  total.value = res.data.total
}

// 点搜索：重置到第 1 页再查
function search() {
  query.value.page = 1
  load()
}

function goDetail(id) {
  router.push(`/items/${id}`)
}

onMounted(load)
</script>

<template>
  <div>
    <!-- 筛选区：控件都 v-model 绑定 query 里对应的字段 -->
    <div class="filters">
      <el-select v-model="query.type" placeholder="类型" clearable style="width: 120px">
        <el-option label="寻物" value="LOST" />
        <el-option label="招领" value="FOUND" />
      </el-select>

      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
        <el-option label="未认领" value="UNCLAIMED" />
        <el-option label="已认领" value="CLAIMED" />
      </el-select>

      <el-select v-model="query.category" placeholder="分类" clearable style="width: 120px">
        <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
      </el-select>

      <el-input v-model="query.keyword" placeholder="搜索标题关键词" clearable
                style="width: 200px" @keyup.enter="search" />

      <el-select v-model="query.upordown" style="width: 130px">
        <el-option label="最新在前" value="DESC" />
        <el-option label="最旧在前" value="ASC" />
      </el-select>

      <el-button type="primary" @click="search">搜索</el-button>
    </div>

    <el-table :data="items" @row-click="(row) => goDetail(row.id)">
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="type" label="类型" width="90">
        <template #default="{ row }">{{ row.type === 'LOST' ? '寻物' : '招领' }}</template>
      </el-table-column>
      <el-table-column prop="category" label="分类" width="110" />
      <el-table-column prop="location" label="地点" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">{{ row.status === 'UNCLAIMED' ? '未认领' : '已认领' }}</template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :total="total"
      layout="prev, pager, next, sizes, total"
      @current-change="load"
      @size-change="load"
      style="margin-top: 16px"
    />
  </div>
</template>

<style scoped>
.filters {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
</style>
