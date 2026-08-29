<template>
  <div class="page">
    <article v-if="detail" class="briefing" aria-labelledby="news-title">
      <header class="briefing-head">
        <div class="issue-line">
          <span class="issue-type">{{ detail.category || '健康新闻' }}</span>
          <span class="source-mark"><i></i>{{ detail.sourceName || '公开权威来源' }}</span>
          <time v-if="detail.publishedOn">{{ detail.publishedOn }}</time>
        </div>
        <h1 id="news-title">{{ detail.title }}</h1>
        <div v-if="detail.summary" class="brief-summary">
          <span>内容摘要</span>
          <p>{{ detail.summary }}</p>
        </div>
      </header>

      <NewsPhoto
        v-if="detail.image"
        :id="detail.id"
        :alt="detail.title"
        class="lead-photo"
      />

      <div class="reading-grid">
        <div class="body">
          <p v-for="(para, i) in paragraphs" :key="i">{{ para }}</p>
          <p v-if="!paragraphs.length" class="body-empty">来源暂未提供更多正文内容，请查看原始发布页面。</p>
        </div>

        <aside class="source-rail" aria-label="信息来源">
          <span class="rail-kicker">SOURCE CHECK</span>
          <h2>来源信息</h2>
          <dl>
            <div><dt>发布机构</dt><dd>{{ detail.sourceName || '公开权威来源' }}</dd></div>
            <div v-if="detail.publishedOn"><dt>发布日期</dt><dd>{{ detail.publishedOn }}</dd></div>
            <div><dt>内容分类</dt><dd>{{ detail.category || '健康新闻' }}</dd></div>
          </dl>
          <a
            v-if="detail.sourceUrl"
            class="source-link"
            :href="detail.sourceUrl"
            target="_blank"
            rel="noopener noreferrer"
          >
            核验来源原文<span v-html="ICONS.external"></span>
          </a>
          <p>内容保留原发布机构与日期，便于核对出处。</p>
        </aside>
      </div>
    </article>

    <section v-else-if="loaded" class="missing">
      <span class="missing-code">404 / BRIEF</span>
      <h1>这条健康简报已不可用</h1>
      <p>内容可能已经过期或被来源机构撤回。</p>
      <button class="btn btn-primary" type="button" @click="router.push('/home')">返回首页</button>
    </section>

    <section v-else class="loading-state" aria-label="正在加载">
      <div class="skeleton title-skeleton"></div>
      <div class="skeleton summary-skeleton"></div>
      <div class="skeleton photo-skeleton"></div>
      <div class="skeleton copy-skeleton"></div>
    </section>

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

</script>

<style scoped>
.page {
  width: 100%;
  max-width: 1120px;
  margin: 0 auto;
  display: grid;
  gap: var(--space-4);
}

.briefing {
  overflow: hidden;
  border: 1px solid var(--edge);
  border-top: 3px solid var(--ink);
  border-radius: 0 0 var(--r-shell) var(--r-shell);
  background: var(--card);
  box-shadow: var(--shadow-2);
}

.briefing-head {
  max-width: 870px;
  padding: var(--space-6) var(--space-7);
}

.issue-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  color: var(--ink-faint);
  font-size: 11.5px;
}

.issue-type {
  padding: 3px var(--space-2);
  border-radius: var(--r-chip);
  background: var(--accent-wash);
  color: var(--accent);
  font-weight: 650;
}

.source-mark {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--ink-mute);
  font-weight: 600;
}

.source-mark i {
  width: 6px;
  height: 6px;
  border-radius: var(--r-pill);
  background: var(--flag-normal);
}

.issue-line time::before {
  content: '/';
  margin-right: var(--space-3);
  color: var(--edge-strong);
}

h1 {
  max-width: 21em;
  margin-top: var(--space-4);
  font-size: clamp(28px, 4vw, 40px);
  font-weight: 650;
  line-height: 1.32;
  text-wrap: balance;
}

.brief-summary {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: var(--space-4);
  margin-top: var(--space-5);
  padding-top: var(--space-4);
  border-top: 1px solid var(--edge);
}

.brief-summary > span {
  padding-top: 3px;
  color: var(--accent);
  font: 600 10.5px/1.5 var(--font-mono);
}

.brief-summary p {
  max-width: 44em;
  color: var(--ink-soft);
  font-size: 15px;
  line-height: 1.8;
}

.lead-photo {
  width: 100%;
  aspect-ratio: 16 / 7;
  border-block: 1px solid var(--edge);
  border-radius: 0;
}

