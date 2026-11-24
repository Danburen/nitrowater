<script setup lang="ts">
/**
 * Compact outbound link to the tools module source on GitHub.
 *
 * <p>Lives in the shell top bar next to the theme toggle. The tools site is otherwise fully
 * standalone: this is an outbound documentation link, not a route back to the portal.</p>
 */
import SvgIcon from './SvgIcon.vue'
import { TOOLS_SOURCE_URL } from '../config/repo'
</script>

<template>
  <a
    :href="TOOLS_SOURCE_URL"
    target="_blank"
    rel="noopener noreferrer"
    title="在 GitHub 上查看本项目"
    aria-label="在 GitHub 上查看本项目"
    class="inline-flex size-9 shrink-0 items-center justify-center rounded-[9px] border border-line bg-soft text-ink transition hover:border-line-strong hover:text-accent"
  >
    <SvgIcon
      name="github"
      :size="18"
    />
  </a>
</template>
