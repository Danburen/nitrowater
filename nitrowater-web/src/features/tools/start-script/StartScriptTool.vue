<script setup lang="ts">
/**
 * start.bat / start.sh generator.
 *
 * The Windows script is emitted with CRLF and kept ASCII-first (the project convention for .bat);
 * the Linux script is emitted with LF and UTF-8. Both are generated locally from the same form.
 */
import { computed, ref } from 'vue'
import FieldShell from '../../../shared/ui/FieldShell.vue'
import PageShell from '../../../shared/ui/PageShell.vue'
import TextArea from '../../../shared/ui/TextArea.vue'
import TextField from '../../../shared/ui/TextField.vue'
import { toast } from '../../../shared/ui/toast'
import { copyText, downloadText } from '../lib/mc/download'

const platforms = [
  { key: 'windows', label: 'Windows · start.bat' },
  { key: 'linux', label: 'Linux · start.sh' },
] as const

const scriptPresets = [
  { key: 'paper', label: 'Paper 4G', desc: '2–4 GB + Aikar flags', min: 2, max: 4, aikar: true },
  { key: 'vanilla', label: 'Vanilla 2G', desc: '1–2 GB，无额外 flags', min: 1, max: 2, aikar: false },
  { key: 'modded', label: '大型模组 8G', desc: '4–8 GB，关闭 Aikar flags', min: 4, max: 8, aikar: false },
] as const

type Platform = (typeof platforms)[number]['key']
type ScriptPreset = (typeof scriptPresets)[number]

const AIKAR_FLAGS = [
  '-XX:+UseG1GC',
  '-XX:+ParallelRefProcEnabled',
  '-XX:MaxGCPauseMillis=200',
  '-XX:+UnlockExperimentalVMOptions',
  '-XX:+DisableExplicitGC',
  '-XX:+AlwaysPreTouch',
  '-XX:G1NewSizePercent=30',
  '-XX:G1MaxNewSizePercent=40',
  '-XX:G1HeapRegionSize=8M',
  '-XX:G1ReservePercent=20',
  '-XX:G1HeapWastePercent=5',
  '-XX:G1MixedGCCountTarget=4',
  '-XX:InitiatingHeapOccupancyPercent=15',
  '-XX:G1MixedGCLiveThresholdPercent=90',
  '-XX:G1RSetUpdatingPauseTimePercent=5',
  '-XX:SurvivorRatio=32',
  '-XX:+PerfDisableSharedMem',
  '-XX:MaxTenuringThreshold=1',
  '-Dusing.aikars.flags=https://mcflags.emc.gs',
  '-Daikars.new.flags=true',
].join(' ')

const platform = ref<Platform>('windows')
const jarName = ref('server.jar')
const javaPath = ref('java')
const minMemory = ref('2')
const maxMemory = ref('4')
const nogui = ref(true)
const aikarFlags = ref(true)
const autoRestart = ref(false)
const utf8Console = ref(false)
const extraArgs = ref('')

const filename = computed(() => (platform.value === 'windows' ? 'start.bat' : 'start.sh'))
const lineEndingLabel = computed(() => (platform.value === 'windows' ? 'CRLF · ASCII 优先' : 'LF · UTF-8'))

