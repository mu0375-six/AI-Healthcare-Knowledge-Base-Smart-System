<template>
  <div class="page home">
    <section class="hero">
      <img class="hero-img" src="/art/hero.svg" alt="" />
      <div class="hero-veil">
        <p class="hello">{{ greeting() }}，{{ user.nickname }}</p>
        <h1>今天，想先弄清哪一件事？</h1>
        <p class="lead">问症状、查用药、给家人建档，或把体检报告交给系统逐项读一遍。</p>
        <form class="ask" @submit.prevent="goAsk">
          <input v-model="q" placeholder="例如：嗓子痛三天了，要不要去医院？" />
          <button class="copper-btn" type="submit">去问诊</button>
        </form>
        <div class="chips">
          <button v-for="s in suggests" :key="s" type="button" @click="ask(s)">{{ s }}</button>
        </div>
      </div>
    </section>

    <section class="family">
      <div class="sec-head">
        <h3>健康档案</h3>
        <button class="copper-btn" type="button" @click="router.push('/health?new=1')">新建档案</button>
      </div>
      <div class="members">
        <button v-for="p in overview.profiles" :key="p.id" class="member card" type="button" @click="router.push('/health?id=' + p.id)">
          <span class="av">{{ initial(p.displayName) }}</span>
          <span>
            <b>{{ p.displayName || '未命名' }}</b>
            <i>{{ p.relation || '档案' }}{{ p.age ? ' · ' + p.age + '岁' : '' }}</i>
          </span>
        </button>
        <button class="member add card" type="button" @click="router.push('/health?new=1')">
          <span class="av plus">+</span>
          <span>
            <b>新建档案</b>
            <i>给爸爸、妈妈或孩子</i>
          </span>
        </button>
      </div>
    </section>

    <section v-if="statItems.length" class="stats">
      <button v-for="s in statItems" :key="s.label" class="stat card" type="button" @click="router.push(s.to)">
        <em>{{ s.value }}</em>
        <span>{{ s.label }}</span>
      </button>
    </section>

    <div class="cols">
      <section class="sheet sheet-pad">
        <div class="sec-head tight">
          <h3>最近问答</h3>
          <router-link to="/chat">全部</router-link>
        </div>
        <div v-if="!overview.recentSessions.length" class="quiet">还没有会话。从上面那一行问题开始。</div>
        <button
          v-for="s in overview.recentSessions"
          :key="s.id"
          class="row"
          type="button"
          @click="router.push('/chat?sid=' + s.id)"
        >
          <b>{{ s.title }}</b>
          <time>{{ s.updatedAt }}</time>
        </button>
      </section>
      <section class="sheet sheet-pad">
        <div class="sec-head tight">
          <h3>最近报告</h3>
          <router-link to="/reports">上传</router-link>
        </div>
        <div v-if="!overview.recentReports.length" class="quiet">还没有解读过报告。</div>
        <button
          v-for="r in overview.recentReports"
          :key="r.id"
          class="row"
          type="button"
          @click="router.push('/reports/' + r.id)"
        >
          <span>
            <b>{{ r.filename }}</b>
            <i v-if="r.hint">{{ r.hint }}</i>
          </span>
          <time>{{ r.createdAt }}</time>
        </button>
      </section>
    </div>

    <div class="disclaimer">以上内容仅供健康科普参考，不能替代执业医师的面诊、检查与处方。如有不适请及时就医。</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getOverview } from '@/api/home'
import type { HomeOverview } from '@/api/types'
import { useUserStore } from '@/stores/user'
import { greeting, initial } from '@/utils/format'

const router = useRouter()
const user = useUserStore()
const q = ref('')
const overview = reactive<HomeOverview>({
  profileCount: 0,
  metricCount: 0,
  reportCount: 0,
  favoriteCount: 0,
  sessionCount: 0,
  profiles: [],
  recentSessions: [],
  recentReports: [],
})

const suggests = ['嗓子痛三天了怎么办', '二甲双胍有哪些注意事项', '头晕要看哪个科']

// 只显示有内容的项：新用户不该一进来就看到一排 0。
// 每项都可点进对应页面 —— 不能点的数字是装饰，能跳转的才是快捷入口。
const statItems = computed(() =>
  [
    { label: '份档案', value: overview.profileCount, to: '/health' },
    { label: '条指标', value: overview.metricCount, to: '/health' },
    { label: '次问答', value: overview.sessionCount, to: '/chat' },
    { label: '份报告', value: overview.reportCount, to: '/reports' },
    { label: '条收藏', value: overview.favoriteCount, to: '/favorites' },
  ].filter((s) => Number(s.value) > 0),
)

