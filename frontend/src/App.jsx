import { useCallback, useEffect, useRef, useState } from 'react';
import { day, fetchPlan, money, parseDollars } from './api.js';

const DEFAULT_SCENARIO = 'one-box-wins';

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
      if (id !== latest.current) return; // a newer request superseded this one
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

  function onScenarioChange(e) {
    setScenario(e.target.value);
    setInputError('');
    load(e.target.value); // fresh scenario -> its own default Ottawa quote
  }

  function onRecalculate(e) {
    e.preventDefault();
    const parsed = parseDollars(quoteText);
    if (parsed.error) {
      setInputError(parsed.error);
      return;
    }
    setInputError('');
    load(scenario, parsed.cents);
  }

  const description = data?.scenario?.description;

  return (
    <div className="page">
      <header className="masthead">
        <p className="eyebrow">OrderWaymark</p>
        <h1>One Box or Two?</h1>
        <p className="lede">
          One keyboard, one mouse, one deadline. Find the cheapest plan that gets <em>both</em> items there on time.
        </p>
      </header>

      <main>
        <section className="panel controls" aria-labelledby="controls-heading">
          <h2 id="controls-heading">Scenario &amp; quote</h2>
          <form onSubmit={onRecalculate} noValidate>
            <div className="field">
              <label htmlFor="scenario">Scenario</label>
              <select id="scenario" value={scenario} onChange={onScenarioChange} disabled={!scenarios.length}>
                {scenarios.length === 0 && <option value={scenario}>Loading…</option>}
                {scenarios.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.title}
                  </option>
                ))}
              </select>
            </div>

            <div className="field">
              <label htmlFor="ottawa-quote">Ottawa combined-box quote</label>
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
              <p id="quote-hint" className="hint">
                Keyboard + mouse shipped together from Ottawa.
                {defaultQuote != null && <> Scenario default: {money(defaultQuote)}.</>}
              </p>
              {inputError && (
                <p id="quote-error" className="field-error" role="alert">
                  {inputError}
                </p>
              )}
            </div>

            <div className="actions">
              <button type="submit" className="primary" disabled={loading || !data}>
                {loading ? 'Recalculating…' : 'Recalculate'}
              </button>
            </div>
          </form>
          {description && <p className="scenario-desc">{description}</p>}
        </section>

        {requestError && (
          <div className="banner error" role="alert">
            {requestError}
          </div>
        )}

        <div className={'results' + (loading ? ' is-loading' : '')} aria-busy={loading} aria-live="polite">
          {!data && !requestError && <div className="panel skeleton">Loading plan…</div>}
          {data && <Recommendation data={data} />}
          {data && <Comparison data={data} />}
        </div>
      </main>

      <footer className="foot">
        Synthetic demo data. Prices are calculated in integer cents by the Spring Boot planner.
      </footer>
    </div>
  );
}

function Recommendation({ data }) {
  if (data.status !== 'PLANNED') {
    const reasons = [...new Set(data.candidates.flatMap((c) => c.rejections))];
    return (
      <section className="panel no-plan" aria-labelledby="rec-heading">
        <p className="tag warn">No on-time plan</p>
        <h2 id="rec-heading">Nothing can arrive by {day(data.deadline)}</h2>
        <p className="explanation">
          Every possible plan was rejected, so OrderWaymark returns <code>NO_ON_TIME_PLAN</code> instead of
          recommending a late delivery.
        </p>
        <ul className="reasons">
          {reasons.map((r) => (
            <li key={r}>{r}</li>
          ))}
        </ul>
      </section>
    );
  }

  const w = data.winner;
  return (
    <section className="panel recommended" aria-labelledby="rec-heading">
      <p className="tag ok">
        <span aria-hidden="true">✓</span> Recommended
      </p>
      <h2 id="rec-heading">{w.summary}</h2>
      <dl className="stats">
        <div>
          <dt>Total cost</dt>
          <dd className="num big">{money(w.totalCents)}</dd>
        </div>
        <div>
          <dt>Boxes</dt>
          <dd className="num big">{w.boxCount}</dd>
        </div>
        <div>
          <dt>Arrives by</dt>
          <dd className="num">
            {day(w.latestArrival)} <span className="sub">deadline {day(data.deadline)}</span>
          </dd>
        </div>
      </dl>
      <ul className="boxes">
        {w.boxes.map((b) => (
          <li key={b.warehouse}>
            <strong>{b.warehouseName}</strong>
            <span>{b.items.join(' + ')}</span>
            <span className="num">{money(b.priceCents)}</span>
            <span className="sub">arrives {day(b.arrives)}</span>
          </li>
        ))}
      </ul>
      <p className="explanation">{data.explanation}</p>
    </section>
  );
}

function Comparison({ data }) {
  const best = data.winner?.totalCents;
  return (
    <section className="panel" aria-labelledby="cmp-heading">
      <h2 id="cmp-heading">Strategy comparison</h2>
      <table className="compare">
        <thead>
          <tr>
            <th scope="col">Strategy</th>
            <th scope="col">Plan</th>
            <th scope="col" className="r">Boxes</th>
            <th scope="col" className="r">Total</th>
            <th scope="col">Result</th>
          </tr>
        </thead>
        <tbody>
          {data.strategies.map((s) => {
            const isRec = s.strategy === 'OPTIMAL' && s.status === 'PLANNED';
            const diff = s.status === 'PLANNED' && best != null ? s.plan.totalCents - best : null;
            return (
              <tr key={s.strategy} className={isRec ? 'is-rec' : ''}>
                <th scope="row" data-label="Strategy">
                  {s.label}
                </th>
                <td data-label="Plan">
                  {s.plan ? s.plan.summary : '—'}
                  <details>
                    <summary>Why</summary>
                    <p>{s.explanation}</p>
                  </details>
                </td>
                <td data-label="Boxes" className="r num">
                  {s.plan ? s.plan.boxCount : '—'}
                </td>
                <td data-label="Total" className="r num">
                  {s.plan ? money(s.plan.totalCents) : '—'}
                  {diff > 0 && <span className="sub"> +{money(diff)}</span>}
                </td>
                <td data-label="Result">
                  {isRec ? (
                    <span className="tag ok sm">Recommended</span>
                  ) : s.status === 'PLANNED' ? (
                    <span className="tag neutral sm">On time</span>
                  ) : s.status === 'REJECTED' ? (
                    <span className="tag warn sm">Rejected</span>
                  ) : (
                    <span className="tag warn sm">No on-time plan</span>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </section>
  );
}
