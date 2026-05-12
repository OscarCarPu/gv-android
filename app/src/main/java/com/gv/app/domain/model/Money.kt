package com.gv.app.domain.model

data class Account(
    val id: Int,
    val name: String,
    val total: String,
    val created_at: String,
)

data class Category(
    val id: Int,
    val name: String,
    val parent_id: Int?,
    val type: String,
    val created_at: String,
)

data class Transaction(
    val id: Int,
    val type: String,
    val amount: String,
    val account_id: Int,
    val to_account_id: Int?,
    val category_id: Int?,
    val description: String?,
    val occurred_at: String,
    val created_at: String,
)

data class OverviewTransaction(
    val id: Int,
    val type: String,
    val amount: String,
    val account_name: String,
    val to_account_name: String?,
    val category_name: String?,
    val description: String?,
    val occurred_at: String,
)

data class OverviewMonth(
    val income: String,
    val expense: String,
    val balance: String,
)

data class Overview(
    val accounts_total: String,
    val month: OverviewMonth,
    val previous_month: OverviewMonth,
    val recent_transactions: List<OverviewTransaction>,
)

data class CreateAccountRequest(val name: String)
data class UpdateAccountRequest(val name: String)

data class CreateCategoryRequest(
    val name: String,
    val parent_id: Int?,
    val type: String,
)

data class UpdateCategoryRequest(
    val name: String,
    val parent_id: Int?,
    val type: String,
)

data class CreateTransactionRequest(
    val type: String,
    val amount: String,
    val account_id: Int,
    val to_account_id: Int?,
    val category_id: Int?,
    val description: String?,
    val occurred_at: String?,
)

data class UpdateTransactionRequest(
    val type: String,
    val amount: String,
    val account_id: Int,
    val to_account_id: Int?,
    val category_id: Int?,
    val description: String?,
    val occurred_at: String,
)
