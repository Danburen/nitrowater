/**
 * Chart colour palette derived from the live design tokens, so canvases painted by
 * ECharts / html2canvas follow the active light/dark theme without duplicating hex values.
 */

export interface ChartPalette {
  /** Primary text (node labels). */
  ink: string
  /** Secondary text (tooltip keys). */
  dim: string
  /** Connector lines / borders. */
  line: string
  /** Node ring + tooltip surface (matches the panel background). */
  card: string
  /** Page background — used as the export canvas background. */
  bg: string
  /** Brand accent (hovered branch, highlights). */
  accent: string
}

export function chartPalette(): ChartPalette {
  const token = (name: string): string =>
    getComputedStyle(document.documentElement).getPropertyValue(name).trim()

  return {
    ink: token('--color-ink'),
    dim: token('--color-dim'),
    line: token('--color-line'),
    card: token('--color-card'),
    bg: token('--color-bg'),
    accent: token('--color-accent'),
  }
}
