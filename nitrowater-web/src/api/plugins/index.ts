/**
 * Plugin domain barrel.
 *
 * `types` holds the wire shapes, `catalog` the anonymous reads used by the plugin center, `admin`
 * the metadata CRUD and jar uploads. Call sites keep using the domain path:
 * `import { fetchPlugins, type PluginSummary } from '../../api/plugins'`.
 */
export * from './types'
export * from './catalog'
export * from './admin'
