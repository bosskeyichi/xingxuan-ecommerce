<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { compare } from '@/utils/compare'

const router = useRouter()
const items = ref(compare.list())

const refresh = () => { items.value = compare.list() }
const onEvent = () => refresh()
onMounted(() => window.addEventListener('compare-changed', onEvent))
onBeforeUnmount(() => window.removeEventListener('compare-changed', onEvent))

const remove = id => { compare.remove(id); refresh() }
const clearAll = () => { compare.clear(); refresh() }
const goCompare = () => router.push('/compare')
</script>

<template>
  <transition name="compare-fade">
    <div v-if="items.length" class="compare-bar">
      <div class="compare-inner">
        <div class="compare-label">
          对比栏
          <span class="compare-count">{{ items.length }}/{{ compare.MAX }}</span>
        </div>
        <div class="compare-thumbs">
          <div v-for="p in items" :key="p.id" class="compare-thumb">
            <img v-if="p.cover" :src="p.cover" :alt="p.name" />
            <div v-else class="img-ph">{{ (p.name || '?')[0] }}</div>
            <button class="compare-thumb-x" title="移出对比" @click="remove(p.id)">×</button>
          </div>
          <div v-for="n in (compare.MAX - items.length)" :key="'e' + n" class="compare-thumb is-empty">＋</div>
        </div>
        <div class="compare-actions">
          <button class="compare-clear" @click="clearAll">清空</button>
          <button class="compare-go" :disabled="items.length < 2" @click="goCompare">
            开始对比
          </button>
        </div>
      </div>
    </div>
  </transition>
</template>

<style scoped>
.compare-bar {
  position: fixed;
  left: 0; right: 0; bottom: 0;
  z-index: 200;
  background: var(--surface);
  border-top: 1px solid var(--border);
  box-shadow: 0 -8px 28px rgba(0, 0, 0, .1);
}
.compare-inner {
  max-width: var(--page-max);
  margin: 0 auto;
  padding: 12px 24px;
  display: flex;
  align-items: center;
  gap: 18px;
}
.compare-label {
  font-size: 13.5px;
  font-weight: 600;
  letter-spacing: .04em;
  white-space: nowrap;
}
.compare-count { margin-left: 6px; font-family: var(--font-num); font-size: 12px; color: var(--ink-3); font-weight: 400; }
.compare-thumbs { display: flex; gap: 10px; flex: 1; }
.compare-thumb {
  position: relative;
  width: 46px; height: 46px;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  overflow: hidden;
}
.compare-thumb img { width: 100%; height: 100%; object-fit: cover; }
.compare-thumb.is-empty {
  display: flex; align-items: center; justify-content: center;
  border-style: dashed;
  color: var(--ink-4);
  font-size: 18px;
}
.compare-thumb-x {
  position: absolute; top: 0; right: 0;
  width: 16px; height: 16px;
  border: none; border-radius: 0 0 0 6px;
  background: rgba(0, 0, 0, .5);
  color: #fff;
  font-size: 11px; line-height: 1; cursor: pointer;
}
.compare-thumb-x:hover { background: var(--brand); }
.compare-actions { display: flex; gap: 10px; }
.compare-clear {
  background: transparent;
  border: 1px solid var(--border-strong);
  color: var(--ink-2);
  height: 36px; padding: 0 16px;
  border-radius: var(--r-sm);
  cursor: pointer; font-size: 13px;
}
.compare-clear:hover { border-color: var(--brand); color: var(--brand); }
.compare-go {
  background: var(--brand);
  border: 1px solid var(--brand);
  color: #fff;
  height: 36px; padding: 0 22px;
  border-radius: var(--r-sm);
  cursor: pointer; font-size: 13px; font-weight: 600;
  box-shadow: var(--sh-brand);
}
.compare-go:hover { background: var(--brand-deep); border-color: var(--brand-deep); }
.compare-go:disabled { opacity: .45; cursor: not-allowed; box-shadow: none; }

.compare-fade-enter-active, .compare-fade-leave-active { transition: transform .25s ease, opacity .25s ease; }
.compare-fade-enter-from, .compare-fade-leave-to { transform: translateY(100%); opacity: 0; }
</style>
