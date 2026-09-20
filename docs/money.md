# Money (Finance)

Source: `app/src/main/java/com/gv/app/ui/money/`, `domain/model/Money.kt`, `data/repository/MoneyRepository.kt`, `data/api/ApiService.kt`

Client for the `gv-api` `/finance/*` endpoints, following gv-web's `/money` page: the six summary tiles, the recent transactions (or one account's whole history), and accounts and categories with create / edit / delete. The stats sheets and charts from the web (net worth, breakdown, monthly trend, estimation) are deliberately not ported.

The feature is reached via the **Finance** tab in `HomeScreen`'s bottom navigation.

Like the rest of the app it is **online-first, offline read-only** — with one difference: **it keeps no cache.** Balances are what you decide things by, and a stale one is worse than none, so with no connection the screen says it cannot load rather than show last week's total. Every write goes to the server and is refused offline (`OnlineGate`).

---

## Layout

Three swipeable pages under a tab bar — **Overview**, **Accounts**, **Categories** — each with the web's header button (**+ Transaction**, **New**) rather than a floating one.

### Overview (`OverviewTab.kt`)

- **Summary** — six tiles in the web's order and words: Total accounts, Monthly income, Monthly expenses, Monthly balance, Savings, % vs prev month. **+ Transaction** is disabled until there is an account to put one on.
- **Transactions** — the last 30 days, grouped by day (`Today` / `Yesterday` / `EEE, d MMM`), or, with an account picked in the filter beside the title, **that account's whole history** (`GET /finance/transactions?account_id=`). The list folds at **15** and "N more" unfolds 10 at a time.
- A row shows a type badge (In / Out / Tx), the description or category, `source → destination` for a transfer, and the signed amount. **Tapping a row** fetches the full `Transaction` (`GET /finance/transactions/{id}`) and opens the edit sheet — the overview rows are name-based and lack the ids the form needs. The trash icon deletes at once.

### Accounts (`AccountsTab.kt`)

Name and total (red when negative), edit, delete. Deleting an account that has transactions is refused by the API (`409`) and reported as "Account has associated transactions".

### Categories (`CategoriesTab.kt`)

Grouped Income / Expenses / Transfers, each a tree that **starts collapsed**, as on the web: a chevron opens a branch, and `visibleCategoryRows` shows a row only when every ancestor is open. Deleting a category still in use is refused (`409`) and reported as "Category is in use".

---

## Deletes are immediate

As on the web: no confirmation dialog. This is safe for accounts and categories because the API refuses to delete one that is in use, so only empty ones can go. A transaction delete *is* final, and the trash icon sits on every row — but re-entering a transaction is cheap and the account total (kept by a database trigger) corrects itself either way. Habits keep a confirmation, because deleting one takes its whole history with it.

---

## Money rules that are easy to get wrong (`MoneyLogic.kt`, `MoneyUtils.kt`)

All pure, ported from gv-web, unit-tested (`MoneyLogicTest`).

- **Amounts are strings**, `NUMERIC(15,2)` on the wire. They are parsed only at display or arithmetic, and sent back as a two-decimal string (`Locale.ROOT`, `"%.2f"`) so the JSON never contains a comma.
- **Display is es-ES**: `1.234,56 €`, grouping always — spelled out as a `DecimalFormat` pattern because a locale's own currency format drops the separator on four digits. Percentages use a dot whatever the phone's locale.
- **A decimal comma is a decimal point** in the amount field: the decimal keypad on a Spanish-locale phone types one, and `"12,5".toDouble()` is not a number.
- **`occurred_at` is a wall-clock stamp, not an instant.** A transaction entered at 10:00 is stored `10:00Z` and read back by taking the digits (`wallClockToIso` / `isoToWallClock`) — the web's `toISOString` / `toLocalDatetime`, and the same idea as a task's `due_at`. Converting it to or from the phone's zone would put a purchase made at 00:30 on the previous day (the list groups by the first ten characters) and show a web-created row two hours out.
- **The KPIs** (`deriveOverviewKpis`): savings is `balance / income` (0 with no income); the change against last month is divided by `|previous balance|`, not the balance itself, so going from −100 to −50 reads as an improvement.
- **The transaction form** (`checkTransactionForm`): a positive amount, a source account, a category for every type (whose own type must match — the option list enforces it), and for a transfer a destination different from the source.
- **Delete conflicts** (`deleteFailureMessage`): a `409` whose message names `transactions` (account) or `referenced` (category) is "in use", not a generic error; offline says so.

---

## Files

| File | Responsibility |
|------|---------------|
| `domain/model/Money.kt` | DTOs: `Account`, `Category`, `Transaction`, `OverviewTransaction`, `Overview`, and `Create*` / `Update*` bodies. snake_case via Gson defaults. |
| `data/repository/MoneyRepository.kt` | Live reads (overview, accounts, categories in parallel; an account's history) and every write, gated by `OnlineGate`, returning `ApiResult`. |
| `ui/money/MoneyViewModel.kt` | `state` (Loading / Loaded / Error), `transactions` (the folded list), and every action. Re-reads everything after a write — totals are trigger-maintained. Failures come back on `toast`. |
| `ui/money/MoneyScreen.kt` | The shell: tab bar, pager, sheets. |
| `OverviewTab.kt`, `AccountsTab.kt`, `CategoriesTab.kt`, `MoneyRows.kt` | The pages and their rows. |
| `TransactionFormSheet.kt`, `AccountFormSheet.kt`, `CategoryFormSheet.kt`, `FormFields.kt` | The `ModalBottomSheet` forms and their fields (a sheet-based dropdown, a date field, the type chips). |
| `MoneyLogic.kt`, `MoneyUtils.kt` | The pure rules above, plus category trees and pickers. |

---

## API contract

```
GET    /finance/overview                       → Overview
GET    /finance/accounts                       → Account[]
POST   /finance/accounts        { name }
PUT    /finance/accounts/{id}   { name }
DELETE /finance/accounts/{id}                  (409 when it has transactions)
GET    /finance/categories                     → Category[]
POST   /finance/categories      { name, type, parent_id? }
PUT    /finance/categories/{id} { name, type, parent_id }
DELETE /finance/categories/{id}                (409 when in use)
GET    /finance/transactions[?account_id=]     → Transaction[]
GET    /finance/transactions/{id}              → Transaction
POST   /finance/transactions    CreateTransactionRequest
PUT    /finance/transactions/{id} UpdateTransactionRequest
DELETE /finance/transactions/{id}
```

`occurred_at` is optional on `POST` (the server defaults it) but required on `PUT`; this client always sends it. The `/finance/stats/*` endpoints exist server-side but are not wired here.
