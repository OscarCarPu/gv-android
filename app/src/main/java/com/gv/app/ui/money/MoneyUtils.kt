package com.gv.app.ui.money

import com.gv.app.domain.model.Category
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * `1.234,56 €` — gv-web's `formatMoney` (es-ES, EUR, grouping always). Everything is EUR; there
 * is no per-account currency. A `DecimalFormat` is built per call because it is not thread-safe,
 * and the pattern is spelled out because a locale's own currency format drops the grouping
 * separator on four-digit numbers.
 */
fun formatMoney(amount: Double): String {
    val format = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("es", "ES")))
    return format.format(amount) + "\u00A0€"
}

/** Amounts arrive as decimal strings; one that will not parse is shown as it came. */
fun formatMoney(amount: String): String {
    val n = amount.toDoubleOrNull() ?: return amount
    return formatMoney(n)
}

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

enum class AmountSign { POS, NEG, NEU }

fun amountSign(type: String): AmountSign = when (type) {
    "income" -> AmountSign.POS
    "expense" -> AmountSign.NEG
    else -> AmountSign.NEU
}

/** [id] and every category below it, so a parent picker cannot offer itself or a descendant. */
fun collectDescendantIds(categories: List<Category>, id: Int): Set<Int> {
    val banned = mutableSetOf(id)
    var added = true
    while (added) {
        added = false
        for (c in categories) {
            val parent = c.parent_id
            if (parent != null && parent in banned && c.id !in banned) {
                banned.add(c.id)
                added = true
            }
        }
    }
    return banned
}

/**
 * The rows that show given which branches are open: a row is visible when every ancestor is in
 * [expanded]. Branches start closed, so this is what the tree looks like before anything is tapped.
 */
fun visibleCategoryRows(rows: List<CategoryTreeRow>, expanded: Set<Int>): List<CategoryTreeRow> {
    val out = mutableListOf<CategoryTreeRow>()
    var hideBelowDepth: Int? = null
    for (row in rows) {
        val hidden = hideBelowDepth
        if (hidden != null && row.depth > hidden) continue
        hideBelowDepth = null
        out.add(row)
        if (row.hasChildren && row.category.id !in expanded) hideBelowDepth = row.depth
    }
    return out
}
