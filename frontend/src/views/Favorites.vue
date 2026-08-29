<template>
  <div class="page">
    <PageHeader
      kicker="个人知识库"
      title="健康知识收藏"
      desc="集中保存值得反复查看的问诊解释，按收藏时间快速回溯。"
    />

    <div class="collection-summary">
      <div>
        <span class="summary-label">已归档回答</span>
        <strong class="num">{{ total }}</strong>
        <small>条</small>
      </div>
      <p>收藏内容保留原回答结构与医学术语标注，删除后无法在此处恢复。</p>
    </div>

    <section v-if="loading && !items.length" class="knowledge-list loading-list" aria-label="正在加载收藏">
      <div v-for="n in 3" :key="n" class="loading-row">
        <div class="skeleton index-skeleton"></div>
        <div>
          <div class="skeleton meta-skeleton"></div>
          <div class="skeleton text-skeleton"></div>
          <div class="skeleton text-skeleton short"></div>
        </div>
      </div>
    </section>

    <section v-else-if="!items.length" class="empty-state">
      <span class="empty-icon" v-html="ICONS.star"></span>
      <span class="empty-kicker">SAVED ANSWERS</span>
      <h2>还没有收藏内容</h2>
      <p>在问诊回答中选择“收藏该回答”，重要解释会进入这份清单。</p>
      <router-link class="btn btn-primary" to="/chat">开始第一次问诊</router-link>
    </section>

    <section v-else class="knowledge-list" aria-label="收藏的问诊回答">
      <header class="list-head">
        <div><span class="live-mark"></span><b>全部收藏</b><small class="num">{{ total }}</small></div>
        <span>按收藏时间排序</span>
      </header>

      <article v-for="(it, index) in items" :key="it.id" class="knowledge-row">
        <div class="row-index num">{{ itemNumber(index) }}</div>
        <div class="row-main">
          <div class="row-meta">
            <span class="answer-type">问诊回答</span>
            <time>{{ formatWhen(it.createdAt) }}</time>
            <button type="button" :aria-label="`取消收藏第 ${itemNumber(index)} 条回答`" @click="remove(it.id)">
              <span v-html="ICONS.trash"></span>取消收藏
            </button>
          </div>
          <div class="prose answer-copy" v-html="renderMarkdown(it.content, terms)"></div>
        </div>
      </article>
    </section>

    <el-pagination
      v-if="total > pageSize"
      layout="prev, pager, next, total"
      :pager-count="5"
      :total="total"
      :page-size="pageSize"
      :current-page="page"
      @current-change="turn"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { deleteFavorite, listFavorites, type FavoriteItem } from '@/api/favorites'
import { renderMarkdown } from '@/utils/markdown'
import { formatWhen } from '@/utils/format'
import { useTerms } from '@/composables/useTerms'
import { ICONS } from '@/utils/icons'
import PageHeader from '@/components/PageHeader.vue'

const items = ref<FavoriteItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const loading = ref(false)
const { terms, loadTerms } = useTerms()

onMounted(async () => {
  await Promise.all([load(1), loadTerms()])
})

async function load(p: number) {
  loading.value = true
  try {
    const res = await listFavorites(p, pageSize)
    items.value = res.data?.records || []
    total.value = res.data?.total || 0
    page.value = p
  } finally {
    loading.value = false
  }
}

function turn(p: number) {
  load(p)
}

function itemNumber(index: number) {
  return String((page.value - 1) * pageSize + index + 1).padStart(2, '0')
}

async function remove(id: number) {
  await deleteFavorite(id)
  const lastOnPage = items.value.length === 1 && page.value > 1
  await load(lastOnPage ? page.value - 1 : page.value)
}
</script>

<style scoped>
.page {
  width: 100%;
  max-width: 1040px;
  display: grid;
  gap: var(--space-4);
}

.page :deep(.head) {
  margin-bottom: 0;
}

.collection-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-6);
  padding: var(--space-3) var(--space-4);
  border-block: 1px solid var(--edge);
}

.collection-summary > div {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  flex-shrink: 0;
}

.summary-label {
  color: var(--ink-mute);
  font-size: 12px;
}

.collection-summary strong {
  color: var(--ink);
  font-size: 22px;
}

.collection-summary small {
  color: var(--ink-faint);
  font-size: 11px;
}

.collection-summary p {
  color: var(--ink-faint);
  font-size: 11.5px;
  text-align: right;
}

.knowledge-list {
  overflow: hidden;
  border: 1px solid var(--edge);
  border-top: 3px solid var(--ink);
  border-radius: 0 0 var(--r-shell) var(--r-shell);
  background: var(--card);
  box-shadow: var(--shadow-1);
}

.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-5);
  border-bottom: 1px solid var(--edge-strong);
  background: var(--paper);
  color: var(--ink-faint);
  font-size: 11.5px;
}

.list-head > div {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.list-head b {
  color: var(--ink-soft);
  font-size: 12.5px;
  font-weight: 650;
}

.list-head small {
  color: var(--ink-faint);
}

.live-mark {
  width: 7px;
  height: 7px;
  border-radius: var(--r-pill);
  background: var(--accent);
}

.knowledge-row {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr);
}

