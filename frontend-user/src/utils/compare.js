const KEY = 'compare_list'
const MAX = 4

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

export const compare = {
  list: () => read(),
  has: id => read().some(p => p.id === id),
  add(product) {
    const list = read()
    if (list.some(p => p.id === product.id)) return list
    if (list.length >= MAX) list.shift()
    list.push({
      id: product.id,
      name: product.name,
      price: product.price,
      stock: product.stock,
      sales: product.sales,
      cover: product.cover,
      categoryId: product.categoryId
    })
    write(list)
    return list
  },
  remove(id) {
    const list = read().filter(p => p.id !== id)
    write(list)
    return list
  },
  clear() {
    write([])
  },
  MAX
}
