import type { ExprKind, ExprNode } from '../../api/types'

export function findExpression(root: ExprNode | null | undefined, id: string | null): ExprNode | null {
  if (!root || !id) return null
  if (root.id === id) return root
  for (const child of root.children) {
    const found = findExpression(child, id)
    if (found) return found
  }
  return null
}

export const expressionKind: Record<ExprKind, string> = {
  call: 'вызов', new: 'создание', binary: 'операция', concat: 'склейка · эвристика', unary: 'операция',
  assign: 'присваивание', ternary: 'выбор', instanceof: 'проверка типа', cast: 'приведение',
  lambda: 'лямбда', methodRef: 'ссылка на метод', literal: 'значение', name: 'имя', field: 'поле',
  index: 'индекс', arrayInit: 'массив', switchExpr: 'выбор switch', declare: 'объявление',
  forHeader: 'заголовок for', forEachHeader: 'перебор', resources: 'ресурсы', block: 'блок', other: 'выражение',
}
