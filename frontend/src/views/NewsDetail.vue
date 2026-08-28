<template>
  <div class="page">
    <article class="article" v-reveal>
      <button class="btn btn-quiet btn-sm back" type="button" @click="goBack">
        <span v-html="ICONS.chevron"></span>返回首页
      </button>

      <template v-if="detail">
        <div class="meta">
          <span class="chip">{{ detail.category || '健康新闻' }}</span>
          <span>{{ detail.sourceName }}</span>
          <i v-if="detail.publishedOn">·</i>
          <time>{{ detail.publishedOn }}</time>
        </div>

        <h1>{{ detail.title }}</h1>
        <p v-if="detail.summary" class="lede">{{ detail.summary }}</p>

        <NewsPhoto v-if="detail.image" :id="detail.id" :alt="detail.title" class="hero-img" />

        <div class="body">
          <p v-for="(para, i) in paragraphs" :key="i">{{ para }}</p>
        </div>

        <footer class="src">
          <a
            v-if="detail.sourceUrl"
            :href="detail.sourceUrl"
            target="_blank"
            rel="noopener noreferrer"
          >
            查看来源原文<em>{{ detail.sourceName }}</em>
            <span v-html="ICONS.external"></span>
          </a>
        </footer>
      </template>

      <template v-else-if="loaded">
        <h1>这条新闻不见了</h1>
        <p class="lede">它可能已过期或被清理。回首页看看最新的吧。</p>
      </template>

      <template v-else>
        <div class="skeleton" style="height: 34px; width: 70%"></div>
        <div class="skeleton" style="height: 18px; width: 40%; margin-top: 14px"></div>
        <div class="skeleton" style="height: 220px; margin-top: 22px; border-radius: var(--r-card)"></div>
      </template>
    </article>

    <MedicalDisclaimer />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getNews, type NewsDetail } from '@/api/news'
import { ICONS } from '@/utils/icons'
import NewsPhoto from '@/components/NewsPhoto.vue'
import MedicalDisclaimer from '@/components/MedicalDisclaimer.vue'

const route = useRoute()
const router = useRouter()
const detail = ref<NewsDetail | null>(null)
const loaded = ref(false)

/** 正文以空行分段落 —— 后端存的就是这个约定，谁都不用碰 HTML。 */
const paragraphs = computed(() =>
  (detail.value?.content || '')
    .split(/\n\s*\n/)
    .map((s) => s.trim())
    .filter(Boolean),
)

onMounted(async () => {
  try {
    detail.value = (await getNews(route.params.id as string)).data
  } catch {
    detail.value = null
  } finally {
    loaded.value = true
  }
})

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/home')
  }
}
</script>

<style scoped>
.page {
  padding-top: 4px;
}

/* 阅读页收窄到一行 30 个字上下 —— 这是中文长文的舒适行宽 */
.article {
  max-width: 760px;
  margin: 0 auto;
}

.back {
  margin-bottom: 18px;
}

.meta {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 12.5px;
  color: var(--ink-faint);
}

.meta .chip {
  padding: 2px 10px;
  border-radius: var(--r-chip);
  background: var(--accent-wash);
  color: var(--accent);
  font-weight: 600;
  font-size: 11.5px;
}

.meta i {
  font-style: normal;
}

h1 {
  margin: 14px 0 0;
  font-size: clamp(22px, 3.2vw, 30px);
  line-height: 1.42;
  letter-spacing: -0.015em;
  text-wrap: balance;
}

.lede {
  margin-top: 14px;
  padding-left: 14px;
  border-left: 3px solid var(--accent-line);
  color: var(--ink-soft);
  font-size: 15px;
  line-height: 1.8;
}

.hero-img {
  margin-top: 22px;
  border-radius: var(--r-card);
  border: 1px solid var(--edge);
}

.body {
  margin-top: 24px;
}

.body p {
  font-size: 16px;
  line-height: 1.92;
  color: var(--ink);
  margin-bottom: 1.15em;
}

.src {
  margin-top: 26px;
  padding-top: 16px;
  border-top: 1px solid var(--edge);
}

.src a {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13.5px;
  color: var(--accent);
}

.src a em {
  font-style: normal;
  color: var(--ink-faint);
  font-size: 12.5px;
}

.src a :deep(svg) {
  width: 14px;
  height: 14px;
}
</style>
