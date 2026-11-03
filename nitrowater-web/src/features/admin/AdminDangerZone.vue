<script setup lang="ts">
/**
 * Destructive actions, set apart from the page's regular controls.
 *
 * <p>GitHub-style: a red-tinted header band on top, a normal white card body underneath, and a red
 * outer frame — the rows themselves stay on the plain surface so only the frame and the heading
 * carry the warning colour. Geometry (14px card radius, 13.5px title, `px-4 py-3` header and rows,
 * hairline separators) is deliberately identical to {@code AdminSettingsGroup}, so a settings page
 * never mixes two card rhythms. The heading is always the plain words {@code Danger zone}: no
 * description, no per-group structure — the confirm dialog carries the detail, and every entry is
 * one self-contained action rendered by {@code AdminDangerRow}.</p>
 */
</script>

<template>
  <section class="overflow-hidden rounded-[14px] border border-danger/35 bg-card">
    <header class="border-b border-danger/25 bg-danger/15 px-4 py-3">
      <h2 class="text-[13.5px] font-semibold text-danger">
        Danger zone
      </h2>
    </header>
    <div class="divide-y divide-line">
      <slot />
    </div>
  </section>
</template>
