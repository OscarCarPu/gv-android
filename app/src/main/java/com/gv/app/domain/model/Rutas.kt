package com.gv.app.domain.model

/**
 * A "concello" (Galician municipality) the user has marked as visited.
 * Mirrors the gv-api rutas domain. [visited_on] is an ISO date (YYYY-MM-DD).
 */
data class ConcelloMark(
    val id: Int,
    val name: String,
    val visited_on: String,
    val description: String,
)

data class CreateMarkRequest(
    val name: String,
    val visited_on: String,
    val description: String,
)

data class UpdateMarkRequest(
    val visited_on: String,
    val description: String,
)