.reading-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  align-items: start;
}

.body {
  max-width: 760px;
  padding: var(--space-6) var(--space-7) var(--space-7);
}

.body p {
  margin: 0 0 1.3em;
  color: var(--ink);
  font-size: 16px;
  line-height: 1.95;
}

.body p:first-child {
  font-size: 17px;
}

.body-empty {
  color: var(--ink-mute) !important;
}

.source-rail {
  position: sticky;
  top: var(--main-pad);
  min-width: 0;
  margin: var(--space-6) var(--space-5) var(--space-6) 0;
  padding: var(--space-4) 0 0 var(--space-5);
  border-top: 2px solid var(--accent);
  border-left: 1px solid var(--edge);
}

.rail-kicker {
  color: var(--accent);
  font: 600 9.5px/1.4 var(--font-mono);
}

.source-rail h2 {
  margin-top: var(--space-1);
  font-size: 16px;
}

.source-rail dl {
  display: grid;
  gap: 0;
  margin: var(--space-4) 0 0;
}

.source-rail dl > div {
  padding: var(--space-3) 0;
  border-top: 1px solid var(--edge);
}

.source-rail dt {
  color: var(--ink-faint);
  font-size: 10.5px;
}

.source-rail dd {
  margin: 3px 0 0;
  overflow-wrap: anywhere;
  color: var(--ink-soft);
  font-size: 12.5px;
  font-weight: 550;
}

.source-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin-top: var(--space-4);
  padding: var(--space-3);
  border: 1px solid var(--accent-line);
  border-radius: var(--r-control);
  background: var(--accent-wash);
  color: var(--accent);
  font-size: 12.5px;
  font-weight: 650;
}

.source-link :deep(svg) {
  width: 14px;
  height: 14px;
}

.source-rail > p {
  margin-top: var(--space-3);
  color: var(--ink-faint);
  font-size: 11px;
  line-height: 1.65;
}

.missing,
.loading-state {
  min-height: 420px;
  padding: var(--space-7);
  border: 1px solid var(--edge);
  border-top: 3px solid var(--ink);
  background: var(--card);
}

.missing {
  display: grid;
  justify-items: start;
  align-content: center;
}

.missing-code {
  color: var(--accent);
  font: 600 11px/1.4 var(--font-mono);
}

.missing h1 {
  margin-top: var(--space-3);
}

.missing p {
  margin: var(--space-3) 0 var(--space-5);
  color: var(--ink-mute);
}

.title-skeleton {
  width: min(68%, 640px);
  height: 48px;
}

.summary-skeleton {
  width: min(82%, 760px);
  height: 70px;
  margin-top: var(--space-5);
}

.photo-skeleton {
  height: 260px;
  margin-top: var(--space-6);
}

.copy-skeleton {
  width: min(70%, 700px);
  height: 160px;
  margin-top: var(--space-6);
}

@media (hover: hover) and (pointer: fine) {
  .source-link:hover {
    border-color: var(--accent);
    background: var(--card);
  }
}

@media (max-width: 800px) {
  .briefing-head,
  .body {
    padding-inline: var(--space-5);
  }

  .reading-grid {
    grid-template-columns: 1fr;
  }

  .source-rail {
    position: static;
    order: -1;
    margin: var(--space-5) var(--space-5) 0;
    padding: var(--space-4) 0;
    border-left: 0;
    border-bottom: 1px solid var(--edge);
  }

  .source-rail dl {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .source-rail dl > div {
    padding-right: var(--space-3);
  }

  .source-link {
    width: max-content;
  }
}

@media (max-width: 520px) {
  .briefing-head {
    padding: var(--space-5) var(--space-4);
  }

  h1 {
    font-size: 27px;
  }

  .brief-summary {
    grid-template-columns: 1fr;
    gap: var(--space-2);
  }

  .lead-photo {
    aspect-ratio: 4 / 3;
  }

  .source-rail {
    margin-inline: var(--space-4);
  }

  .source-rail dl {
    grid-template-columns: 1fr;
  }

  .body {
    padding: var(--space-5) var(--space-4);
  }

  .body p,
  .body p:first-child {
    font-size: 15px;
    line-height: 1.9;
  }

  .missing,
  .loading-state {
    min-height: 360px;
    padding: var(--space-5);
  }

  .title-skeleton,
  .summary-skeleton,
  .copy-skeleton {
    width: 100%;
  }
}
</style>
