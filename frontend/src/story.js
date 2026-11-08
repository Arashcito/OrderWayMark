import { day, money } from './api.js';

/** Plain-language copy for each preset scenario: a short name, the setup, and the twist. */
export const STORY = {
  'one-box-wins': {
    emoji: '📦',
    name: 'One box is cheaper',
    setup: 'Both warehouses have everything. Ottawa offers a deal: ship both items together for $8.',
    twist: 'Grabbing each item wherever it is cheapest costs $11. Packing them together is cheaper.',
  },
  'two-boxes-win': {
    emoji: '📦📦',
    name: 'Two boxes are cheaper',
    setup: "Ottawa's deal for one combined box goes up to $14.",
    twist: 'Now splitting the order into two boxes ($11) beats any single box, even though it means one more package.',
  },
  'stock-changes': {
    emoji: '🚫',
    name: 'Ottawa runs out',
    setup: 'Ottawa has no keyboards left.',
    twist: "Any plan that needs Ottawa's keyboard is impossible, so the planner works around it.",
  },
  'late-box': {
    emoji: '🐢',
    name: 'One box is slow',
    setup: "Ottawa's mouse-only box would arrive Saturday, one day after the deadline.",
    twist: 'Half an order on time is still a late order. The cheapest split is out.',
  },
  'all-late': {
    emoji: '⛈️',
    name: 'Everything is late',
    setup: 'A storm delays every carrier. Every box would arrive Saturday.',
    twist: 'There is no on-time option at all, and the honest answer is to say so.',
  },
  'price-tie': {
    emoji: '⚖️',
    name: 'A price tie',
    setup: "Ottawa's combined box costs $11, exactly the same as splitting.",
    twist: 'Same price, so fewer boxes wins: less packing, less tracking, less waste.',
  },
  'last-keyboard': {
    emoji: '🔮',
    name: 'The last keyboard',
    setup: "Ottawa's $8 deal is back, but that keyboard is the last one Ottawa has until it restocks.",
    twist:
      'Another Ottawa customer will probably want one soon, and shipping it to them from Montréal would cost $6 more. The cheapest box today could cost more tomorrow.',
  },
};

/** What each shortcut rule does, in plain words. */
export const SHORTCUTS = {
  CHEAPEST_PER_ITEM: {
    name: 'Grab each item wherever it is cheapest',
    short: 'Cheapest per item',
  },
  FEWEST_BOXES_FIRST: {
    name: 'Always use as few boxes as possible',
    short: 'Fewest boxes first',
  },
  CHEAPEST_TODAY: {
    name: 'Take the lowest price right now',
    short: 'Cheapest today',
  },
  OPTIMAL: { name: 'OrderWaymark', short: 'OrderWaymark' },
};

const PREFERENCE = ['CHEAPEST_TODAY', 'CHEAPEST_PER_ITEM', 'FEWEST_BOXES_FIRST'];

/**
 * "Before" = the shortcut rule that does worst in this scenario: a rejected (late) plan first,
 * then the highest effective cost, then more boxes. Ties fall back to PREFERENCE order.
 */
export function pickBefore(strategies) {
  const shortcuts = strategies.filter((s) => s.strategy !== 'OPTIMAL' && s.plan);
  if (shortcuts.length === 0) return strategies.find((s) => s.strategy !== 'OPTIMAL') || null;
  const score = (s) => [
    s.status === 'PLANNED' ? 0 : 1,
    s.plan.effectiveCents,
    s.plan.boxCount,
    -PREFERENCE.indexOf(s.strategy),
  ];
  return [...shortcuts].sort((a, b) => {
    const x = score(a), y = score(b);
    for (let i = 0; i < x.length; i++) if (x[i] !== y[i]) return y[i] - x[i];
    return 0;
  })[0];
}

