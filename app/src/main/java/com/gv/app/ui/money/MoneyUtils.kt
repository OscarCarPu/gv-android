package com.gv.app.ui.money

import com.gv.app.domain.model.Category
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private val moneyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale.UK).apply {
    currency = Currency.getInstance("EUR")
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

fun formatMoney(amount: String): String {
    val n = amount.toDoubleOrNull() ?: return amount
    return moneyFormatter.format(n)
}

fun formatMoney(amount: Double): String = moneyFormatter.format(amount)

data class CategoryOption(
    val id: Int,
    val name: String,
    val depth: Int,
    val label: String,
)

fun buildCategoryOptions(categories: List<Category>): List<CategoryOption> {
    val ids = categories.map { it.id }.toSet()
    val byParent = mutableMapOf<Int, MutableList<Category>>()
    val roots = mutableListOf<Category>()
    for (c in categories) {
        val parent = c.parent_id
        if (parent != null && parent in ids) {
            byParent.getOrPut(parent) { mutableListOf() }.add(c)
        } else {
            roots.add(c)
        }
    }
    val byName = compareBy<Category> { it.name.lowercase() }
    roots.sortWith(byName)
    byParent.values.forEach { it.sortWith(byName) }

    val out = mutableListOf<CategoryOption>()
    fun walk(node: Category, depth: Int) {
        val prefix = "    ".repeat(depth)
        out.add(CategoryOption(node.id, node.name, depth, prefix + node.name))
        byParent[node.id]?.forEach { walk(it, depth + 1) }
    }
    roots.forEach { walk(it, 0) }
    return out
}

data class CategoryTreeRow(
    val category: Category,
    val depth: Int,
    val ancestorHasMore: BooleanArray,
    val isLast: Boolean,
    val hasChildren: Boolean,
)

fun buildCategoryTreeRows(categories: List<Category>): List<CategoryTreeRow> {
    val ids = categories.map { it.id }.toSet()
    val byParent = mutableMapOf<Int, MutableList<Category>>()
    val roots = mutableListOf<Category>()
    for (c in categories) {
        val parent = c.parent_id
        if (parent != null && parent in ids) {
            byParent.getOrPut(parent) { mutableListOf() }.add(c)
        } else {
            roots.add(c)
        }
    }
    val byName = compareBy<Category> { it.name.lowercase() }
    roots.sortWith(byName)
    byParent.values.forEach { it.sortWith(byName) }

    val out = mutableListOf<CategoryTreeRow>()
    fun walk(node: Category, depth: Int, isLast: Boolean, ancestors: BooleanArray) {
        val children = byParent[node.id].orEmpty()
        out.add(
            CategoryTreeRow(
                category = node,
                depth = depth,
                ancestorHasMore = ancestors.copyOf(),
                isLast = isLast,
                hasChildren = children.isNotEmpty(),
            ),
        )
        val nextAncestors = BooleanArray(depth + 1) { i ->
            if (i < depth) ancestors[i] else !isLast
        }
        children.forEachIndexed { idx, child ->
            walk(child, depth + 1, idx == children.lastIndex, nextAncestors)
        }
    }
    roots.forEachIndexed { idx, root ->
        walk(root, 0, idx == roots.lastIndex, BooleanArray(0))
    }
    return out
}

fun typeLabel(type: String): String = when (type) {
    "income" -> "Income"
    "expense" -> "Expense"
    "transfer" -> "Transfer"
    else -> type
}

enum class AmountSign { POS, NEG, NEU }

fun amountSign(type: String): AmountSign = when (type) {
    "income" -> AmountSign.POS
    "expense" -> AmountSign.NEG
    else -> AmountSign.NEU
}
