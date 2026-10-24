package blueprints.graph;

import java.util.List;

/**
 * Узел дерева выражения — форма из docs/API.md, раздел «Дерево выражения ExprNode».
 *
 * @param id       id оператора + «/e» + путь индексов детей, например «…/e/0/1»
 * @param kind     вид узла: call, new, binary, concat, unary, assign, ternary, … (список в контракте)
 * @param label    коротко, что показать на узле: имя метода, знак, тип, значение литерала
 * @param code     исходный текст выражения, до 200 символов
 * @param range    [строка, колонка, строка конца, колонка конца], с 1, конец включительно
 * @param role     роль в родителе («арг 1», «слева», «условие» …); у корня — null
 * @param children вложенные выражения по порядку; у листьев — пустой список
 */
public record ExprNode(String id, String kind, String label, String code, int[] range, String role,
                       List<ExprNode> children) {}
