import { ref } from 'vue'
import { pageList } from '../api/http'

/**
 * 分页列表加载：统一 loading / error / 分页状态，并用请求令牌丢弃过期响应。
 * @param {object} options
 * @param {(ctx: {pageNum: number, pageSize: number}) => Promise<any>} options.fetcher 取数，直接返回接口响应
 * @param {number} [options.pageSize=20] 每页条数
 * @param {(res: any) => void} [options.onData] 自定义赋值；缺省按 {data:{list,total,totalPage}} 解析
 * @returns {{ list, pageNum, pageSize, total, totalPage, loading, error, load, reload }}
 */
export function usePageList({ fetcher, pageSize: initialPageSize = 20, onData } = {}) {
  const list = ref([])
  const pageNum = ref(1)
  const pageSize = ref(initialPageSize)
  const total = ref(0)
  const totalPage = ref(0)
  const loading = ref(false)
  const error = ref('')
  // 请求令牌：丢弃过期响应，避免慢请求覆盖新结果
  let seq = 0

  async function load(page = 1) {
    const next = Number(page)
    const target = Number.isFinite(next) && next > 0 ? next : 1
    const mySeq = ++seq
    loading.value = true
    error.value = ''
    try {
      const res = await fetcher({ pageNum: target, pageSize: pageSize.value })
      // 过期响应直接丢弃，也不覆盖 error
      if (mySeq !== seq) return
      pageNum.value = target
      if (onData) {
        onData(res)
      } else {
        list.value = pageList(res)
        total.value = res?.data?.total ?? 0
        totalPage.value = res?.data?.totalPage ?? 0
      }
    } catch (e) {
      if (mySeq !== seq) return
      error.value = e.message
    } finally {
      if (mySeq === seq) loading.value = false
    }
  }

  const reload = () => load(1)

  return { list, pageNum, pageSize, total, totalPage, loading, error, load, reload }
}
