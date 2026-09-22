<template>
  <div class="login-page">
    <el-card class="login-card">
      <div class="login-title">🪐 成长星球 · 运营后台</div>
      <div class="login-sub">仅限授权运营人员使用，操作将记入审计日志</div>
      <el-form :model="form" @keyup.enter="onSubmit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="账号" size="large" autofocus />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password />
        </el-form-item>
        <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="onSubmit">
          登 录
        </el-button>
        <div v-if="error" class="login-error">{{ error }}</div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { doLogin } from '../stores/auth'

const router = useRouter()
const form = reactive({ username: '', password: '' })
const loading = ref(false)
const error = ref('')

async function onSubmit() {
  if (!form.username || !form.password) {
    error.value = '请输入账号与密码'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await doLogin(form.username.trim(), form.password)
    router.push('/workbench')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #1f2d3d 0%, #2b4a6f 100%);
}
.login-card { width: 380px; padding: 8px 12px; }
.login-title { text-align: center; font-size: 20px; font-weight: 700; margin-bottom: 6px; }
.login-sub { text-align: center; font-size: 12px; color: #909399; margin-bottom: 18px; }
.login-error { color: #f56c6c; font-size: 13px; margin-top: 10px; text-align: center; }
</style>
