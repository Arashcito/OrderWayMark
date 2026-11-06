/** Calls the Spring Boot endpoint (proxied by Vite in dev). Throws Error with a readable message. */
export async function fetchPlan({ scenario, ottawaCombinedQuoteCents }) {
  const body = { scenario };
  if (ottawaCombinedQuoteCents != null) body.ottawaCombinedQuoteCents = ottawaCombinedQuoteCents;

  let res;
  try {
    res = await fetch('/api/plan', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
  } catch {
    throw new Error('Could not reach the OrderWaymark API. Is the Spring Boot app running on port 8080?');
  }

  let data = null;
  try {
    data = await res.json();
  } catch {
    /* non-JSON body, handled below */
  }
  if (!res.ok) {
    throw new Error(data?.error || `The API returned HTTP ${res.status}. Is the Spring Boot app running on port 8080?`);
  }
  return data;
}

/** "11.01" -> 1101. Returns { cents } or { error }. Avoids float rounding by splitting the string. */
export function parseDollars(input) {
  const text = String(input).trim().replace(/^\$/, '');
  if (text === '') return { error: 'Enter a price, for example 8.00.' };
  if (!/^\d+(\.\d{0,2})?$/.test(text)) {
    return { error: 'Use dollars and up to two decimal places, for example 11.01.' };
  }
  const [whole, frac = ''] = text.split('.');
  const cents = Number(whole) * 100 + Number(frac.padEnd(2, '0'));
  if (cents > 100000) return { error: 'The quote must be $1,000.00 or less.' };
  return { cents };
}

export const money = (cents) =>
  cents == null ? '—' : '$' + (cents / 100).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

export const day = (iso) =>
  iso
    ? new Date(iso + 'T12:00:00').toLocaleDateString('en-CA', { weekday: 'short', month: 'short', day: 'numeric' })
    : '—';