/** The headline that compares before and after. Returns { tone, title, body }. */
export function verdict(data, before) {
  const after = data.winner;
  if (!after) {
    return {
      tone: 'warn',
      title: 'Nothing can arrive on time, and OrderWaymark tells you so',
      body:
        before?.plan
          ? `The shortcut would quietly ship anyway and arrive ${day(before.plan.latestArrival)}, after the ${day(data.deadline)} deadline. OrderWaymark flags it upfront so you can warn the customer or pay for faster shipping.`
          : 'No combination of warehouses can meet the deadline.',
    };
  }
  const b = before?.plan;
  if (!b) return { tone: 'ok', title: 'OrderWaymark found the best plan', body: '' };
  if (before.status !== 'PLANNED') {
    const diff = after.totalCents - b.totalCents;
    return {
      tone: 'ok',
      title: 'The shortcut arrives late. OrderWaymark arrives on time.',
      body: `The shortcut's plan misses the ${day(data.deadline)} deadline. OrderWaymark finds a plan that gets everything there on time${
        diff > 0 ? ` for just ${money(diff)} more` : diff < 0 ? ` and saves ${money(-diff)}` : ' for the same price'
      }.`,
    };
  }
  const effDiff = b.effectiveCents - after.effectiveCents;
  if (b.totalCents < after.totalCents && effDiff > 0) {
    return {
      tone: 'ok',
      title: `Pay ${money(after.totalCents - b.totalCents)} more today, avoid ${money(b.inventoryCostCents)} extra later`,
      body: `The shortcut looks cheaper today (${money(b.totalCents)}), but it uses up scarce stock. Counting what that is expected to cost later, OrderWaymark comes out ${money(effDiff)} ahead.`,
    };
  }
  if (effDiff > 0) {
    const pct = Math.round((100 * effDiff) / b.effectiveCents);
    const hidden = b.inventoryCostCents - after.inventoryCostCents;
    return {
      tone: 'ok',
      title: `OrderWaymark saves ${money(effDiff)} (${pct}%)`,
      body:
        hidden > 0
          ? `The shortcut costs ${money(b.totalCents)} today plus an expected ${money(b.inventoryCostCents)} later for using up scarce stock. OrderWaymark costs ${money(after.totalCents)}${after.inventoryCostCents > 0 ? ` plus ${money(after.inventoryCostCents)} later` : ' with nothing hidden'}, and everything still arrives on time.`
          : `${money(b.totalCents)} with the shortcut, ${money(after.totalCents)} with OrderWaymark, and everything still arrives on time.`,
    };
  }
  if (b.boxCount > after.boxCount) {
    return {
      tone: 'ok',
      title: 'Same price, one less box',
      body: 'Both cost the same, so OrderWaymark picks fewer boxes: less packing, less tracking, less cardboard.',
    };
  }
  return {
    tone: 'neutral',
    title: 'Here the shortcut gets it right too',
    body: 'Shortcuts sometimes land on the best plan. OrderWaymark checks every option to make sure.',
  };
}

/** "Keyboard from Montréal · Mouse from Ottawa" */
export function describeAssignment(candidate, items, warehouses) {
  const names = Object.fromEntries(warehouses.map((w) => [w.id, w.name]));
  return items.map((it, i) => `${it.name} from ${names[candidate.assignment[i]] || candidate.assignment[i]}`);
}

/** Friendly label for a rejection reason coming from the API. */
export function reasonLabel(reason) {
  if (reason.startsWith('Late box')) return 'Arrives too late';
  if (reason.startsWith('Insufficient stock')) return 'Out of stock';
  if (reason.startsWith('No shipping quote')) return 'No price available';
  return 'Not possible';
}

/** All box prices for the setup table, read from the candidate plans (warehouse -> "Keyboard + Mouse" -> cents). */
export function priceList(data) {
  const out = {};
  for (const c of data.candidates) {
    for (const b of c.boxes) {
      const key = b.items.join(' + ');
      out[b.warehouse] = out[b.warehouse] || {};
      if (!(key in out[b.warehouse])) out[b.warehouse][key] = { cents: b.priceCents, arrives: b.arrives };
    }
  }
  return out;
}
