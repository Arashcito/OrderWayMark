import { useCallback, useEffect, useRef, useState } from 'react';
import { day, fetchPlan, money, parseDollars } from './api.js';
import { BoxIcon, HeroScene, HouseIcon, Sparkle, TruckIcon, WarehouseIcon } from './Illustrations.jsx';
import { describeAssignment, pickBefore, priceList, reasonLabel, SHORTCUTS, STORY, verdict } from './story.js';

const DEFAULT_SCENARIO = 'one-box-wins';
const STEPS = [
  ['what', 'What is this?'],
  ['problem', 'The problem'],
  ['scenario', 'Scenario'],
  ['before-after', 'Before & after'],
  ['how', 'How it works'],
  ['results', 'Results'],
];

export default function App() {
  const [scenarios, setScenarios] = useState([]);
  const [scenario, setScenario] = useState(DEFAULT_SCENARIO);
  const [quoteText, setQuoteText] = useState('');
  const [defaultQuote, setDefaultQuote] = useState(null);
  const [inputError, setInputError] = useState('');
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [requestError, setRequestError] = useState('');
  const latest = useRef(0);

  const load = useCallback(async (scenarioId, overrideCents) => {
    const id = ++latest.current;
    setLoading(true);
    setRequestError('');
    try {
      const d = await fetchPlan({ scenario: scenarioId, ottawaCombinedQuoteCents: overrideCents });
      if (id !== latest.current) return;
      setData(d);
      setScenarios(d.scenarios);
      if (overrideCents == null) {
        setDefaultQuote(d.ottawaCombinedQuoteCents);
        setQuoteText((d.ottawaCombinedQuoteCents / 100).toFixed(2));
      }
    } catch (e) {
      if (id === latest.current) setRequestError(e.message);
    } finally {
      if (id === latest.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    load(DEFAULT_SCENARIO);
  }, [load]);

  function choose(id) {
    setScenario(id);
    setInputError('');
    load(id);
  }

  function recalculate(e) {
    e.preventDefault();
    const parsed = parseDollars(quoteText);
    if (parsed.error) {
      setInputError(parsed.error);
      return;
    }
    setInputError('');
    load(scenario, parsed.cents);
  }

  function resetQuote() {
    setInputError('');
    load(scenario);
  }

  const story = STORY[scenario] || {};
  const before = data ? pickBefore(data.strategies) : null;
  const overridden = data?.ottawaCombinedQuoteOverridden;

  return (
    <>
      <nav className="topbar" aria-label="Story steps">
        <a href="#what" className="brand">
          <BoxIcon size={26} /> OrderWaymark
        </a>
        <ol className="steps">
          {STEPS.map(([id, label], i) => (
            <li key={id}>
              <a href={`#${id}`}>
                <span className="step-num">{i + 1}</span>
                <span className="step-label">{label}</span>
              </a>
            </li>
          ))}
        </ol>
      </nav>

      <main>
        {/* 1. WHAT ----------------------------------------------------------- */}
        <section id="what" className="hero">
          <div className="hero-copy">
            <p className="kicker">A tiny shipping puzzle</p>
            <h1>One box or two?</h1>
            <p className="lead">
              You order a <b>keyboard</b> and a <b>mouse</b>. The store has two warehouses, in <b>Montréal</b> and{' '}
              <b>Ottawa</b>. Which one should ship which item?
            </p>
            <p className="lead">
              OrderWaymark picks the <b>cheapest way that still gets everything to you on time</b>, and explains why.
            </p>
            <ol className="mini-flow">
              <li>
                <span className="dot">1</span>You place an order
              </li>
              <li>
                <span className="dot">2</span>We check every way to ship it
              </li>
              <li>
                <span className="dot">3</span>You get the best plan
              </li>
            </ol>
            <a className="btn primary" href="#problem">
              Start the demo ↓
            </a>
          </div>
          <HeroScene />
        </section>

        {/* 2. PROBLEM -------------------------------------------------------- */}
        <section id="problem" className="section">
          <SectionHead n={2} title="The problem: it's trickier than it looks">
            Shipping two things sounds easy. But four things pull in different directions.
          </SectionHead>
          <div className="problem-grid">
            <ProblemCard emoji="💰" title="Packing changes the price">
              One box with both items can be cheaper than two boxes, or more expensive. It depends on the warehouse.
            </ProblemCard>
            <ProblemCard emoji="📭" title="Stock runs out">
              A warehouse can't ship what it doesn't have, even if it's the cheapest.
            </ProblemCard>
            <ProblemCard emoji="⏰" title="Everything must be on time">
              If one box is late, the whole order is late. A cheap plan that arrives Saturday doesn't count.
            </ProblemCard>
            <ProblemCard emoji="🔮" title="Today affects tomorrow">
              Using a warehouse's last item today can make the next customer's order more expensive.
            </ProblemCard>
          </div>
          <div className="callout">
            <TruckIcon size={52} />
            <p>
              Most stores use a <b>shortcut rule</b>, like "grab each item wherever it's cheapest". Shortcuts are fast,
              but they miss things. <b>OrderWaymark checks every option.</b> Let's compare them.
            </p>
          </div>
        </section>

        {/* 3. SCENARIO ------------------------------------------------------- */}
        <section id="scenario" className="section">
          <SectionHead n={3} title="Pick a scenario">
            Each one changes a single thing about the store. Watch how the best plan changes with it.
          </SectionHead>

          <div className="chips" role="radiogroup" aria-label="Scenario">
            {(scenarios.length ? scenarios : [{ id: DEFAULT_SCENARIO, title: '' }]).map((s) => {
              const st = STORY[s.id] || {};
              const on = s.id === scenario;
              return (
                <button
                  key={s.id}
                  type="button"
                  role="radio"
                  aria-checked={on}
                  className={'chip' + (on ? ' on' : '')}
                  onClick={() => choose(s.id)}
                  disabled={!scenarios.length}
                >
                  <span className="chip-emoji" aria-hidden="true">
                    {st.emoji}
                  </span>
                  {st.name || s.title}
                </button>
              );
            })}
          </div>

          {data && (
            <div className="story-card">
              <div>
                <p className="label">What's going on</p>
                <p className="story-setup">{story.setup}</p>
                <p className="story-twist">
                  <b>The twist:</b> {story.twist}
                </p>
              </div>
              <Store data={data} />
            </div>
          )}

          <form className="play" onSubmit={recalculate} noValidate>
            <div className="field">
              <label htmlFor="ottawa-quote">Play with it: Ottawa's price for one box with both items</label>
              <div className="play-row">
                <div className={'money-input' + (inputError ? ' invalid' : '')}>
                  <span aria-hidden="true">$</span>
                  <input
                    id="ottawa-quote"
                    inputMode="decimal"
                    autoComplete="off"
                    value={quoteText}
                    onChange={(e) => setQuoteText(e.target.value)}
                    aria-invalid={inputError ? 'true' : 'false'}
                    aria-describedby={inputError ? 'quote-hint quote-error' : 'quote-hint'}
                  />
                </div>
                <button type="submit" className="btn primary" disabled={loading || !data}>
                  {loading ? 'Recalculating…' : 'Recalculate'}
                </button>
                {overridden && (
                  <button type="button" className="btn ghost" onClick={resetQuote} disabled={loading}>
                    Reset to {money(defaultQuote)}
                  </button>
                )}
              </div>
              <p id="quote-hint" className="hint">
                Try $11.01 in the first scenario: one cent can flip the answer.
                {defaultQuote != null && <> This scenario starts at {money(defaultQuote)}.</>}
              </p>
              {inputError && (
                <p id="quote-error" className="field-error" role="alert">
                  {inputError}
                </p>
              )}
            </div>
          </form>
        </section>

        {requestError && (
          <div className="banner error" role="alert">
            {requestError}
          </div>
        )}

        <div className={loading ? 'is-loading' : ''} aria-busy={loading} aria-live="polite">
          {!data && !requestError && <p className="section muted">Loading the demo…</p>}
          {data && (
            <>
              <BeforeAfter data={data} before={before} />
              <HowItDecided data={data} />
              <Results data={data} before={before} />
            </>
          )}
        </div>

        {/* 7. WRAP ----------------------------------------------------------- */}
        <section id="wrap" className="section wrap">
          <div className="wrap-head">
            <Sparkle size={30} />
            <h2>That's the demo!</h2>
            <Sparkle size={22} />
          </div>
          <ul className="takeaways">
            <li>
              <b>One box isn't always cheaper, and neither are two.</b> You have to price both.
            </li>
            <li>
              <b>A plan only counts if everything arrives on time.</b> One slow box spoils it.
            </li>
            <li>
              <b>The cheapest choice now can cost more later.</b> Scarce stock has a hidden price.
            </li>
          </ul>
          <details className="under-hood">
            <summary>Under the hood: does this work on real orders?</summary>
            <p>
              We replayed <b>20,812 real orders</b> from Olist, a Brazilian online marketplace, through three simulated
              warehouses. Saving the last item helped, but only a little (about 0.06%). The warehouses there are close
              together, so a backup warehouse is never much more expensive. That's an honest result. The idea pays off
              most when warehouses are far apart and stock is tight.
            </p>
          </details>
          <div className="wrap-actions">
            <a className="btn primary" href="#scenario">
              Try another scenario ↑
            </a>
            <a className="btn ghost" href="#what">
              Back to the top
            </a>
          </div>
          <p className="foot">
            Demo prices and stock are made up. Real calculations run on a Spring Boot planner in whole cents.
          </p>
        </section>
      </main>
    </>
  );
}

function SectionHead({ n, title, children }) {
  return (
    <header className="section-head">
      <span className="badge-num" aria-hidden="true">
        {n}
      </span>
      <div>
        <h2>{title}</h2>
        {children && <p className="sub-lead">{children}</p>}
      </div>
    </header>
  );
}

function ProblemCard({ emoji, title, children }) {
  return (
    <article className="problem-card">
      <span className="problem-emoji" aria-hidden="true">
        {emoji}
      </span>
      <h3>{title}</h3>
      <p>{children}</p>
    </article>
  );
}

/** The two warehouses for this scenario: stock and box prices. */
function Store({ data }) {
  const prices = priceList(data);
  const order = data.items.map((i) => i.name);
  const rank = (what) => (what.includes('+') ? order.length : Math.max(0, order.indexOf(what)));
  return (
    <div className="store">
      {data.warehouses.map((w) => (
        <div className="store-wh" key={w.id}>
          <div className="store-wh-head">
            <WarehouseIcon size={40} fill={w.id === 'OTT' ? '#E0E7FF' : '#DBEAFE'} />
            <strong>{w.name}</strong>
          </div>
          <ul className="stock">
            {data.items.map((it) => {
              const n = w.stock[it.sku] || 0;
              return (
                <li key={it.sku} className={n > 0 ? '' : 'out'}>
                  {it.name}: {n > 0 ? `${n} in stock` : 'out of stock'}
                </li>
              );
            })}
          </ul>
          <ul className="prices">
            {Object.entries(prices[w.id] || {})
              .sort((a, b) => rank(a[0]) - rank(b[0]))
              .map(([what, p]) => (
                <li key={what}>
                  <span>{what.includes('+') ? 'Both in one box' : `${what} only`}</span>
                  <span className="num">
                    {money(p.cents)}
                    {p.arrives && p.arrives > data.deadline && <em className="late-tag"> late</em>}
                  </span>
                </li>
              ))}
          </ul>
        </div>
      ))}
      <div className="store-order">
        <HouseIcon size={40} />
        <div>
          <strong>Your order</strong>
          <p>
            {data.items.map((i) => i.name).join(' + ')}
            <br />
            needed by <b>{day(data.deadline)}</b>
          </p>
        </div>
      </div>
    </div>
  );
}

/* 4. BEFORE & AFTER ------------------------------------------------------- */
function BeforeAfter({ data, before }) {
  const v = verdict(data, before);
  return (
    <section id="before-after" className="section">
      <SectionHead n={4} title="Before & after">
        Before: a typical shortcut rule. After: OrderWaymark checking every option.
      </SectionHead>
      <div className={`verdict ${v.tone}`}>
        <p className="verdict-title">{v.title}</p>
        {v.body && <p>{v.body}</p>}
      </div>
      <div className="ba-grid">
        <PlanCard
          side="before"
          heading="Before"
          rule={before ? SHORTCUTS[before.strategy]?.name : 'Shortcut'}
          plan={before?.plan}
          status={before?.status}
          deadline={data.deadline}
        />
        <div className="ba-arrow" aria-hidden="true">
          →
        </div>
        <PlanCard
          side="after"
          heading="After"
          rule="OrderWaymark: check every option, keep the on-time ones, pick the cheapest"
          plan={data.winner}
          status={data.status}
          deadline={data.deadline}
        />
      </div>
    </section>
  );
}

function PlanCard({ side, heading, rule, plan, status, deadline }) {
  const isAfter = side === 'after';
  const ok = status === 'PLANNED';
  return (
    <article className={`plan-card ${side}${isAfter && ok ? ' recommended' : ''}${!plan ? ' empty' : ''}`}>
      <div className="plan-top">
        <span className={`side-tag ${side}`}>{heading}</span>
        {isAfter && ok && <span className="tag ok">✓ Recommended</span>}
        {!ok && plan && <span className="tag bad">Late</span>}
      </div>
      <p className="rule">"{rule}"</p>
      {!plan ? (
        <div className="no-plan">
          <p className="big-word">No on-time plan</p>
          <p>Every option arrives after {day(deadline)}. OrderWaymark won't pretend otherwise.</p>
        </div>
      ) : (
        <>
          <div className="plan-stats">
            <div>
              <span className="stat-label">Total</span>
              <span className="stat-big num">{money(plan.totalCents)}</span>
              {plan.inventoryCostCents > 0 && (
                <span className="stat-sub warn-text">+ {money(plan.inventoryCostCents)} expected later</span>
              )}
            </div>
            <div>
              <span className="stat-label">Boxes</span>
              <span className="stat-big num">{plan.boxCount}</span>
            </div>
            <div>
              <span className="stat-label">Arrives</span>
              <span className={'stat-mid' + (ok ? '' : ' bad-text')}>{day(plan.latestArrival)}</span>
              <span className="stat-sub">{ok ? 'on time ✓' : `deadline ${day(deadline)}`}</span>
            </div>
          </div>
          <ul className="parcel-list">
            {plan.boxes.map((b) => (
              <li key={b.warehouse}>
                <BoxIcon size={30} fill={b.warehouse === 'OTT' ? '#FBCFE8' : '#FDE68A'} />
                <span>
                  <b>{b.warehouseName}</b> ships {b.items.join(' + ')}
                </span>
                <span className="num">{money(b.priceCents)}</span>
              </li>
            ))}
          </ul>
        </>
      )}
    </article>
  );
}

/* 5. HOW IT DECIDED -------------------------------------------------------- */
function HowItDecided({ data }) {
  const letters = 'ABCDEFGH';
  const valid = data.candidates.filter((c) => c.valid).length;
  const forecasting = data.candidates.some((c) => c.inventoryCostCents > 0);
  const won = (c) => data.winner && JSON.stringify(c.assignment) === JSON.stringify(data.winner.assignment);
  return (
    <section id="how" className="section">
      <SectionHead n={5} title="How OrderWaymark decided">
        No magic: three simple steps, done for every order.
      </SectionHead>
      <ol className="how-steps">
        <li>
          <b>List every option.</b> 2 items × 2 warehouses = {data.candidates.length} ways to ship.
        </li>
        <li>
          <b>Cross out the impossible ones.</b> Out of stock, no price, or anything late.{' '}
          {data.candidates.length - valid} crossed out.
        </li>
        <li>
          <b>Pick the cheapest one left.</b>
          {forecasting && ' Counting the hidden cost of using up scarce stock.'} On a tie, fewer boxes wins.
        </li>
      </ol>
      <div className="options">
        {data.candidates.map((c, i) => (
          <article key={c.assignment.join()} className={'option' + (c.valid ? '' : ' crossed') + (won(c) ? ' winner' : '')}>
            <div className="option-head">
              <span className="option-letter">{letters[i]}</span>
              {won(c) ? (
                <span className="tag ok">✓ Chosen</span>
              ) : c.valid ? (
                <span className="tag neutral">Possible</span>
              ) : (
                <span className="tag bad">✗ {reasonLabel(c.rejections[0])}</span>
              )}
            </div>
            <ul className="option-items">
              {describeAssignment(c, data.items, data.warehouses).map((t) => (
                <li key={t}>{t}</li>
              ))}
            </ul>
            <p className="option-price">
              <span className="num">{money(c.totalCents)}</span> · {c.boxCount} box{c.boxCount === 1 ? '' : 'es'}
              {c.inventoryCostCents > 0 && <span className="warn-text"> · +{money(c.inventoryCostCents)} later</span>}
            </p>
            {!c.valid && <p className="option-why">{c.rejections.join(' ')}</p>}
          </article>
        ))}
      </div>
      <details className="why-exactly">
        <summary>Read the planner's own explanation</summary>
        <p>{data.explanation}</p>
        {data.winner?.inventoryNotes?.length > 0 && <p>{data.winner.inventoryNotes.join(' ')}</p>}
      </details>
    </section>
  );
}

/* 6. RESULTS --------------------------------------------------------------- */
function Results({ data, before }) {
  const forecasting = data.candidates.some((c) => c.inventoryCostCents > 0);
  const best = data.winner;
  return (
    <section id="results" className="section">
      <SectionHead n={6} title="The results, side by side">
        Every rule on the same order. Green is what OrderWaymark recommends.
      </SectionHead>
      <div className="table-wrap">
        <table className="compare">
          <thead>
            <tr>
              <th scope="col">Rule</th>
              <th scope="col">What it picks</th>
              <th scope="col" className="r">Boxes</th>
              <th scope="col" className="r">Price</th>
              {forecasting && <th scope="col" className="r">Hidden cost later</th>}
              <th scope="col">On time?</th>
            </tr>
          </thead>
          <tbody>
            {data.strategies.map((s) => {
              const isRec = s.strategy === 'OPTIMAL' && s.status === 'PLANNED';
              const isBefore = before && s.strategy === before.strategy;
              const diff = s.plan && best ? s.plan.totalCents - best.totalCents : 0; // today's price only
              return (
                <tr key={s.strategy} className={isRec ? 'is-rec' : ''}>
                  <th scope="row" data-label="Rule">
                    {SHORTCUTS[s.strategy]?.short || s.label}
                    {isBefore && <span className="mini-tag">used as "before"</span>}
                  </th>
                  <td data-label="Picks">{s.plan ? s.plan.summary : '—'}</td>
                  <td data-label="Boxes" className="r num">
                    {s.plan ? s.plan.boxCount : '—'}
                  </td>
                  <td data-label="Price" className="r num">
                    {s.plan ? money(s.plan.totalCents) : '—'}
                    {diff > 0 && s.status === 'PLANNED' && <span className="sub"> (+{money(diff)})</span>}
                  </td>
                  {forecasting && (
                    <td data-label="Later" className="r num">
                      {s.plan && s.plan.inventoryCostCents > 0 ? (
                        <span className="warn-text">+{money(s.plan.inventoryCostCents)}</span>
                      ) : (
                        '$0.00'
                      )}
                    </td>
                  )}
                  <td data-label="On time?">
                    {isRec ? (
                      <span className="tag ok">✓ Recommended</span>
                    ) : s.status === 'PLANNED' ? (
                      <span className="tag neutral">Yes</span>
                    ) : s.status === 'REJECTED' ? (
                      <span className="tag bad">No, late</span>
                    ) : (
                      <span className="tag bad">No plan</span>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}