const jarError = computed(() => {
  const value = jarName.value.trim()
  if (!value) return '请填写服务端 JAR 文件名'
  if (value.includes('/') || value.includes('\\')) return '只填文件名，不要带路径'
  if (platform.value === 'windows' && /[<>:"|?*]/.test(value)) return '文件名包含 Windows 不允许的字符'
  return ''
})

const javaError = computed(() => {
  const value = javaPath.value.trim()
  if (!value) return '请填写 Java 可执行文件'
  if (/[\r\n]/.test(value)) return 'Java 路径不能包含换行'
  return ''
})

const memoryError = computed(() => {
  const min = Number(minMemory.value)
  const max = Number(maxMemory.value)
  if (!Number.isFinite(min) || !Number.isFinite(max)) return '内存必须是数字'
  if (min < 1) return '最小内存至少 1 GB'
  if (max < min) return '最大内存不能小于最小内存'
  if (max > 64) return '最大内存不要超过 64 GB'
  return ''
})

const hasErrors = computed(() => Boolean(jarError.value || javaError.value || memoryError.value))

const script = computed(() => (platform.value === 'windows' ? buildWindows() : buildLinux()))
const scriptLineCount = computed(() => script.value.trimEnd().split(/\r?\n/).length)
const hasNonAscii = computed(() =>
  Array.from(script.value).some((char) => (char.codePointAt(0) ?? 0) > 0x7f),
)

const warnings = computed(() => {
  const list: string[] = []
  if (aikarFlags.value) list.push('Aikar flags 主要针对 Paper/Purpur；原版或模组核心不一定适用。')
  if (autoRestart.value) list.push('自动重启会循环执行；停止请关闭窗口或按 Ctrl+C。')
  if (platform.value === 'windows' && hasNonAscii.value) {
    list.push('.bat 包含非 ASCII 字符；建议使用英文路径与参数，避免乱码或命令解析问题。')
  }
  if (platform.value === 'windows' && /%/.test(`${javaPath.value}${jarName.value}${extraArgs.value}`)) {
    list.push('路径、文件名或额外参数包含 %，批处理会把它当作变量展开。')
  }
  if (platform.value === 'windows' && /[&|<>^]/.test(extraArgs.value)) {
    list.push('额外参数包含 cmd 特殊字符（& | < > ^），可能破坏脚本。')
  }
  if (/[\r\n]/.test(extraArgs.value)) list.push('额外参数包含换行，可能导致脚本语法错误。')
  return list
})

function cleanPath(value: string): string {
  return value.trim().replace(/^"(.*)"$/, '$1')
}

function formatMemory(value: string | number): string {
  const number = Number(value)
  return `${Math.max(1, Math.round(Number.isFinite(number) ? number : 1))}G`
}

function buildWindows(): string {
  const lines: string[] = ['@echo off']
  if (utf8Console.value) lines.push('chcp 65001 >nul')
  lines.push('setlocal', 'title Minecraft Server', 'cd /d "%~dp0"', '')
  lines.push(`set "JAVA=${cleanPath(javaPath.value) || 'java'}"`)
  lines.push(`set "JAR=${cleanPath(jarName.value) || 'server.jar'}"`)
  lines.push(`set "XMS=${formatMemory(minMemory.value)}"`)
  lines.push(`set "XMX=${formatMemory(maxMemory.value)}"`)
  lines.push(`set "AIKAR=${aikarFlags.value ? AIKAR_FLAGS : ''}"`)
  lines.push('')

  const extra = extraArgs.value.trim()
  const command = `"%JAVA%" -Xms%XMS% -Xmx%XMX% %AIKAR%${extra ? ` ${extra}` : ''} -jar "%JAR%"${nogui.value ? ' nogui' : ''}`

  if (autoRestart.value) {
    lines.push(':loop', command, 'echo Server stopped. Restarting in 5 seconds...', 'timeout /t 5 /nobreak >nul', 'goto loop')
  } else {
    lines.push(command, 'echo Server stopped.', 'pause')
  }

  return `${lines.join('\r\n')}\r\n`
}

function buildLinux(): string {
  const lines: string[] = [
    '#!/usr/bin/env bash',
    autoRestart.value ? 'set -uo pipefail' : 'set -euo pipefail',
    '',
    'cd "$(dirname "$0")"',
    '',
    `JAVA="${cleanPath(javaPath.value) || 'java'}"`,
    `JAR="${cleanPath(jarName.value) || 'server.jar'}"`,
    `XMS="${formatMemory(minMemory.value)}"`,
    `XMX="${formatMemory(maxMemory.value)}"`,
    `JAVA_ARGS="${aikarFlags.value ? AIKAR_FLAGS : ''}"`,
    '',
  ]

  const extra = extraArgs.value.trim()
  const command = `"$JAVA" -Xms"$XMS" -Xmx"$XMX" $JAVA_ARGS${extra ? ` ${extra}` : ''} -jar "$JAR"${nogui.value ? ' nogui' : ''}`

  if (autoRestart.value) {
    lines.push('while true; do', `  ${command}`, '  echo "Server stopped. Restarting in 5 seconds..."', '  sleep 5', 'done')
  } else {
    lines.push(`exec ${command}`)
  }

  return `${lines.join('\n')}\n`
}

function applyScriptPreset(preset: ScriptPreset): void {
  minMemory.value = String(preset.min)
  maxMemory.value = String(preset.max)
  aikarFlags.value = preset.aikar
  toast.success(`已应用${preset.label}预设`)
}

function reset(): void {
  jarName.value = 'server.jar'
  javaPath.value = 'java'
  minMemory.value = '2'
  maxMemory.value = '4'
  nogui.value = true
  aikarFlags.value = true
  autoRestart.value = false
  utf8Console.value = false
  extraArgs.value = ''
  toast.success('已恢复默认设置')
}

async function copy(): Promise<void> {
  if (hasErrors.value) return
  const ok = await copyText(script.value)
  if (ok) toast.success(`已复制 ${filename.value}`)
  else toast.error('复制失败，请手动选择复制')
}

function download(): void {
  if (hasErrors.value) return
  if (platform.value === 'windows') {
    downloadText(filename.value, script.value, 'text/plain;charset=utf-8')
  } else {
    downloadText(filename.value, script.value, 'text/x-shellscript;charset=utf-8')
  }
  toast.success(`已下载 ${filename.value}`)
}
</script>

<template>
  <PageShell
    title="启动脚本生成"
    desc="生成可直接运行的 start.bat / start.sh：内存、nogui、Aikar flags、自动重启和 Java 路径都可配置。Windows 使用 CRLF，Linux 使用 LF。"
  >
    <div class="flex flex-col gap-4">
      <!-- Platform + presets -->
      <div class="panel flex flex-wrap items-center justify-between gap-3">
        <div class="flex rounded-[9px] border border-line bg-soft p-1">
          <button
            v-for="item in platforms"
            :key="item.key"
            type="button"
            class="rounded-[7px] px-3 py-1.5 text-[12.5px] transition"
            :class="platform === item.key ? 'bg-card text-ink shadow-sm' : 'text-dim hover:text-ink'"
            @click="platform = item.key"
          >
            {{ item.label }}
          </button>
        </div>

        <div class="flex flex-wrap items-center gap-2">
          <span class="text-[12px] text-faint">预设</span>
          <button
            v-for="preset in scriptPresets"
            :key="preset.key"
            type="button"
            class="btn"
            :title="preset.desc"
            @click="applyScriptPreset(preset)"
          >
            {{ preset.label }}
          </button>
          <button
            type="button"
            class="btn"
            @click="reset"
          >
            恢复默认
          </button>
        </div>
      </div>

      <div class="grid gap-4 lg:grid-cols-[minmax(0,1fr)_380px]">
        <!-- Settings -->
        <section class="panel flex flex-col gap-5">
          <div class="space-y-3">
            <div>
              <h2 class="text-sm font-semibold text-ink">
                服务端
              </h2>
              <p class="mt-0.5 text-[12.5px] text-dim">
                JAR 与脚本放在同一目录
              </p>
            </div>
            <div class="grid gap-3 md:grid-cols-2">
              <FieldShell
                label="服务端 JAR"
                html-for="jar-name"
                :error="jarError"
                hint="例如 paper.jar、fabric-server-launch.jar。"
              >
                <TextField
                  id="jar-name"
                  v-model="jarName"
                  placeholder="server.jar"
                />
              </FieldShell>
              <FieldShell
                label="Java 可执行文件"
                html-for="java-path"
                :error="javaError"
                hint="填 java 使用 PATH；也可填完整路径。"
              >
                <TextField
                  id="java-path"
                  v-model="javaPath"
                  placeholder="java"
                />
              </FieldShell>
            </div>
          </div>

          <div class="space-y-3 border-t border-line pt-4">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <div>
                <h2 class="text-sm font-semibold text-ink">
                  内存
                </h2>
                <p class="mt-0.5 text-[12.5px] text-dim">
                  单位 GB，按实际物理内存留出余量
                </p>
              </div>
              <code class="rounded bg-soft px-2 py-1 text-[11.5px] text-dim">-Xms{{ formatMemory(minMemory) }} -Xmx{{ formatMemory(maxMemory) }}</code>
            </div>
            <div class="grid gap-3 md:grid-cols-2">
              <FieldShell
                label="最小内存（GB）"
                html-for="min-memory"
              >
                <TextField
                  id="min-memory"
                  v-model="minMemory"
                  type="number"
                  min="1"
                  max="64"
                  step="1"
                />
              </FieldShell>
              <FieldShell
                label="最大内存（GB）"
                html-for="max-memory"
              >
                <TextField
                  id="max-memory"
                  v-model="maxMemory"
                  type="number"
                  min="1"
                  max="64"
                  step="1"
                />
              </FieldShell>
            </div>
            <p
              v-if="memoryError"
              class="text-[11.5px] text-danger"
            >
              {{ memoryError }}
            </p>
          </div>

          <div class="space-y-2.5 border-t border-line pt-4">
            <h2 class="text-sm font-semibold text-ink">
              选项
            </h2>
            <label class="flex cursor-pointer items-start gap-2 text-[12.5px] text-dim">
              <input
                v-model="nogui"
                type="checkbox"
                class="mt-0.5 accent-accent"
              >
              <span>
                <span class="text-ink">使用 nogui</span>
                <span class="block text-[11.5px] text-faint">不打开服务端图形界面，通常更省资源。</span>
              </span>
            </label>
            <label class="flex cursor-pointer items-start gap-2 text-[12.5px] text-dim">
              <input
                v-model="aikarFlags"
                type="checkbox"
                class="mt-0.5 accent-accent"
              >
              <span>
                <span class="text-ink">启用 Aikar flags</span>
                <span class="block text-[11.5px] text-faint">Paper/Purpur 常用的 G1GC 优化参数。</span>
              </span>
            </label>
            <label class="flex cursor-pointer items-start gap-2 text-[12.5px] text-dim">
              <input
                v-model="autoRestart"
                type="checkbox"
                class="mt-0.5 accent-accent"
              >
              <span>
                <span class="text-ink">退出后自动重启</span>
                <span class="block text-[11.5px] text-faint">崩溃或手动停止后等待 5 秒再启动。</span>
              </span>
            </label>
            <label
              v-if="platform === 'windows'"
              class="flex cursor-pointer items-start gap-2 text-[12.5px] text-dim"
            >
              <input
                v-model="utf8Console"
                type="checkbox"
                class="mt-0.5 accent-accent"
              >
              <span>
                <span class="text-ink">UTF-8 控制台</span>
                <span class="block text-[11.5px] text-faint">加入 chcp 65001；建议 Java 18+ 使用。</span>
              </span>
            </label>
          </div>

          <div class="border-t border-line pt-4">
            <FieldShell
              label="额外 JVM 参数（可选）"
              html-for="extra-args"
              hint="直接拼接到 java 命令中；包含空格或特殊字符时请自行确认引号。"
            >
              <TextField
                id="extra-args"
                v-model="extraArgs"
                placeholder="-Dfoo=bar"
              />
            </FieldShell>
          </div>
        </section>

        <!-- Output -->
        <section class="panel flex flex-col gap-3">
          <div class="flex flex-wrap items-start justify-between gap-2">
            <div>
              <h2 class="text-sm font-semibold text-ink">
                生成的脚本
              </h2>
              <p class="mt-0.5 text-[12px] text-faint">
                {{ lineEndingLabel }} · {{ scriptLineCount }} 行
              </p>
            </div>
            <div class="flex flex-wrap gap-2">
              <button
                type="button"
                class="btn"
                :disabled="hasErrors"
                @click="copy"
              >
                复制
              </button>
              <button
                type="button"
                class="btn btn-primary"
                :disabled="hasErrors"
                @click="download"
              >
                下载 {{ filename }}
              </button>
            </div>
          </div>

          <div
            v-if="warnings.length"
            class="rounded-[9px] border border-warn/30 bg-warn/10 px-3 py-2 text-[11.5px] leading-relaxed text-warn"
          >
            <ul class="list-disc space-y-0.5 pl-4">
              <li
                v-for="warning in warnings"
                :key="warning"
              >
                {{ warning }}
              </li>
            </ul>
          </div>

          <TextArea
            :model-value="script"
            readonly
            min-height="440px"
            class="text-[12px]"
            aria-label="生成的启动脚本"
          />

          <p class="text-[11.5px] leading-relaxed text-faint">
            <template v-if="platform === 'windows'">
              下载后直接双击运行；若提示找不到 Java，请把「Java 可执行文件」改成完整路径。
            </template>
            <template v-else>
              下载后执行 <code>chmod +x start.sh && ./start.sh</code>；长期运行建议用 systemd / screen / tmux 托管。
            </template>
          </p>
        </section>
      </div>
    </div>
  </PageShell>
</template>