.knowledge-row + .knowledge-row {
  border-top: 1px solid var(--edge);
}

.row-index {
  padding: var(--space-5) var(--space-3);
  border-right: 1px solid var(--edge);
  background: var(--paper);
  color: var(--ink-faint);
  font-size: 12px;
  text-align: center;
}

.row-main {
  min-width: 0;
  padding: var(--space-4) var(--space-5) var(--space-5);
}

.row-meta {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--edge);
}

.answer-type {
  padding: 2px var(--space-2);
  border-radius: var(--r-chip);
  background: var(--accent-wash);
  color: var(--accent);
  font-size: 10.5px;
  font-weight: 650;
}

.row-meta time {
  color: var(--ink-faint);
  font-size: 11.5px;
}

.row-meta button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
  padding: var(--space-1) 0;
  border: 0;
  border-bottom: 1px solid transparent;
  background: transparent;
  color: var(--ink-faint);
  font-size: 11.5px;
  cursor: pointer;
}

.row-meta button :deep(svg) {
  width: 13px;
  height: 13px;
}

.answer-copy {
  max-width: 760px;
  margin-top: var(--space-4);
  color: var(--ink-soft);
}

.answer-copy :deep(h1),
.answer-copy :deep(h2),
.answer-copy :deep(h3) {
  font-size: 15px;
  line-height: 1.5;
}

.answer-copy :deep(p),
.answer-copy :deep(li) {
  font-size: 14px;
  line-height: 1.78;
}

.answer-copy :deep(p:last-child),
.answer-copy :deep(ul:last-child),
.answer-copy :deep(ol:last-child) {
  margin-bottom: 0;
}

.empty-state {
  min-height: 390px;
  display: grid;
  justify-items: center;
  align-content: center;
  padding: var(--space-7);
  border: 1px solid var(--edge);
  border-top: 3px solid var(--ink);
  background: var(--card);
  text-align: center;
}

.empty-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  margin-bottom: var(--space-4);
  border: 1px solid var(--accent-line);
  border-radius: var(--r-control);
  background: var(--accent-wash);
  color: var(--accent);
}

.empty-icon :deep(svg) {
  width: 22px;
  height: 22px;
}

.empty-kicker {
  color: var(--accent);
  font: 600 9.5px/1.4 var(--font-mono);
}

.empty-state h2 {
  margin-top: var(--space-2);
  font-size: 22px;
}

.empty-state p {
  max-width: 30em;
  margin: var(--space-2) 0 var(--space-5);
  color: var(--ink-mute);
  font-size: 13px;
}

.loading-row {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr);
  min-height: 150px;
}

.loading-row + .loading-row {
  border-top: 1px solid var(--edge);
}

.loading-row > div:last-child {
  padding: var(--space-5);
}

.index-skeleton {
  width: 100%;
  height: 100%;
  border-radius: 0;
}

.meta-skeleton {
  width: 130px;
  height: 18px;
}

.text-skeleton {
  width: 90%;
  height: 16px;
  margin-top: var(--space-5);
}

.text-skeleton.short {
  width: 62%;
  margin-top: var(--space-3);
}

.el-pagination {
  justify-content: center;
  margin-top: var(--space-2);
}

@media (hover: hover) and (pointer: fine) {
  .knowledge-row:hover .row-index {
    color: var(--accent);
    background: var(--accent-wash);
  }

  .row-meta button:hover {
    border-bottom-color: var(--flag-high-line);
    color: var(--flag-high);
  }
}

@media (max-width: 640px) {
  .collection-summary {
    align-items: flex-start;
    flex-direction: column;
    gap: var(--space-2);
  }

  .collection-summary p {
    text-align: left;
  }

  .list-head {
    padding-inline: var(--space-4);
  }

  .list-head > span {
    display: none;
  }

  .knowledge-row,
  .loading-row {
    grid-template-columns: 40px minmax(0, 1fr);
  }

  .row-index {
    padding-inline: var(--space-2);
  }

  .row-main {
    padding: var(--space-4);
  }

  .row-meta {
    align-items: flex-start;
    flex-wrap: wrap;
    gap: var(--space-2);
  }

  .row-meta button {
    width: 40px;
    height: 40px;
    justify-content: center;
    margin-top: -10px;
    margin-left: auto;
  }

  .row-meta button {
    font-size: 0;
  }

  .row-meta button :deep(svg) {
    width: 15px;
    height: 15px;
  }

  .el-pagination {
    flex-wrap: wrap;
    gap: var(--space-1);
  }

  .el-pagination :deep(.el-pagination__total) {
    display: none;
  }

  .answer-copy :deep(p),
  .answer-copy :deep(li) {
    font-size: 13.5px;
  }

  .empty-state {
    min-height: 340px;
    padding: var(--space-5);
  }

  .loading-row > div:last-child {
    padding: var(--space-4);
  }
}
</style>
