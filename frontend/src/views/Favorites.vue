<template>
  <div class="page">
    <header class="head">
      <p class="eyebrow">我的收藏</p>
      <h1>钉下来的回答</h1>
      <p class="lead">在问诊里点「收藏该回答」，有用的解释就会留在这里。</p>
    </header>

    <div v-if="loading && !items.length" class="stack">
      <div v-for="n in 3" :key="n" class="skeleton" style="height: 128px; border-radius: var(--r-card)"></div>
    </div>

    <div v-else-if="!items.length" class="panel empty">
      <span v-html="ICONS.star"></span>
      <h3>还没有收藏</h3>
      <p>问出第一个问题，把有用的回答留下来。</p>
      <router-link class="btn btn-primary" to="/chat">去问诊</router-link>
    </div>

    <article v-for="it in items" :key="it.id" class="clip">
      <div class="meta">
        <time>{{ formatWhen(it.createdAt) }}</time>
        <button class="btn btn-quiet btn-sm" type="button" @click="remove(it.id)">
          <span v-html="ICONS.trash"></span>取消收藏
        </button>
      </div>
      <div class="prose" v-html="renderMarkdown(it.content, terms)"></div>
    </article>

    <el-pagination
      v-if="total > pageSize"
      layout="prev, pager, next, total"
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

async function remove(id: number) {
  await deleteFavorite(id)
  // 删掉当前页最后一条时往前退一页，避免停在空页
  const lastOnPage = items.value.length === 1 && page.value > 1
  await load(lastOnPage ? page.value - 1 : page.value)
}
</script>

<style scoped>
.page {
  max-width: 780px;
  display: grid;
  gap: 16px;
}

.head h1 {
  margin: 4px 0 8px;
}

.lead {
  color: var(--ink-mute);
}

/* 收藏卡：左侧一条主色细条，长文段落之间有明确的起始边界 */
.clip {
  background: var(--card);
  border: 1px solid var(--edge);
  border-left: 3px solid var(--accent);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-1), var(--inner-light);
  padding: 16px 20px;
}

.meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.meta time {
  font-size: 12px;
  color: var(--ink-faint);
  margin-right: auto;
}

.el-pagination {
  justify-content: center;
}

@media (max-width: 720px) {
  .clip {
    padding: 14px 16px;
  }
}
</style>
