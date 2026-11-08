# OrderWaymark — One Box or Two?

A small Java 21 + Spring Boot + Maven project. An order has one keyboard, one mouse and a requested
delivery date. OrderWaymark picks the **cheapest plan that gets both items there on time**. Each plan
ships from Montréal, Ottawa or both, with one box per warehouse used.

## Run it

```bash
mvn spring-boot:run
```

Then open <http://localhost:8080/>. Pick a scenario to see each strategy's assignments, price and
estimated arrivals, why the winning plan was chosen, and why any plans were rejected.

Other commands:

```bash
mvn test                                    # run the test suite
mvn package && java -jar target/orderwaymark.jar
java -jar target/orderwaymark.jar benchmark # optional Olist benchmark (see below)
```

## React frontend (`frontend/`)

A one-page React + Vite app (JavaScript, plain CSS). It has a scenario picker, an editable Ottawa
combined-box quote and a Recalculate button. It shows the recommended plan, its box count, total
cost and explanation, and compares it with cheapest per item and fewest boxes first. The Vite dev
server forwards `/api/*` to Spring Boot on port 8080 (see `frontend/vite.config.js`).

Prerequisites: JDK 21, Maven, and Node.js 18 or later with npm.

### Start both parts from IntelliJ

Open the project root (`OrderWaymark/`) in IntelliJ IDEA, then open two terminal tabs
(**View → Tool Windows → Terminal**, then **+**).

**Tab 1, the Spring Boot API on port 8080:**

```bash
mvn spring-boot:run
```

You can also run `com.orderwaymark.OrderWaymarkApplication` with the green ▶ gutter icon.

**Tab 2, the React app on port 5173.** Install dependencies once:

```bash
cd frontend
npm install
```

Then start the dev server:

```bash
npm run dev
```

You can also open `frontend/package.json` and click ▶ next to the `dev` script.

**Open <http://localhost:5173/> in your browser.** Start Spring Boot first. If it isn't running,
the page shows an error banner instead of results.

Production build check:

```bash
npm --prefix frontend run build
```

### Expected behaviour

| Scenario | Ottawa combined quote | Recommendation |
|---|---:|---|
| 1. One box wins | $8.00 (default) | **Ottawa, 1 box, $8.00** |
| 1. One box wins | $11.00 | Ottawa, 1 box, $11.00 (a price tie goes to fewer boxes) |
| 1. One box wins | $11.01 | **Montréal keyboard + Ottawa mouse, 2 boxes, $11.00** |
| 4b. Nothing arrives on time | any | **No on-time plan**, with the reason each plan was rejected |

The quote field accepts dollars with up to two decimal places, from $0.00 to $1,000.00. Anything
else shows an inline error and makes no request.

## How the planner works

The planner is `com.orderwaymark.planner.Planner`. It is plain Java with no Spring dependency.

1. **Enumerate** every way to assign items to warehouses: 2 items × 2 warehouses = 4 assignments.
2. **Group** the items by warehouse into one box each, then **price** each box from the quote for
   those exact contents. A combined box has its own quote and is not the sum of the single-item quotes.
3. **Reject** a plan if a warehouse lacks stock, a box has no quote, or **any** box arrives after
   the deadline. Arriving on the deadline day counts as on time.
4. **Rank** the valid plans by **effective cost** in integer cents, then by fewer boxes, then by a
   stable tie-break (the earlier-listed warehouse wins, Montréal before Ottawa). Effective cost is
   today's shipping plus the expected future cost of the stock the plan uses up (see below). With no
   stock forecast, that future cost is zero and effective cost is just shipping.
5. If no plan is valid, return `NO_ON_TIME_PLAN`.

### Inventory-aware sourcing

The cheapest box today can be the expensive choice overall. If Ottawa ships its last keyboard to
this customer, the next Ottawa-area customer who wants one has to be served from Montréal at a
higher price. This follows Acimovic & Graves (2015), *Making Better Fulfillment Decisions on the
Fly in an Online Retail Environment*.

An `InventoryForecast` gives each (warehouse, sku) two numbers:

- **λ**: expected demand from that warehouse's own customers before its next restock (Poisson).
- **Δ**: how much more it costs to serve one of those customers from the next-best warehouse.

Taking one unit when `q` are on hand causes a future shortfall exactly when demand D ≥ q, so its
expected cost is `P(D ≥ q) × Δ`. Taking k units sums this for q, q−1, …, q−k+1
(`InventoryForecast.expectedCostCents`). Plentiful stock costs almost nothing, and the last unit
costs the most.