onMounted(async () => {
  const res = await getOverview()
  Object.assign(overview, res.data)
})

function ask(text: string) {
  q.value = text
  goAsk()
}

function goAsk() {
  const text = q.value.trim()
  router.push(text ? { path: '/chat', query: { q: text } } : '/chat')
}
</script>

<style scoped>
.hero {
  position: relative;
  border-radius: var(--r-panel);
  overflow: hidden;
  min-height: 320px;
  margin-bottom: 28px;
  box-shadow: var(--shadow);
}
.hero-img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.hero-veil {
  position: relative;
  padding: 36px 36px 28px;
  min-height: 320px;
  background: linear-gradient(90deg, rgba(16, 26, 22, 0.72) 0%, rgba(16, 26, 22, 0.28) 70%, rgba(16, 26, 22, 0.08));
  color: #f6f0e6;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}
.hello {
  margin: 0 0 8px;
  letter-spacing: 0.08em;
  color: #e0b08a;
  font-size: 13px;
}
h1 {
  margin: 0 0 10px;
  font-size: 36px;
  letter-spacing: 0.04em;
  max-width: 16ch;
}
.lead {
  margin: 0 0 18px;
  max-width: 460px;
  color: rgba(246, 240, 230, 0.82);
  line-height: 1.7;
}
.ask {
  display: flex;
  gap: 8px;
  max-width: 620px;
  background: rgba(250, 246, 239, 0.94);
  border-radius: 999px;
  padding: 6px 6px 6px 18px;
}
.ask input {
  flex: 1;
  border: 0;
  background: transparent;
  color: var(--ink);
  outline: none;
  font-size: 14px;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
.chips button {
  border: 1px solid rgba(246, 240, 230, 0.28);
  background: rgba(16, 26, 22, 0.18);
  color: #f6f0e6;
  border-radius: 999px;
  padding: 5px 12px;
  cursor: pointer;
  font-size: 12px;
}
.family,
.stats,
.cols {
  margin-bottom: 22px;
}
.sec-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 12px;
}
.sec-head.tight {
  margin-bottom: 8px;
}
.sec-head h3 {
  margin: 0;
  font-size: 22px;
}
.members {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 4px;
}
.member {
  min-width: 188px;
  display: flex;
  gap: 10px;
  align-items: center;
  text-align: left;
  padding: 12px 14px;
  cursor: pointer;
}
.member.add {
  border-style: dashed;
}
.member b,
.row b {
  display: block;
  font-weight: 600;
}
.member i,
.row i {
  display: block;
  font-style: normal;
  color: var(--ink-3);
  font-size: 12px;
  margin-top: 2px;
}
.av {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: #ead8cc;
  color: var(--copper-deep);
  display: grid;
  place-items: center;
  font-family: var(--font-serif);
  flex-shrink: 0;
}
.av.plus {
  background: var(--paper-deep);
  color: var(--ink-2);
  font-size: 22px;
}
.stats {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.stat {
  /* 项数随数据变化，限个上限，避免只剩一项时拉满整行 */
  flex: 1 1 140px;
  max-width: 240px;
  text-align: left;
  padding: 16px 14px;
  cursor: pointer;
}
.stat:hover {
  border-color: #d7b09a;
}
.stat em {
  font-family: var(--font-serif);
  font-style: normal;
  font-size: 28px;
  display: block;
}
.stat span {
  color: var(--ink-3);
  font-size: 12px;
}
.cols {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.row {
  width: 100%;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  text-align: left;
  background: none;
  border: 0;
  border-top: 1px solid var(--line);
  padding: 12px 0;
  cursor: pointer;
}
.row time {
  color: var(--ink-3);
  font-size: 12px;
  flex-shrink: 0;
}
.quiet {
  color: var(--ink-3);
  font-size: 13px;
  padding: 12px 0;
}
@media (max-width: 980px) {
  .cols {
    grid-template-columns: 1fr;
  }
  h1 {
    font-size: 28px;
  }
  .hero-veil {
    padding: 24px 20px;
  }
}
</style>
