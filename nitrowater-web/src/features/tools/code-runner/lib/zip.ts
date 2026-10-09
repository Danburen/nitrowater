/**
 * Single-buffer DEFLATE compression/decompression used to shrink the cached Pyodide files
 * (~50% smaller in IndexedDB).
 *
 * Ported from wtools (MIT) `src/utils/archive/zip.ts` (only the single-buffer helpers).
 */
import JSZip from 'jszip'

/** Compress one Uint8Array with DEFLATE. */
export async function compressUint8Array(data: Uint8Array): Promise<Uint8Array> {
  const zip = new JSZip()
  zip.file('data', data, { compression: 'DEFLATE' })
  return zip.generateAsync({ type: 'uint8array', compression: 'DEFLATE' })
}

/** Inverse of {@link compressUint8Array}. */
export async function decompressUint8Array(compressed: Uint8Array): Promise<Uint8Array> {
  const zip = await JSZip.loadAsync(compressed)
  const file = zip.file('data')
  if (!file) throw new Error('解压失败：缓存块缺少数据')
  return file.async('uint8array')
}