The planner adds this to each plan's shipping and explains any trade-off it makes. A fourth
baseline, **Cheapest today**, ranks by shipping alone so you can see what the forecast changed.

Three baseline strategies are shown alongside it:

| Strategy | Rule |
|---|---|
| **Cheapest per item** | For each item, pick the in-stock warehouse with the cheapest single-item quote. Then group, price and validate the resulting boxes. If that plan fails validation, it is shown as `REJECTED`. |
| **Fewest boxes first** | Among valid plans, keep those with the fewest boxes and pick the cheapest of them. |
| **Cheapest today** | The cheapest valid plan by today's shipping price, ignoring the stock it uses up. Without a forecast it is identical to the Waymark planner. |

### API

The project has one JSON endpoint, `POST /api/plan`. You can send a preset scenario:

```json
{ "scenario": "two-boxes-win" }
```

Or you can send a custom order:

```json
{
  "deadline": "2026-11-13",
  "items": ["keyboard", "mouse"],
  "warehouses": [{ "id": "MTL", "name": "Montréal", "stock": { "keyboard": 1, "mouse": 1 } }],
  "quotes": [{ "warehouse": "MTL", "items": ["keyboard", "mouse"], "priceCents": 1200, "arrives": "2026-11-13" }]
}
```

You can add `"ottawaCombinedQuoteCents": 1101` to a scenario request. It replaces the Ottawa
keyboard + mouse box price for that request, and the box keeps the scenario's arrival date. The value
must be a whole number of cents from 0 to 100000. It is rejected on custom orders, which carry their
own `quotes`. The response echoes the effective value in `ottawaCombinedQuoteCents`.

The response contains `status` (`PLANNED` or `NO_ON_TIME_PLAN`), `winner`, `explanation`, every
evaluated plan in `candidates` (each with its `rejections`), and the three `strategies`. An invalid
request gets HTTP 400 with `{"error": "..."}`.

## Seeded demo (synthetic data)

Both warehouses have one keyboard and one mouse in stock. The deadline is **Fri 2026-11-13**, and
every box arrives that Friday unless a scenario says otherwise.

| Warehouse | Keyboard only | Mouse only | Both in one box |
|---|---:|---:|---:|
| Montréal | $5 | $8 | $12 |
| Ottawa | $7 | $6 | $8 |

### Expected demo results

