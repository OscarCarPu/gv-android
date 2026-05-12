# Money (Finance)

Source: `app/src/main/java/com/gv/app/ui/money/`, `app/src/main/java/com/gv/app/domain/model/Money.kt`, `app/src/main/java/com/gv/app/data/api/ApiService.kt`

Read-and-write client for the `gv-api` `/finance/*` endpoints. Surfaces the monthly KPIs from the web `/money` page, plus CRUD for transactions, accounts, and categories. Charts and stats sheets from the web (net worth, breakdown, monthly trend, estimation) are intentionally out of scope on Android.

The feature is reached via the **Finance** tab in `HomeScreen`'s bottom navigation.

---

## Components

| File | Responsibility |
|------|---------------|
| `domain/model/Money.kt` | DTOs: `Account`, `Category`, `Transaction`, `OverviewTransaction`, `OverviewMonth`, `Overview`, plus `Create*` / `Update*` request bodies. snake_case to match the API JSON via Gson defaults. |
| `data/api/ApiService.kt` | Retrofit endpoints under `/finance/`: `overview`, `accounts` CRUD, `categories` CRUD, `transactions` CRUD + GET-by-id. |
| `ui/money/MoneyViewModel.kt` | `state: StateFlow<MoneyUiState>` (Loading / Loaded(MoneyData) / Error). Loads overview + accounts + categories in parallel on init and after every successful mutation. Emits `toast: SharedFlow<String>` for inline errors. |
| `ui/money/MoneyScreen.kt` | Single-screen entry. Three sub-tabs (Overview / Accounts / Categories) and a context-aware FAB that opens the relevant create sheet. |
| `ui/money/FormSheets.kt` | `ModalBottomSheet`s: `TransactionFormSheet`, `AccountFormSheet`, `CategoryFormSheet`. Shared `DropdownField` (sheet-based picker), `DateField` (Material3 `DatePicker`), `TypeSelector` (Income / Expense / Transfer chips), and `GvTextField`. |
| `ui/money/MoneyUtils.kt` | `formatMoney` (locale-pinned currency formatting), `buildCategoryOptions` (flat indented list for dropdowns), `buildCategoryTreeRows` (tree rows with connector metadata for the Categories tab), plus type-label and amount-sign helpers. |

---

## Sub-tabs

`MoneyScreen` renders a top tab bar (`OVERVIEW` / `ACCOUNTS` / `CATEGORIES`, persisted via `rememberSaveable`) and the FAB rewires to that tab's create action:

| Tab | FAB action |
|-----|-----------|
| Overview | New transaction (with an `AlertDialog` fallback if no accounts exist yet, offering to create one) |
| Accounts | New account |
| Categories | New category |

### Overview

Six KPI tiles arranged in a 2-column grid:

| Tile | Source |
|------|--------|
| Total accounts | `overview.accounts_total` |
| Balance (month) | `overview.month.balance`, signed and tinted (green/red) |
| Income (month) | `overview.month.income`, prefixed `+`, green |
| Expense (month) | `overview.month.expense`, prefixed `−`, red |
| Savings rate | `balance / income * 100`, one decimal, signed-tinted |
| vs previous | `(balance − prev_balance) / |prev_balance| * 100`, or `—` if previous month had a zero balance |

Below the KPIs, `overview.recent_transactions` (last 30 days) is rendered as a list with day dividers (`Today` / `Yesterday` / `EEE, d MMM`) when the `occurred_at` date changes. Each row has a colored type badge (In / Out / Tx), the description-or-category as the primary label, the account chain (`source → destination` for transfers), the signed amount, and a delete button. Tapping the row fetches the full `Transaction` via `GET /finance/transactions/{id}` and opens the edit sheet — `OverviewTransaction` lacks the foreign key ids, so the round-trip is required to pre-fill the form correctly.

### Accounts

Plain list of `Account` cards: name + total (tinted red if negative) + edit + delete. No filters.

### Categories

Visually-clear tree, grouped by transaction type (Income / Expense / Transfer), each group preceded by a colored accent header showing the count.

`buildCategoryTreeRows(categories)` produces `CategoryTreeRow(category, depth, ancestorHasMore, isLast, hasChildren)` entries via DFS, sorted alphabetically per level. `CategoryTreeNode` renders each row with:

- A `Canvas`-drawn connector column on the left: vertical guides for every ancestor that still has more siblings below this row, plus an `├` / `└` branch at this row's column. Lines are tinted with the group's accent color.
- An expand/collapse chevron for parents (leaves render a placeholder of the same width so columns stay aligned). Collapsed parent ids are stored in a `rememberSaveable` `Set<Int>` that's filtered out by `filterCollapsed` before display.
- Depth-aware typography: root rows are semibold and full-opacity; children are smaller and slightly muted.

---

## Form sheets

All three sheets are `ModalBottomSheet`s opening from the FAB or from a row tap/edit-icon. They share `GvTextField` and `DropdownField` (which itself opens a nested `ModalBottomSheet` rather than a Material dropdown, to stay tappable on small screens).

### TransactionFormSheet

