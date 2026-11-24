import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import tseslint from 'typescript-eslint'
import globals from 'globals'

export default tseslint.config(
  {
    // `public/**` (favicons and other verbatim static assets) is copied into the build as-is;
    // never lint it.
    ignores: ['dist/**', 'node_modules/**', 'public/**', '**/*.d.ts'],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: {
        parser: tseslint.parser,
        extraFileExtensions: ['.vue'],
      },
    },
  },
  {
    files: ['**/*.{js,ts,vue}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: { ...globals.browser },
    },
  },
  {
    files: ['eslint.config.js', 'vite.config.ts'],
    languageOptions: {
      globals: { ...globals.node },
    },
  },
  {
    rules: {
      'vue/multi-word-component-names': ['error', { ignores: ['App'] }],
    },
  },
)
