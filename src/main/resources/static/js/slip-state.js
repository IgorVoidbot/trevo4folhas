// Preview only: the server remains authoritative when issuance is implemented.
export function decimalUnits(value) {
  if (typeof value !== 'string' || !/^\d{1,14}(\.\d{1,2})?$/.test(value))
    return null;
  const [whole, fraction = ''] = value.split('.');
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'));
}

export function normalizeStake(value) {
  const text = value.trim();
  if (!/^\d{1,12}([,.]\d{1,2})?$/.test(text)) return null;
  const [whole, fraction = ''] = text.replace(',', '.').split('.');
  return `${BigInt(whole)}.${fraction.padEnd(2, '0')}`;
}

export function preview(items, stake) {
  if (!items.length) return null;
  const prices = items.map((item) => decimalUnits(item.odds));
  if (prices.some((price) => price === null || price < 101n || price > 10000n))
    return null;
  const numerator = prices.reduce((product, price) => product * price, 1n);
  const denominator = 100n ** BigInt(items.length);
  const amount = decimalUnits(stake);
  return {
    combinedHundredths: (numerator * 100n + denominator / 2n) / denominator,
    returnCents:
      amount === null
        ? null
        : (amount * numerator + denominator / 2n) / denominator,
  };
}

export class Slip {
  items = new Map();
  constructor(limit) {
    this.limit = Math.min(10, limit);
  }
  choose(item) {
    const previous = this.items.get(item.matchId);
    if (
      previous?.selectionId === item.selectionId &&
      !previous.needsReview &&
      ['matchVersion', 'marketVersion', 'selectionVersion', 'odds'].every(
        (key) => previous[key] === item[key],
      )
    ) {
      this.items.delete(item.matchId);
      return 'removed';
    }
    if (!previous && this.items.size >= this.limit) return 'limit';
    this.items.set(
      item.matchId,
      Object.freeze({ ...item, needsReview: false }),
    );
    return previous ? 'replaced' : 'added';
  }
  markUnavailable(matchId) {
    const item = this.items.get(matchId);
    if (item)
      this.items.set(matchId, Object.freeze({ ...item, needsReview: true }));
  }
  remove(matchId) {
    this.items.delete(matchId);
  }
  clear() {
    this.items.clear();
  }
}
