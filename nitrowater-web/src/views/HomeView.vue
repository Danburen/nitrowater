<script setup lang="ts">
import { computed } from 'vue'
import { useAuth } from '../auth/useAuth'
import { decodeJwtPayload, oidcSettings } from '../auth/oidc'

const { user, loading, login, logout, renew, refresh } = useAuth()

const claims = computed(() => decodeJwtPayload(user.value?.access_token))

function asText(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (Array.isArray(value)) return value.map((item) => String(item)).join(' · ')
  return String(value)
}

const isLoggedIn = computed(() => !!user.value)
const uid = computed(() => asText(claims.value?.['uid'] ?? user.value?.profile.sub))
const roles = computed<string[]>(() => {
  const value = claims.value?.['roles']
  return Array.isArray(value) ? value.map((item) => String(item)) : []
})
const did = computed(() => asText(claims.value?.['did']))
const issuer = computed(() => asText(claims.value?.['iss']))
const expiresAt = computed(() => {
  const exp = user.value?.expires_at
  return exp ? new Date(exp * 1000).toLocaleString() : '—'
})
const profileJson = computed(() => JSON.stringify(user.value?.profile ?? {}, null, 2))
const avatarLetter = computed(() =>
  (asText(user.value?.profile['preferred_username']) || 'U').charAt(0).toUpperCase(),
)

const features = [
  { title: '工具箱', desc: '7 个在线开发小工具（Java→TS、ZIP、树可视化…）', tag: 'M1 · 待迁移' },
  { title: '编程演练场', desc: '提交 → 编译 → 沙箱运行 → 判题', tag: 'M2 · 待开发' },
  { title: '文档频道', desc: 'VeloChatX VitePress 文档站', tag: 'N5 · 待融合' },
]
</script>

