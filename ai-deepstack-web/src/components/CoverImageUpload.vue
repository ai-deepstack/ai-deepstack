<template>
  <div class="cover-upload">
    <div class="cover-upload-preview">
      <img :src="displayUrl" alt="封面预览" @error="onImgError" />
      <div class="cover-upload-actions">
        <label class="btn-ghost cover-upload-btn">
          {{ modelValue ? '更换封面' : '上传封面' }}
          <input
            ref="fileInput"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            hidden
            @change="onFilePicked"
          />
        </label>
        <button
          v-if="modelValue"
          class="btn-ghost"
          type="button"
          @click="clearCover"
        >清除</button>
      </div>
    </div>
    <p v-if="hint" class="muted cover-upload-hint">{{ hint }}</p>
    <p v-if="error" class="error">{{ error }}</p>

    <Teleport to="body">
      <div v-if="cropVisible" class="cover-crop-mask" @click.self="cancelCrop">
        <div class="cover-crop-panel" role="dialog" aria-modal="true">
          <h3>裁剪封面</h3>
          <p class="muted" style="margin: 0 0 0.75rem; font-size: 0.78rem">
            拖动图片调整位置，滚轮缩放；输出比例 16:9
          </p>
          <div
            class="cover-crop-stage"
            ref="stageRef"
            @pointerdown="onPointerDown"
            @pointermove="onPointerMove"
            @pointerup="onPointerUp"
            @pointercancel="onPointerUp"
            @wheel.prevent="onWheel"
          >
            <img
              ref="imgRef"
              class="cover-crop-img"
              :src="rawUrl"
              :style="imgStyle"
              draggable="false"
              alt=""
              @load="onImageLoad"
            />
            <div class="cover-crop-frame" ref="frameRef" aria-hidden="true" />
          </div>
          <div class="cover-crop-actions">
            <button class="btn-ghost" type="button" @click="cancelCrop">取消</button>
            <button class="btn" type="button" :disabled="uploading" @click="confirmCrop">
              {{ uploading ? '上传中…' : '确认并上传' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, onUnmounted, ref } from 'vue'
import { getToken } from '../api/http'

const props = defineProps({
  modelValue: { type: String, default: '' },
  /** 导出宽度（高度按 16:9） */
  exportWidth: { type: Number, default: 1280 }
})

const emit = defineEmits(['update:modelValue'])

const DEFAULT_COVER = '/agent-cover-default.svg'
const ASPECT = 16 / 9

const fileInput = ref(null)
const stageRef = ref(null)
const frameRef = ref(null)
const imgRef = ref(null)
const error = ref('')
const hint = ref('建议上传横图，裁剪后按 16:9 适配卡片封面')
const uploading = ref(false)
const cropVisible = ref(false)
const rawUrl = ref('')
const natural = ref({ w: 0, h: 0 })
const scale = ref(1)
const offset = ref({ x: 0, y: 0 })
const dragging = ref(false)
const dragStart = ref({ x: 0, y: 0, ox: 0, oy: 0 })

const displayUrl = computed(() => {
  const v = (props.modelValue || '').trim()
  return v || DEFAULT_COVER
})

const imgStyle = computed(() => ({
  transform: `translate(${offset.value.x}px, ${offset.value.y}px) scale(${scale.value})`,
  transformOrigin: '0 0'
}))

function onImgError(e) {
  if (e?.target && e.target.src !== DEFAULT_COVER) e.target.src = DEFAULT_COVER
}

function clearCover() {
  emit('update:modelValue', '')
  error.value = ''
}

function onFilePicked(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  error.value = ''
  if (!/^image\/(jpeg|jpg|png|webp)$/i.test(file.type)) {
    error.value = '仅支持 JPG / PNG / WEBP'
    return
  }
  if (file.size > 8 * 1024 * 1024) {
    error.value = '原图请小于 8MB'
    return
  }
  if (rawUrl.value) URL.revokeObjectURL(rawUrl.value)
  rawUrl.value = URL.createObjectURL(file)
  cropVisible.value = true
  scale.value = 1
  offset.value = { x: 0, y: 0 }
}

function onImageLoad() {
  const img = imgRef.value
  const stage = stageRef.value
  if (!img || !stage) return
  natural.value = { w: img.naturalWidth, h: img.naturalHeight }
  fitImage()
}

function frameRectInStage() {
  const stage = stageRef.value
  const frame = frameRef.value
  if (!stage || !frame) return null
  const sr = stage.getBoundingClientRect()
  const fr = frame.getBoundingClientRect()
  return {
    left: fr.left - sr.left,
    top: fr.top - sr.top,
    fw: fr.width,
    fh: fr.height
  }
}

function fitImage() {
  const stage = stageRef.value
  const box = frameRectInStage()
  const { w, h } = natural.value
  if (!stage || !box || !w || !h) return
  const cover = Math.max(box.fw / w, box.fh / h)
  scale.value = cover
  offset.value = {
    x: box.left + (box.fw - w * cover) / 2,
    y: box.top + (box.fh - h * cover) / 2
  }
  clampOffset()
}

function clampOffset() {
  const box = frameRectInStage()
  const { w, h } = natural.value
  if (!box || !w || !h) return
  const sw = w * scale.value
  const sh = h * scale.value
  const minX = box.left + box.fw - sw
  const minY = box.top + box.fh - sh
  const maxX = box.left
  const maxY = box.top
  offset.value = {
    x: Math.min(maxX, Math.max(minX, offset.value.x)),
    y: Math.min(maxY, Math.max(minY, offset.value.y))
  }
}

function onPointerDown(e) {
  dragging.value = true
  dragStart.value = {
    x: e.clientX,
    y: e.clientY,
    ox: offset.value.x,
    oy: offset.value.y
  }
  e.currentTarget.setPointerCapture?.(e.pointerId)
}

function onPointerMove(e) {
  if (!dragging.value) return
  offset.value = {
    x: dragStart.value.ox + (e.clientX - dragStart.value.x),
    y: dragStart.value.oy + (e.clientY - dragStart.value.y)
  }
  clampOffset()
}

function onPointerUp() {
  dragging.value = false
}

function onWheel(e) {
  const box = frameRectInStage()
  const stage = stageRef.value
  const { w, h } = natural.value
  if (!box || !stage || !w || !h) return
  const minScale = Math.max(box.fw / w, box.fh / h)
  const next = Math.min(6, Math.max(minScale, scale.value * (e.deltaY < 0 ? 1.08 : 0.92)))
  const rect = stage.getBoundingClientRect()
  const cx = e.clientX - rect.left
  const cy = e.clientY - rect.top
  const ratio = next / scale.value
  offset.value = {
    x: cx - (cx - offset.value.x) * ratio,
    y: cy - (cy - offset.value.y) * ratio
  }
  scale.value = next
  clampOffset()
}

function cancelCrop() {
  cropVisible.value = false
  if (rawUrl.value) {
    URL.revokeObjectURL(rawUrl.value)
    rawUrl.value = ''
  }
}

async function confirmCrop() {
  error.value = ''
  uploading.value = true
  try {
    const blob = await exportCroppedBlob()
    const fd = new FormData()
    fd.append('file', blob, 'agent-cover.jpg')
    fd.append('bizType', 'agent-covers')
    const res = await fetch('/api/files/upload', {
      method: 'POST',
      headers: { Authorization: `Bearer ${getToken()}` },
      body: fd
    })
    const body = await res.json()
    if (!res.ok || body.code !== 200) {
      throw new Error(body.message || '上传失败')
    }
    const url = body.data?.url
    if (!url) throw new Error('上传成功但未返回地址')
    emit('update:modelValue', url)
    cancelCrop()
  } catch (e) {
    error.value = e.message || '上传失败'
  } finally {
    uploading.value = false
  }
}

function exportCroppedBlob() {
  return new Promise((resolve, reject) => {
    const img = imgRef.value
    const box = frameRectInStage()
    if (!img || !box || !natural.value.w) {
      reject(new Error('图片未就绪'))
      return
    }
    const sx = (box.left - offset.value.x) / scale.value
    const sy = (box.top - offset.value.y) / scale.value
    const sw = box.fw / scale.value
    const sh = box.fh / scale.value

    const outW = props.exportWidth
    const outH = Math.round(outW / ASPECT)
    const canvas = document.createElement('canvas')
    canvas.width = outW
    canvas.height = outH
    const ctx = canvas.getContext('2d')
    ctx.fillStyle = '#0a1424'
    ctx.fillRect(0, 0, outW, outH)
    ctx.drawImage(img, sx, sy, sw, sh, 0, 0, outW, outH)
    canvas.toBlob(
      (blob) => {
        if (!blob) reject(new Error('导出失败'))
        else resolve(blob)
      },
      'image/jpeg',
      0.9
    )
  })
}

onUnmounted(() => {
  if (rawUrl.value) URL.revokeObjectURL(rawUrl.value)
})
</script>

<style scoped>
.cover-upload-preview {
  position: relative;
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid rgba(148, 183, 255, 0.16);
  aspect-ratio: 16 / 9;
  background: #0a1424;
}
.cover-upload-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.cover-upload-actions {
  position: absolute;
  left: 0.65rem;
  right: 0.65rem;
  bottom: 0.65rem;
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
}
.cover-upload-btn {
  cursor: pointer;
}
.cover-upload-hint {
  margin: 0.45rem 0 0;
  font-size: 0.72rem;
}

.cover-crop-mask {
  position: fixed;
  inset: 0;
  z-index: 220;
  background: rgba(2, 6, 14, 0.72);
  backdrop-filter: blur(6px);
  display: grid;
  place-items: center;
  padding: 1rem;
}
.cover-crop-panel {
  width: min(720px, 100%);
  background: linear-gradient(180deg, #121c2e, #0a1220);
  border: 1px solid rgba(148, 183, 255, 0.18);
  border-radius: 18px;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.5);
  padding: 1.1rem 1.15rem 1.15rem;
}
.cover-crop-panel h3 {
  margin: 0 0 0.35rem;
  font-family: var(--font-brand);
  font-size: 1.15rem;
}
.cover-crop-stage {
  position: relative;
  height: min(52vh, 380px);
  border-radius: 14px;
  overflow: hidden;
  background:
    linear-gradient(45deg, #0c1524 25%, transparent 25%) 0 0 / 16px 16px,
    linear-gradient(-45deg, #0c1524 25%, transparent 25%) 0 8px / 16px 16px,
    #152033;
  touch-action: none;
  cursor: grab;
  user-select: none;
}
.cover-crop-stage:active {
  cursor: grabbing;
}
.cover-crop-img {
  position: absolute;
  left: 0;
  top: 0;
  max-width: none;
  pointer-events: none;
  will-change: transform;
}
.cover-crop-frame {
  position: absolute;
  left: 50%;
  top: 50%;
  width: calc(100% - 48px);
  max-width: 100%;
  aspect-ratio: 16 / 9;
  transform: translate(-50%, -50%);
  border: 2px solid rgba(127, 245, 223, 0.85);
  border-radius: 10px;
  box-shadow: 0 0 0 9999px rgba(0, 0, 0, 0.48);
  pointer-events: none;
}
.cover-crop-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 0.9rem;
}
</style>
