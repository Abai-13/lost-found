<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'

// chat = 自由问答，query = 按描述匹配招领信息
const mode = ref('chat')
const question = ref('')
const answer = ref('')
const loading = ref(false)

async function ask() {
  if (!question.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  loading.value = true
  answer.value = ''
  try {
    // 两个接口路径不同，但请求体都是 { question }
    const res = await request.post(`/ai/${mode.value}`, { question: question.value })
    answer.value = res.data.answer
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-card>
    <el-radio-group v-model="mode">
      <el-radio-button value="chat">AI 问答</el-radio-button>
      <el-radio-button value="query">AI 物品匹配</el-radio-button>
    </el-radio-group>

    <p class="tip">
      <template v-if="mode === 'query'">
        描述你丢的物品（比如「一个黑色双肩包，昨天在图书馆丢的」），AI 会帮你匹配已有的招领信息。
      </template>
      <template v-else>
        随便问，AI 帮你解答。
      </template>
    </p>

    <el-input v-model="question" type="textarea" :rows="3" placeholder="输入你的问题 / 描述..." />

    <el-button type="primary" :loading="loading" style="margin-top: 12px" @click="ask">发送</el-button>

    <div v-if="answer" class="answer">
      <div class="answer-title">回答：</div>
      <div class="answer-body">{{ answer }}</div>
    </div>
  </el-card>
</template>

<style scoped>
.tip {
  color: #888;
  font-size: 13px;
  margin: 16px 0 12px;
}
.answer {
  margin-top: 20px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 6px;
}
.answer-title {
  font-weight: bold;
  margin-bottom: 8px;
}
.answer-body {
  white-space: pre-wrap;
  line-height: 1.6;
}
</style>
