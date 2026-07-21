<script setup>
import FloatingActionButton from '@/components/FloatingActionButton.vue'
import { ref } from 'vue'
import { useScroll } from '@vueuse/core'
import { useTravelAiStore } from '@/stores/travelAi'

const emit = defineEmits(['reload', 'toggle-img-only'])
const travelAiStore = useTravelAiStore()

const { y: scrollY } = useScroll(window);
const btn_1_name = ref('imgNote');
const isReloading = ref(false);

function onlyImgNote() {
    btn_1_name.value = btn_1_name.value === 'imgNote'
        ? 'imgNoteSelect'
        : 'imgNote';
    
    // 发射状态变化事件
    const isImgOnly = btn_1_name.value === 'imgNoteSelect';
    emit('toggle-img-only', isImgOnly);
}

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
        <FloatingActionButton
            v-if="!(scrollY > 260 && btn_1_name === 'imgNote')"
            :icon="btn_1_name"
            :label="btn_1_name === 'imgNoteSelect' ? '取消只看图文' : '只看图文'"
            :active="btn_1_name === 'imgNoteSelect'"
            @click="onlyImgNote"
        />
        <FloatingActionButton
            v-if="scrollY >= 260 && !(scrollY > 260 && scrollY < 500 && btn_1_name === 'imgNoteSelect')"
            icon="arrowTop"
            label="回到顶部"
            @click="goTop"
        />
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
