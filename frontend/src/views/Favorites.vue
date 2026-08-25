<template>
  <div class="page">
    <PageHeader title="我的收藏" desc="问答里点「收藏该回答」会出现在这里，方便以后复看。" />

    <div v-if="!items.length" class="empty-sheet">
      <img src="/art/empty-record.svg" alt="" />
      <div>
        <h3>还没有收藏</h3>
        <p>去智能问答里，把有用的回答钉下来。</p>
        <router-link class="copper-btn" to="/chat">去问诊</router-link>
      </div>
    </div>

    <article v-for="it in items" :key="it.id" class="clip card">
      <div class="meta">
        <time>{{ formatWhen(it.createdAt) }}</time>
        <button class="ghost-btn slim" type="button" @click="remove(it.id)">取消收藏</button>
      </div>
      <div class="markdown-body" v-html="renderMarkdown(it.content, terms)"></div>
    </article>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { deleteFavorite, listFavorites, type FavoriteItem } from '@/api/favorites'
import { listTerms } from '@/api/knowledge'
import { renderMarkdown } from '@/utils/markdown'
import { formatWhen } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'

const items = ref<FavoriteItem[]>([])
const terms = ref<string[]>([])

onMounted(async () => {
  items.value = (await listFavorites()).data || []
  try {
    terms.value = (await listTerms()).data || []
  } catch {
    terms.value = []
  }
})

async function remove(id: number) {
  await deleteFavorite(id)
  items.value = (await listFavorites()).data || []
}
</script>

<style scoped>
.empty-sheet {
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: 20px;
  align-items: center;
}
.empty-sheet img {
  width: 100%;
  height: 200px;
  object-fit: cover;
}
.empty-sheet h3 {
  margin: 0 0 8px;
  font-size: 26px;
}
.empty-sheet p {
  color: var(--ink-3);
}
.copper-btn {
  display: inline-flex;
  margin-top: 8px;
}
.clip {
  padding: 20px 22px;
  margin-bottom: 14px;
}
.meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--ink-3);
  font-size: 12px;
  margin-bottom: 8px;
}
.slim {
  padding: 4px 10px;
  font-size: 12px;
}
@media (max-width: 720px) {
  .empty-sheet {
    grid-template-columns: 1fr;
    padding: 0 0 16px;
  }
}
</style>