<template>
  <el-container class="page">
    <el-header
      class="topbar"
      height="64px"
    >
      <div class="brand">
        <div class="mark">
          NW
        </div>
        <div class="brand-text">
          <strong>NitroWater</strong>
          <span class="sub">复合展示站 · SSO Portal</span>
        </div>
      </div>

      <el-menu
        class="nav"
        mode="horizontal"
        default-active="home"
        :ellipsis="false"
      >
        <el-menu-item index="home">
          首页
        </el-menu-item>
        <el-menu-item
          index="tools"
          disabled
        >
          工具箱
        </el-menu-item>
        <el-menu-item
          index="playground"
          disabled
        >
          演练场
        </el-menu-item>
        <el-menu-item
          index="docs"
          disabled
        >
          文档
        </el-menu-item>
      </el-menu>

      <div class="auth">
        <template v-if="isLoggedIn && user">
          <el-avatar
            :size="30"
            class="avatar"
          >
            {{ avatarLetter }}
          </el-avatar>
          <span class="who">{{ asText(user.profile['preferred_username'] ?? user.profile.sub) }}</span>
          <el-button @click="logout">
            登出
          </el-button>
        </template>
        <el-button
          v-else
          type="primary"
          :loading="loading"
          @click="login"
        >
          登录
        </el-button>
      </div>
    </el-header>

    <el-main class="content">
      <section class="hero">
        <h1>一次登录，通行全站</h1>
        <p class="muted">
          NitroWater 是 WaterFun 生态的复合展示站，认证由独立的 OIDC 身份中心
          <code>{{ oidcSettings.authority }}</code> 统一签发。
        </p>
      </section>

      <el-row :gutter="16">
        <el-col
          v-for="feature in features"
          :key="feature.title"
          :xs="24"
          :sm="12"
          :md="8"
        >
          <el-card
            shadow="hover"
            class="feature"
          >
            <div class="feature-head">
              <h3>{{ feature.title }}</h3>
              <el-tag
                size="small"
                type="info"
              >
                {{ feature.tag }}
              </el-tag>
            </div>
            <p class="muted">
              {{ feature.desc }}
            </p>
          </el-card>
        </el-col>
      </el-row>

      <el-card
        shadow="never"
        class="session"
      >
        <template #header>
          <div class="session-head">
            <span class="title">SSO 会话</span>
            <div
              v-if="isLoggedIn"
              class="actions"
            >
              <el-button
                size="small"
                @click="renew"
              >
                静默续期
              </el-button>
              <el-button
                size="small"
                @click="refresh"
              >
                刷新状态
              </el-button>
            </div>
          </div>
        </template>

        <el-skeleton
          v-if="loading"
          :rows="3"
          animated
        />

        <el-empty
          v-else-if="!isLoggedIn"
          description="尚未登录，点击登录跳转到身份中心"
        >
          <el-button
            type="primary"
            @click="login"
          >
            登录
          </el-button>
        </el-empty>

        <template v-else>
          <el-descriptions
            :column="2"
            border
          >
            <el-descriptions-item label="uid">
              {{ uid }}
            </el-descriptions-item>
            <el-descriptions-item label="登录名">
              {{ asText(user?.profile['preferred_username']) }}
            </el-descriptions-item>
            <el-descriptions-item label="昵称">
              {{ asText(user?.profile['name']) }}
            </el-descriptions-item>
            <el-descriptions-item label="角色">
              <el-tag
                v-for="role in roles"
                :key="role"
                size="small"
                class="role-tag"
              >
                {{ role }}
              </el-tag>
              <span v-if="!roles.length">—</span>
            </el-descriptions-item>
            <el-descriptions-item label="设备 did">
              {{ did }}
            </el-descriptions-item>
            <el-descriptions-item label="issuer">
              {{ issuer }}
            </el-descriptions-item>
            <el-descriptions-item label="scope">
              {{ asText(user?.scope) }}
            </el-descriptions-item>
            <el-descriptions-item label="过期时间">
              {{ expiresAt }}
            </el-descriptions-item>
          </el-descriptions>

          <el-collapse class="raw">
            <el-collapse-item
              title="原始 profile 声明（id_token / userinfo）"
              name="raw"
            >
              <pre class="mono">{{ profileJson }}</pre>
            </el-collapse-item>
          </el-collapse>
        </template>
      </el-card>
    </el-main>

    <el-footer
      class="footer"
      height="auto"
    >
      <span>nitrowater-web · Vue 3 + Element Plus + oidc-client-ts</span>
      <span class="mono">{{ oidcSettings.authority }}</span>
    </el-footer>
  </el-container>
</template>

<style scoped>
.page {
  min-height: 100vh;
}

.topbar {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
  position: sticky;
  top: 0;
  z-index: 10;
}

.brand {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.mark {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #2563eb, #38bdf8);
  color: #fff;
  font-weight: 800;
  font-size: 13px;
  letter-spacing: 0.5px;
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}

.brand-text .sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.nav {
  flex: 1;
  border-bottom: none !important;
}

.auth {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.avatar {
  background: linear-gradient(135deg, #2563eb, #38bdf8);
  color: #fff;
  font-weight: 600;
}

.who {
  font-weight: 600;
}

.content {
  width: min(1080px, 100%);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  padding-top: 2rem;
}

.hero h1 {
  font-size: clamp(26px, 4vw, 40px);
  margin: 0 0 0.5rem;
}

.hero code {
  background: var(--el-fill-color-light);
  padding: 0.15rem 0.4rem;
  border-radius: 6px;
  font-size: 13px;
}

.muted {
  color: var(--el-text-color-secondary);
  margin: 0;
}

.feature {
  margin-bottom: 16px;
}

.feature-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  margin-bottom: 0.4rem;
}

.feature-head h3 {
  margin: 0;
  font-size: 16px;
}

.session-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.session-head .title {
  font-weight: 600;
}

.actions {
  display: flex;
  gap: 8px;
}

.role-tag {
  margin-right: 4px;
}

.raw {
  margin-top: 12px;
}

.raw pre {
  margin: 0;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  overflow: auto;
  font-size: 12.5px;
  max-height: 320px;
}

.footer {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

@media (max-width: 720px) {
  .brand-text {
    display: none;
  }
}
</style>
