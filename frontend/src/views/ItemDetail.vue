<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../api/request'
import { auth } from '../api/auth'
import { relativeTime, fullTime, categoryStyle } from '../utils/format'

const route = useRoute()
const router = useRouter()

const item = ref(null)
const publisherName = ref('')
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    const id = route.params.id
    const res = await request.get(`/item/${id}`)
    item.value = res.data.item
    publisherName.value = res.data.publisherName
  } finally {
    loading.value = false
  }
}

// 判断当前登录用户是不是发布者本人（userId 从 localStorage 拿，是字符串，要转数字比）
function isOwner() {
  return item.value && Number(auth.userId) === item.value.userId
}

async function markClaimed() {
  try {
    await ElMessageBox.confirm(
      '标记后这条信息会显示为「已认领」，其他失主不会再联系你。确定吗？',
      '确认标记为已认领',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '再想想' }
    )
  } catch {
    return // 用户点了取消
  }
  await request.put(`/item/${item.value.id}/status`, null, { params: { status: 'CLAIMED' } })
  ElMessage.success('操作成功')
  load()
}

// 联系方式大多是手机号/微信号，让用户手动选中复制很烦，给个一键复制
async function copyContact() {
  const text = item.value?.contact
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('联系方式已复制')
  } catch {
    // 非 HTTPS 或浏览器不支持时 clipboard 会抛错，不能让页面卡住
    ElMessage.warning('复制失败，请手动选择复制')
  }
}

onMounted(load)
</script>

<template>
  <div class="lf-page">
    <el-button link class="back" @click="router.back()">
      <el-icon><ArrowLeft /></el-icon>返回
    </el-button>

    <!-- 加载态 -->
    <div v-if="loading" class="lf-panel loading-box">
      <el-skeleton :rows="6" animated />
    </div>

    <template v-else-if="item">
      <div class="lf-panel detail">
        <!-- 左：图片 / 分类占位 -->
        <div class="media">
          <img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.title" />
          <div v-else class="media-ph" :style="{ background: categoryStyle(item.category).bg }">
            <span class="media-emoji">{{ categoryStyle(item.category).emoji }}</span>
            <span class="media-hint">发布者未上传图片</span>
          </div>
        </div>

        <!-- 右：信息 -->
        <div class="info">
          <h1 class="title">{{ item.title }}</h1>

          <div class="tags">
            <span class="pill" :class="item.type === 'LOST' ? 'lost' : 'found'">
              {{ item.type === 'LOST' ? '寻物' : '招领' }}
            </span>
            <el-tag
              :type="item.status === 'UNCLAIMED' ? 'warning' : 'info'"
              effect="light"
              round
            >
              {{ item.status === 'UNCLAIMED' ? '未认领' : '已认领' }}
            </el-tag>
            <el-tag type="info" effect="plain" round>{{ item.category || '未分类' }}</el-tag>
          </div>

          <p v-if="item.description" class="desc">{{ item.description }}</p>

          <dl class="facts">
            <div class="fact">
              <dt><el-icon><LocationInformation /></el-icon>地点</dt>
              <dd>{{ item.location || '未填写' }}</dd>
            </div>
            <div class="fact">
              <dt><el-icon><User /></el-icon>发布者</dt>
              <dd>{{ publisherName || '匿名' }}</dd>
            </div>
            <div class="fact">
              <dt><el-icon><Clock /></el-icon>发布时间</dt>
              <dd>
                {{ relativeTime(item.createdAt) }}
                <span class="sub">（{{ fullTime(item.createdAt) }}）</span>
              </dd>
            </div>
          </dl>

          <!-- 联系方式单独强调：这是整个页面用户最想拿到的信息 -->
          <div class="contact" :class="{ empty: !item.contact }">
            <div class="contact-label">联系方式</div>
            <div class="contact-value">
              <span v-if="item.contact">{{ item.contact }}</span>
              <span v-else class="muted">发布者未留下联系方式</span>
              <el-button v-if="item.contact" size="small" type="primary" plain @click="copyContact">
                <el-icon><CopyDocument /></el-icon>复制
              </el-button>
            </div>
          </div>

          <div v-if="isOwner() && item.status === 'UNCLAIMED'" class="owner-actions">
            <el-button type="success" @click="markClaimed">
              <el-icon><Select /></el-icon>标记为已认领
            </el-button>
            <span class="hint">只有发布者本人能看到这个按钮</span>
          </div>
        </div>
      </div>
    </template>

    <el-empty v-else description="物品不存在或已被删除">
      <el-button type="primary" @click="router.push('/items')">返回列表</el-button>
    </el-empty>
  </div>
</template>

<style scoped>
.back {
  margin-bottom: 12px;
  color: var(--lf-text-sub);
}

.loading-box {
  padding: 24px;
}

.detail {
  display: grid;
  grid-template-columns: minmax(0, 420px) minmax(0, 1fr);
  gap: 28px;
  padding: 24px;
}

/* ── 左：媒体区 ───────────────────────────────────────────── */
.media img {
  width: 100%;
  border-radius: var(--lf-radius-sm);
  display: block;
}

.media-ph {
  width: 100%;
  aspect-ratio: 4 / 3;
  border-radius: var(--lf-radius-sm);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
}

.media-emoji {
  font-size: 80px;
  line-height: 1;
  filter: drop-shadow(0 3px 10px rgba(16, 24, 40, 0.14));
}

.media-hint {
  font-size: 13px;
  color: var(--lf-text-muted);
}

/* ── 右：信息区 ───────────────────────────────────────────── */
.title {
  font-size: 24px;
  font-weight: 600;
  line-height: 1.35;
  letter-spacing: -0.3px;
}

.tags {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.pill {
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  color: #fff;
}

.pill.lost {
  background: var(--lf-danger);
}

.pill.found {
  background: var(--lf-success);
}

.desc {
  margin-top: 16px;
  font-size: 14px;
  line-height: 1.75;
  color: var(--lf-text-sub);
  white-space: pre-wrap;
}

/* 用 dl/dt/dd 而不是 el-descriptions：这里是「标签在上、值在下」的竖排，
   语义上就是描述列表，用原生标签比套组件更轻 */
.facts {
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.fact {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.fact dt {
  display: flex;
  align-items: center;
  gap: 5px;
  width: 88px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--lf-text-muted);
}

.fact dd {
  font-size: 14px;
}

.sub {
  color: var(--lf-text-muted);
  font-size: 12.5px;
}

/* ── 联系方式 ─────────────────────────────────────────────── */
.contact {
  margin-top: 22px;
  padding: 14px 16px;
  border-radius: var(--lf-radius-sm);
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
}

.contact.empty {
  background: #fafafa;
  border-color: var(--lf-border);
}

.contact-label {
  font-size: 12px;
  color: var(--lf-text-sub);
}

.contact-value {
  margin-top: 6px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

.muted {
  font-size: 14px;
  font-weight: 400;
  color: var(--lf-text-muted);
}

/* ── 发布者操作 ───────────────────────────────────────────── */
.owner-actions {
  margin-top: 20px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.hint {
  font-size: 12px;
  color: var(--lf-text-muted);
}

/* ── 窄屏：变上下布局 ─────────────────────────────────────── */
@media (max-width: 768px) {
  .detail {
    grid-template-columns: 1fr;
    gap: 20px;
    padding: 16px;
  }

  .title {
    font-size: 20px;
  }
}
</style>
