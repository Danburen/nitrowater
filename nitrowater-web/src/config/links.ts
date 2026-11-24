/**
 * Cross-site destination configuration.
 *
 * <p>These URLs are deployment placeholders — set them via `VITE_*` at build time. Keeping them in
 * one module prevents real domains from being scattered across views.</p>
 */

/** Standalone offline tools site (browser-local tools only; no login required). */
export const offlineToolsUrl = import.meta.env.VITE_OFFLINE_TOOLS_URL || 'https://tools.example.com'
