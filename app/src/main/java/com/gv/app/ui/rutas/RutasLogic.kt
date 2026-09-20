package com.gv.app.ui.rutas

import com.gv.app.data.local.db.ConcelloMarkEntity

/**
 * The Routes page's counting and listing, as gv-web's `+page.svelte` does it.
 */

/** How many of the concellos in view have been visited, out of how many. */
data class Progress(val visited: Int, val total: Int)

/**
 * Progress over the province in view, or over all of Galicia with no filter. The counter follows
 * the filter, so picking a province answers "how many of *these* have I done".
 */
fun progressIn(concellos: List<Concello>, marks: Map<String, ConcelloMarkEntity>, province: String?): Progress {
    val inView = if (province == null) concellos else concellos.filter { it.province == province }
    return Progress(visited = inView.count { it.name in marks }, total = inView.size)
}

/** A visit as the list under the map shows it. */
data class VisitedItem(val name: String, val province: String, val date: String, val description: String)

/**
 * Every visited concello, oldest visit first — the web's order, and unaffected by the province
 * filter (the filter dims the map; the list is your whole history). A mark whose name matches no
 * concello in the geometry is left out rather than shown unopenable, as on the web.
 */
fun visitedItems(concellos: List<Concello>, marks: Map<String, ConcelloMarkEntity>): List<VisitedItem> {
    val byName = concellos.associateBy { it.name }
    return marks.values
        .mapNotNull { m ->
            val concello = byName[m.name] ?: return@mapNotNull null
            VisitedItem(m.name, concello.province, m.visitedOn.take(10), m.description)
        }
        .sortedWith(compareBy({ it.date }, { it.name }))
}
