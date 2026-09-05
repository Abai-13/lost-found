<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../api/request'
import { auth } from '../api/auth'

const route = useRoute()

const item = ref(null)
const publisherName = ref('')

// 加载详情：后端返回 res.data = { item, publisherName }
async function load() {
  const id = route.params.id
  const res = await request.get(`/item/${id}`)
  item.value = res.data.item
  publisherName.value = res.data.publisherName
}

// 判断当前登录用户是不是发布者本人（userId 从 localStorage 拿，是字符串，要转数字比）
function isOwner() {
  return item.value && Number(auth.userId) === item.value.userId
}

// 标记为已认领（只有发布者本人能点）
async function markClaimed() {
  try {
    await ElMessageBox.confirm('确定标记为已认领吗？', '提示', { type: 'warning' })
  } catch (e) {
    return // 用户点了取消
  }
  await request.put(`/item/${item.value.id}/status`, null, { params: { status: 'CLAIMED' } })
  ElMessage.success('操作成功')
  load()
}

onMounted(load)
</script>

<template>
  <div v-if="item">
    <el-card>
      <div class="title">{{ item.title }}</div>

      <el-tag :type="item.type === 'LOST' ? 'danger' : 'success'" style="margin-right: 8px">
        {{ item.type === 'LOST' ? '寻物' : '招领' }}
      </el-tag>
      <el-tag :type="item.status === 'UNCLAIMED' ? 'warning' : 'info'">
        {{ item.status === 'UNCLAIMED' ? '未认领' : '已认领' }}
      </el-tag>

      <el-descriptions :column="2" border style="margin-top: 16px">
        <el-descriptions-item label="分类">{{ item.category }}</el-descriptions-item>
        <el-descriptions-item label="地点">{{ item.location || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发布者">{{ publisherName }}</el-descriptions-item>
        <el-descriptions-item label="联系方式">{{ item.contact || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ item.createdAt?.replace('T', ' ') }}</el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ item.description || '-' }}</el-descriptions-item>
      </el-descriptions>

      <img v-if="item.imageUrl" :src="item.imageUrl" class="image" />

      <div v-if="isOwner() && item.status === 'UNCLAIMED'" style="margin-top: 16px">
        <el-button type="success" @click="markClaimed">标记为已认领</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.title {
  font-size: 22px;
  font-weight: bold;
  margin-bottom: 12px;
}
.image {
  max-width: 400px;
  margin-top: 16px;
  border-radius: 6px;
}
</style>
