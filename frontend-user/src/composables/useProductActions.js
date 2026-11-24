import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { favoriteApi, cartApi } from '@/api'
import { useUserStore } from '@/stores/user'

/**
 * 商品卡片通用动作：加入购物车 / 收藏切换。
 * 统一登录拦截与提示文案，别让各页面各写一套。
 * @param {import('vue').Ref<Array<number>>} favIds 收藏 id 集合（由调用方持有）
 */
export function useProductActions(favIds = ref([])) {
  const router = useRouter()
  const userStore = useUserStore()
  const ids = favIds

  const requireLogin = () => {
    if (userStore.isLogin) return true
    ElMessage.warning('请先登录')
    router.push('/login')
    return false
  }

  const loadFavIds = async () => {
    if (!userStore.isLogin) { ids.value = []; return }
    try {
      const r = await favoriteApi.ids()
      ids.value = r.data || []
    } catch (e) {
      ids.value = []
    }
  }

  const addToCart = async (product, quantity = 1) => {
    if (!requireLogin()) return false
    try {
      await cartApi.add({ productId: product.id, quantity })
      ElMessage.success('已加入购物车')
      window.dispatchEvent(new Event('cart:refresh'))
      return true
    } catch (e) {
      return false
    }
  }

  const toggleFav = async product => {
    if (!requireLogin()) return null
    const has = ids.value.includes(product.id)
    try {
      if (has) {
        await favoriteApi.remove(product.id)
        ids.value = ids.value.filter(id => id !== product.id)
        ElMessage.success('已取消收藏')
        return false
      }
      await favoriteApi.add(product.id)
      ids.value = [...ids.value, product.id]
      ElMessage.success('已加入收藏')
      return true
    } catch (e) {
      return null
    }
  }

  return { favIds: ids, loadFavIds, addToCart, toggleFav }
}
