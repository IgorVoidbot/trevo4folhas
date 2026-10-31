import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  Slip,
  normalizeStake,
  preview,
} from '../../main/resources/static/js/slip-state.js';

test('Brazilian monetary input is normalized without accepting ambiguous precision', () => {
  assert.equal(normalizeStake(' 10,5 '), '10.50');
  assert.equal(normalizeStake('0010.00'), '10.00');
  for (const value of ['1.000,00', '-1', 'NaN', '10,001', '1e3', ''])
    assert.equal(normalizeStake(value), null);
});
test('preview preserves the full product and rounds only final monetary cents', () => {
  assert.equal(
    preview([{ odds: '1.80' }, { odds: '2.50' }], '10.00').returnCents,
    4500n,
  );
  assert.equal(
    preview([{ odds: '1.01' }, { odds: '1.50' }], '1.00').returnCents,
    152n,
  );
  assert.equal(
    preview(
      Array.from({ length: 10 }, () => ({ odds: '100.00' })),
      '500.00',
    ).returnCents,
    50000n * 100n ** 10n,
  );
  assert.equal(preview([{ odds: '1.001' }], '10.00'), null);
  assert.equal(preview([], '10.00'), null);
});
test('one selection per match replaces other markets and preserves accepted versions', () => {
  const slip = new Slip(2);
  slip.choose({
    matchId: 'a',
    selectionId: 'home',
    matchVersion: 1,
    marketVersion: 2,
    selectionVersion: 3,
  });
  assert.equal(
    slip.choose({
      matchId: 'a',
      selectionId: 'over',
      matchVersion: 1,
      marketVersion: 4,
      selectionVersion: 5,
    }),
    'replaced',
  );
  assert.equal(slip.items.size, 1);
  assert.equal(slip.items.get('a').marketVersion, 4);
  assert.equal(
    slip.choose({
      matchId: 'a',
      selectionId: 'over',
      matchVersion: 1,
      marketVersion: 4,
      selectionVersion: 5,
    }),
    'removed',
  );
});
test('limit blocks only new matches and changed offers require a new explicit choice', () => {
  const slip = new Slip(1);
  slip.choose({ matchId: 'a', selectionId: 'home', selectionVersion: 1 });
  assert.equal(slip.choose({ matchId: 'b', selectionId: 'away' }), 'limit');
  slip.markUnavailable('a');
  assert.equal(slip.items.get('a').selectionVersion, 1);
  assert.equal(slip.items.get('a').needsReview, true);
  assert.equal(
    slip.choose({ matchId: 'a', selectionId: 'home', selectionVersion: 2 }),
    'replaced',
  );
  assert.equal(slip.items.get('a').needsReview, false);
  assert.equal(
    slip.choose({ matchId: 'a', selectionId: 'home', selectionVersion: 3 }),
    'replaced',
  );
  slip.clear();
  assert.equal(slip.items.size, 0);
});
