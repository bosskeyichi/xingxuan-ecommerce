const KEY = 'search_history'
const MAX = 8

function read() {
  try {
    const raw = localStorage.getItem(KEY)
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

function write(list) {
  localStorage.setItem(KEY, JSON.stringify(list))
}

export const searchHistory = {
  list: () => read(),
  push(keyword) {
    const k = (keyword || '').trim()
    if (!k) return
    const list = read().filter(x => x !== k)
    list.unshift(k)
    if (list.length > MAX) list.length = MAX
    write(list)
  },
  remove(keyword) {
    write(read().filter(x => x !== keyword))
  },
  clear() {
    write([])
  }
}

export const HOT_KEYWORDS = ['iPhone', 'MacBook', '戴森', 'SK-II', '三体', '优衣库', '雅诗兰黛', '小米']
