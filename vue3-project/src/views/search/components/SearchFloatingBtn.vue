<script setup>
import FloatingActionButton from '@/components/FloatingActionButton.vue'
import { ref } from 'vue'
import { useScroll } from '@vueuse/core'
import { useTravelAiStore } from '@/stores/travelAi'

const emit = defineEmits(['reload'])
const travelAiStore = useTravelAiStore()

const { y: scrollY } = useScroll(window);
const isReloading = ref(false);

function goTop() {
    window.scrollTo({
        top: 0,
        behavior: 'smooth'
    });
}

function reload() {
    isReloading.value = true;
    emit('reload')
    setTimeout(() => {
        isReloading.value = false;
    }, 1000);
    goTop();
}
</script>

<template>
    <div class="floating-btn-sets">
        <FloatingActionButton icon="magic" label="旅行助手" featured @click="travelAiStore.openAssistant()" />
        <FloatingActionButton v-if="scrollY >= 260" icon="arrowTop" label="回到顶部" @click="goTop" />
        <FloatingActionButton icon="reload" label="刷新" :loading="isReloading" @click="reload" />
    </div>
</template>

<style scoped>
.floating-btn-sets {
    position: fixed;
    bottom: 60px;
    right: 12px;
    z-index: 999;
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 8px;
}
</style>