| Field | Notes |
|-------|-------|
| Type | `Income` / `Expense` / `Transfer` chip selector. Changing type resets `categoryId` if the new type has no match, and clears `toAccountId` when leaving `Transfer`. |
| Amount | Decimal keyboard. Parsed via `String.toDoubleOrNull` (so the user must type `.` as the decimal separator). Sent to the API formatted with `Locale.ROOT` `"%.2f"` so the JSON never contains a comma decimal — see the locale section below. |
| Date | `DatePicker` (Material3). Time component is preserved from `LocalDateTime.now()` for new transactions or from the original `occurred_at` for edits. Serialized as `yyyy-MM-dd'T'HH:mm:ss'Z'` in UTC. |
| From account | Required. Defaults to the first account. |
| To account | Only shown when type = `Transfer`. Filtered to exclude the selected source. |
| Category | Required. Options are filtered to categories whose `type` matches the selected transaction type. |
| Description | Optional. Trimmed; empty → `null`. |

Validation happens client-side before submit: `amount > 0`, `account_id != null`, `category_id != null`, and for transfers `to_account_id != null && to_account_id != account_id`. Server-side validation in `gv-api` repeats the same checks plus additional invariants (no `to_account_id` on income/expense, etc.).

### AccountFormSheet

Single `name` field, max 40 chars. The backend rejects empty / overlong names with a 400.

### CategoryFormSheet

`name` (≤40) + `type` chip selector + optional `parent_id` dropdown. When editing, the parent options exclude the category itself and its entire descendant subtree (computed by walking `categories` until no more descendants are added) — this prevents creating a cycle. Changing `type` filters parent candidates to the same type.

---

## Mutations

All mutations go through `MoneyViewModel`, which calls the API and then `refresh()` on success (re-pulling overview + accounts + categories in parallel). No optimistic UI: server-side balances and tree counts are the source of truth, and a refresh is fast enough that the UI feels responsive.

```
saveTransaction(id?, req)    POST /finance/transactions     or PUT /finance/transactions/{id}
deleteTransaction(id)        DELETE /finance/transactions/{id}
saveAccount(id?, name)       POST /finance/accounts         or PUT /finance/accounts/{id}
deleteAccount(id)            DELETE /finance/accounts/{id}      (fails 409 if account has transactions)
saveCategory(id?, name, …)   POST /finance/categories       or PUT /finance/categories/{id}
deleteCategory(id)           DELETE /finance/categories/{id}    (fails 409 if category is in use)
```

Delete confirmations are gated by an `AlertDialog` with a red `Delete` button. On failure, the `toast` `SharedFlow` surfaces a snackbar with the cause (`"may have transactions"` / `"may be in use"`).

---

## Locale handling

The API speaks JSON with `.` as the decimal separator (shopspring/decimal serializes to a quoted string like `"10.00"`). Spanish-locale devices, however, return `","` from `"%.2f".format(value)` because Kotlin's `String.format` defaults to the device locale — so request bodies must pin the format locale:

```kotlin
String.format(Locale.ROOT, "%.2f", amt)   // request payload
```

Display formatting is pinned to `Locale.UK` (English number style with `€` currency) in `MoneyUtils.formatMoney` and in the percentage/date helpers in `MoneyScreen`:

| Item | Format |
|------|--------|
| Money | `€10,000.50` (UK locale, EUR currency) |
| Percent | `12.3%` (UK locale, one decimal) |
| Day label | `Tue, 5 Mar` / `Today` / `Yesterday` (UK locale) |
| ISO date sent to API | `2026-05-12T13:45:30Z` (`Locale.ROOT`) |

Mixing display locale (UK) with payload locale (ROOT) is intentional: display is for the user, payload is for a machine.

---

## API contract

Endpoints under `/finance/*` are authenticated (Bearer token, injected by the OkHttp interceptor in `RetrofitClient`). All amounts cross the wire as JSON strings via shopspring/decimal serialization; on Android they're modeled as Kotlin `String` and parsed to `Double` only for arithmetic / display.

```
GET    /finance/overview                       → Overview
GET    /finance/accounts                       → Account[]
POST   /finance/accounts        { name }       → Account
PUT    /finance/accounts/{id}   { name }       → Account
DELETE /finance/accounts/{id}                  → 204
GET    /finance/categories                     → Category[]
POST   /finance/categories      { name, type, parent_id? }
PUT    /finance/categories/{id} { name, type, parent_id }
DELETE /finance/categories/{id}
GET    /finance/transactions                   → Transaction[]
GET    /finance/transactions/{id}              → Transaction
POST   /finance/transactions    CreateTransactionRequest
PUT    /finance/transactions/{id} UpdateTransactionRequest
DELETE /finance/transactions/{id}
```

Stats endpoints (`/finance/stats/*`) exist server-side but are not wired on Android — the corresponding sheets and charts only live in `gv-web`.

---

## Future work

- **Account-scoped transaction list**: `ApiService.listTransactions(accountId?)` is already plumbed but no UI uses it; the Accounts tab could show per-account history.
- **Stats sheets**: net worth / category breakdown / monthly trend / estimation — port from `gv-web` when there's a clear mobile use case.
- **Optimistic mutations**: not currently used because re-fetching is cheap; reconsider if latency to the API gets high.