| Scenario | Change from seed | Waymark planner | Cheapest per item | Fewest boxes first |
|---|---|---|---|---|
| 1. One box wins | none | **Ottawa, 1 box, $8** | Montréal kb + Ottawa mouse, $11 | Ottawa, $8 |
| 2. Two boxes win | Ottawa combined → $14 | **Split, $11** | Split, $11 | Montréal, $12 |
| 3. Stock changes the answer | Ottawa keyboard stock → 0 | **Split, $11** (both Ottawa-keyboard plans rejected) | Split, $11 | Montréal, $12 |
| 4a. One late box | As 2, plus Ottawa mouse-only arrives Sat | **Montréal, $12** (the $11 split is late) | `REJECTED` (late) | Montréal, $12 |
| 4b. Nothing arrives on time | Every box arrives Sat | **`NO_ON_TIME_PLAN`** | `REJECTED` (late) | `NO_ON_TIME_PLAN` |
| 5. Price tie | Ottawa combined → $11 | **Ottawa, 1 box, $11** (beats the $11 split on fewer boxes) | Split, $11 | Ottawa, $11 |
| 6. Ottawa's last keyboard | Seed prices; Ottawa's keyboard is its last before restock (λ = 1.2, Δ = $6) | **Split, $11** ($11.00 + $0 future, versus Ottawa's $8.00 + $4.19 = $12.19) | Split, $11 | Ottawa, $8 (+$4.19 future) |

In scenario 6, **Cheapest today** picks Ottawa's $8 box. The Waymark planner pays $3 more today to
avoid an expected $4.19 later. Set the Ottawa quote to **$6.81** or less and Ottawa wins again:
$6.81 + $4.19 = $11.00 ties the split and wins on fewer boxes.

## Optional: Olist benchmark

This mode runs the three strategies over up to 100 real **two-item** baskets from the public
[Brazilian E-Commerce Public Dataset by Olist](https://www.kaggle.com/datasets/olistbr/brazilian-ecommerce).

1. Download the dataset and put these files in `data/olist/`:
   - `olist_orders_dataset.csv`
   - `olist_order_items_dataset.csv`
   - `olist_products_dataset.csv`
2. Run `mvn package -DskipTests && java -jar target/orderwaymark.jar benchmark`.
   You can pass an optional directory and limit: `... benchmark path/to/olist 50`.

`/data/olist/` is listed in `.gitignore`. The web demo and the tests don't need the download, because
the importer tests use tiny synthetic CSVs.

**Real data:** the basket contents (product ids, categories and weights), the purchase date, and
the historical `order_estimated_delivery_date`. That date is used as the **deadline proxy**.

**Simulated data:** Montréal/Ottawa warehouse stock, alternative shipping quotes and delivery
estimates. These values are derived deterministically from hashes of the order and product ids, so
repeated runs give the same output.

How the importer joins the files: it groups `order_items` by `order_id`, keeps orders with exactly two
item rows, inner-joins `orders` (both dates required) and `products`, sorts by `order_id`, and takes
the first N orders.

## Stream benchmark: does inventory-aware sourcing pay off?

A single order can't show the benefit, so this benchmark replays **a quarter of real Olist orders
in time order** against shared stock that runs down and restocks. Every policy sees the same
orders. Units shipped leave stock, and stock resets to plan every 7 days.

```bash
mvn package -DskipTests
java -jar target/orderwaymark.jar benchmark stream
```

It needs the Olist `orders`, `order_items`, `products`, `customers`, `sellers` and `geolocation`
CSVs in `data/olist/`. It takes about 10 seconds.

| Data | Source |
|---|---|
| Order stream: timestamps, baskets, customer postal codes, deadline (estimated delivery date) | **Real**: Jan–Mar 2018, 20,812 orders |
| Hub locations: mean location of sellers in SP, MG and PR | **Real** |
| Box price = R$8.43 + R$0.0095/km + R$2.41/kg | **Fitted** by least squares on 39,916 real 2017 single-item orders |
| Transit days = 9.2 + 0.0061/km | **Fitted** on 42,727 real 2017 delivered orders |
| Stock per hub × category (top 12 categories + other), weekly restock to plan | **Simulated** |
| Forecast λ and Δ for the inventory-aware policy | Last 90 days of **2017 only**, so no look-ahead |

### Results (Jan–Mar 2018)

| Stock coverage | Inventory-aware vs cheapest today | Units shipped from a non-nearest hub | Late orders |
|---|---|---|---|
| 0.8× planned demand | saves R$145 (0.06%) | 5.8% → 8.3% | 65 → 65 |
| 1.0× | saves R$174 (0.06%) | 3.9% → 6.4% | 65 → 61 |
| 1.2× | **costs** R$49 (0.02%) | 1.4% → 3.4% | 61 → 58 |

**What this shows:** on this network the gain is real but tiny, and it turns negative when stock is
plentiful. The three hubs are close together, so serving a customer from the next hub costs only a
few reais more. The forecast also over-protects thin categories at small hubs.

Scaling the per-km price ×5 or ×20 changes no decisions. Both sides of the trade-off scale with
distance, so the network's shape matters, not the price level.

The planner is correct, as the synthetic replay tests show it saving the scarce unit. Bigger gains
would need hubs farther apart, tighter stock on fast-moving items, or forecasts that account for
the knock-on effect on the substitute hub (Acimovic & Graves' full method).

## Tests

| # | Requirement | Test |
|---|---|---|
| 1 | One box wins ($8 vs $11) | `PlannerTest.OneBoxWins` |
| 2 | Two boxes win ($11 split; fewest-boxes picks Montréal $12) | `PlannerTest.TwoBoxesWin` |
| 3 | Stock changes the answer | `PlannerTest.StockChanges` |
| 4 | Whole order on time: Friday qualifies, one late box disqualifies, all late → `NO_ON_TIME_PLAN` | `PlannerTest.OnTime` |
| 5 | Deterministic ties (fewer boxes, stable tie-break, repeated runs) | `PlannerTest.Ties` |
| 6 | API: valid, custom, no-plan, invalid requests; Ottawa quote override and its validation; page served | `PlanControllerTest` |
| 6 | Olist joins on tiny synthetic CSVs, limit, benchmark determinism, missing data | `OlistImporterTest` |
| 7 | Poisson math, last-keyboard scenario, break-even price, no forecast → unchanged behaviour | `InventoryForecastTest` |
| 8 | Replay: inventory-aware saves the scarce unit, stock depletes and restocks, late fallback, determinism | `ReplayTest` |
| 9 | Stream world: joins all six Olist files on tiny CSVs, skips bad orders, fits, least squares, distance | `OlistStreamWorldTest` |
