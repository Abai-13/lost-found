<script setup>
import { relativeTime, categoryStyle } from '../utils/format'

/**
 * 物品卡片 —— 列表页和个人中心的「我的发布」共用。
 *
 * 抽成组件的理由：这两个页面要显示的是同一种东西。
 * 复制一份 CSS 的代价不是那 100 行，而是两处会各自演进 ——
 * 哪天改了卡片圆角或者状态圆点的颜色，只改一边，两个页面就长得不一样了。
 *
 * 只 emit 不自己跳转：卡片「点了之后干什么」是使用方的事，
 * 组件不该替它决定（比如个人中心以后可能要弹窗而不是跳详情页）。
 */
defineProps({
  item: { type: Object, required: true },
})

defineEmits(['open'])
</script>

<template>
  <div class="lf-panel card" @click="$emit('open', item.id)">
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
</template>

<style scoped>
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
</style>
