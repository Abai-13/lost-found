<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../api/request'

const router = useRouter()

// 表单字段（跟后端 ItemCreateRequest 对应）
const form = ref({
  title: '',
  type: 'LOST',
  category: '',
  location: '',
  description: '',
  contact: '',
})

const categories = ['电子产品', '证件', '衣物', '书籍', '其他']

// 用户选的图片文件（还没上传，等点「发布」时一起发）
const imageFile = ref(null)

// el-upload 选中文件时触发，file.raw 才是真正的 File 对象
function onFileChange(file) {
  imageFile.value = file.raw
}

function onFileRemove() {
  imageFile.value = null
}

async function publish() {
  // 构造 multipart/form-data
  const fd = new FormData()
  // 「data」部分：把表单对象转成 JSON 字符串（后端 @RequestPart("data") 按 JSON 解析）
  fd.append('data', new Blob([JSON.stringify(form.value)], { type: 'application/json' }))
  // 「image」部分：可选的图片文件（后端 @RequestPart("image") 接收）
  if (imageFile.value) {
    fd.append('image', imageFile.value)
  }
  await request.post('/item', fd)
  ElMessage.success('发布成功')
  router.push('/items')
}
</script>

<template>
  <el-card>
    <el-form label-width="80px">
      <el-form-item label="标题" required>
        <el-input v-model="form.title" placeholder="比如：黑色钱包" />
      </el-form-item>

      <el-form-item label="类型" required>
        <el-radio-group v-model="form.type">
          <el-radio value="LOST">寻物（丢了找东西）</el-radio>
          <el-radio value="FOUND">招领（捡到找失主）</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="分类" required>
        <el-select v-model="form.category" placeholder="选择分类" style="width: 200px">
          <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
        </el-select>
      </el-form-item>

      <el-form-item label="地点">
        <el-input v-model="form.location" placeholder="丢失或拾获地点" />
      </el-form-item>

      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="4"
                  placeholder="物品特征、颜色、时间等" />
      </el-form-item>

      <el-form-item label="联系方式">
        <el-input v-model="form.contact" placeholder="电话 / 微信 / QQ" />
      </el-form-item>

      <el-form-item label="图片">
        <el-upload :auto-upload="false" :limit="1" accept="image/*"
                   :on-change="onFileChange" :on-remove="onFileRemove">
          <el-button>选择图片</el-button>
        </el-upload>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" @click="publish">发布</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>
