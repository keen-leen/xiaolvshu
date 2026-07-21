<script setup>
import SvgIcon from '@/components/SvgIcon.vue'

defineProps({
  icon: {
    type: String,
    required: true
  },
  label: {
    type: String,
    required: true
  },
  active: {
    type: Boolean,
    default: false
  },
  loading: {
    type: Boolean,
    default: false
  },
  featured: {
    type: Boolean,
    default: false
  },
  ariaLabel: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['click'])
</script>

<template>
  <button
    type="button"
    :class="['floating-action', { active, loading, featured }]"
    :aria-label="ariaLabel || label"
    @click="emit('click', $event)"
  >
    <span class="floating-action-icon" aria-hidden="true">
      <SvgIcon :name="icon" width="20" height="20" color="currentColor" />
    </span>
    <span class="floating-action-label">{{ label }}</span>
  </button>
</template>

<style scoped>
.floating-action {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  min-width: 38px;
  height: 38px;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--border-color-primary);
  border-radius: 999px;
  color: var(--text-color-secondary);
  background: var(--bg-color-primary);
  box-shadow: 0 3px 12px color-mix(in srgb, var(--shadow-color) 64%, transparent);
  cursor: pointer;
  transition: color 0.2s ease, border-color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.floating-action::after {
  position: absolute;
  z-index: 0;
  top: -24px;
  bottom: -24px;
  left: -42px;
  width: 24px;
  content: '';
  pointer-events: none;
  opacity: 0;
  background: linear-gradient(90deg, transparent, color-mix(in srgb, #fff 76%, var(--primary-color)), transparent);
  filter: blur(1px);
  transform: skewX(-18deg) translateX(0);
}

.floating-action:hover,
.floating-action:focus-visible {
  border-color: color-mix(in srgb, var(--primary-color) 28%, var(--border-color-primary));
  color: var(--text-color-primary);
  background: var(--bg-color-secondary);
  box-shadow: 0 5px 16px color-mix(in srgb, var(--shadow-color) 78%, transparent);
  transform: translateY(-1px);
}

.floating-action:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: 2px;
}

.floating-action.active {
  border-color: color-mix(in srgb, var(--primary-color) 38%, var(--border-color-primary));
  color: var(--primary-color);
  background: color-mix(in srgb, var(--primary-color) 9%, var(--bg-color-primary));
}

.floating-action.featured {
  border-color: color-mix(in srgb, var(--primary-color) 42%, var(--border-color-primary));
  color: var(--primary-color);
  background: color-mix(in srgb, var(--primary-color) 10%, var(--bg-color-primary));
  box-shadow:
    0 3px 12px color-mix(in srgb, var(--shadow-color) 58%, transparent),
    0 0 0 3px color-mix(in srgb, var(--primary-color) 7%, transparent);
}

.floating-action.featured:hover,
.floating-action.featured:focus-visible {
  border-color: color-mix(in srgb, var(--primary-color) 58%, var(--border-color-primary));
  color: var(--primary-color);
  background: color-mix(in srgb, var(--primary-color) 13%, var(--bg-color-primary));
  box-shadow:
    0 5px 17px color-mix(in srgb, var(--primary-color-shadow) 74%, transparent),
    0 0 0 3px color-mix(in srgb, var(--primary-color) 9%, transparent);
}

.floating-action.featured::after {
  opacity: 0.72;
  animation: featuredLight 3.8s infinite ease-in-out;
}

.floating-action-icon {
  position: relative;
  z-index: 1;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
}

.floating-action-label {
  position: relative;
  z-index: 1;
  max-width: 0;
  overflow: hidden;
  color: currentColor;
  font-size: 12px;
  font-weight: 650;
  line-height: 1;
  opacity: 0;
  white-space: nowrap;
  transform: translateX(6px);
  transition: max-width 0.22s ease, margin 0.22s ease, padding 0.22s ease, opacity 0.16s ease, transform 0.22s ease;
}

.floating-action:hover .floating-action-label,
.floating-action:focus-visible .floating-action-label {
  max-width: 132px;
  margin-left: 4px;
  padding-right: 12px;
  opacity: 1;
  transform: translateX(0);
}

.floating-action.loading .floating-action-icon {
  animation: floatingActionSpin 0.9s linear infinite;
}

@keyframes floatingActionSpin {
  to { transform: rotate(360deg); }
}

@keyframes featuredLight {
  0%, 48% { transform: skewX(-18deg) translateX(0); }
  76%, 100% { transform: skewX(-18deg) translateX(230px); }
}

@media (prefers-reduced-motion: reduce) {
  .floating-action,
  .floating-action-label,
  .floating-action-icon {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }

  .floating-action.featured::after {
    opacity: 0;
  }
}
</style>
