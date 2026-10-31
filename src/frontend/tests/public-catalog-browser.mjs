// Read-only catalog verification. Never emits tickets or calls an external provider.
import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
const { chromium } = await import(
  process.env.PLAYWRIGHT_MODULE || 'playwright'
);
const base = process.env.TEST_BASE_URL;
assert.equal(new URL(base).hostname, '127.0.0.1');
const output = 'target/verification-sprint2';
await mkdir(output, { recursive: true });
const browser = await chromium.launch({
  executablePath: '/usr/bin/google-chrome',
  headless: true,
});
async function openSlip(page, width) {
  if (width < 1024) await page.locator('#open-slip').click();
}
async function closeSlip(page, width) {
  if (width < 1024) await page.locator('#close-slip').click();
}
async function expand(card) {
  await card.locator('summary').first().click();
  await card.locator('.selection-choice').first().waitFor();
}
try {
  for (const width of [360, 1440]) {
    const context = await browser.newContext({
      viewport: { width, height: 900 },
      timezoneId: 'UTC',
    });
    const page = await context.newPage();
    const errors = [],
      mutations = [];
    page.on('pageerror', (error) => errors.push(error.message));
    page.on('console', (entry) => {
      if (
        entry.type() === 'error' &&
        entry.text().includes('Content Security Policy')
      )
        errors.push(entry.text());
    });
    page.on('request', (request) => {
      if (request.method() !== 'GET') mutations.push(request.method());
    });
    await page.goto(base);
    const cards = page.locator('#match-list article');
    await cards.nth(2).waitFor();
    await expand(cards.nth(0));
    await cards
      .nth(0)
      .getByRole('button', {
        name: 'Vitória do mandante, cotação 1,80',
        exact: true,
      })
      .click();
    await expand(cards.nth(1));
    await cards
      .nth(1)
      .getByRole('button', {
        name: 'Mais de 2,5 gols, cotação 2,50',
        exact: true,
      })
      .click();
    await openSlip(page, width);
    await page.locator('#stake').fill('10,00');
    assert.equal(await page.locator('#combined-odds').textContent(), '4,50');
    assert.match(
      await page.locator('#potential-return').textContent(),
      /R\$ 45,00/,
    );
    await page.locator('#review-slip').click();
    await page.locator('#bettor-name[aria-invalid="true"]').waitFor();
    await page.locator('#bettor-name').fill('Pessoa fictícia de teste');
    await page.locator('#bettor-phone').fill('11999999999');
    await page.locator('#adult-consent').check();
    assert.equal(
      await page.locator('#bettor-phone').inputValue(),
      '(11) 99999-9999',
    );
    await page.locator('#rules-consent').check();
    await page.locator('#review-slip').click();
    await page.locator('#review-summary:not([hidden])').waitFor();
    assert.match(
      await page.locator('#review-summary').textContent(),
      /nenhum pedido emitido/,
    );
    assert.equal(
      await page
        .getByRole('button', { name: 'Gerar pré-bilhete', exact: true })
        .isDisabled(),
      false,
    );
    if (width === 360) {
      await page.locator('#review-slip').focus();
      await page.keyboard.press('Tab');
      assert.equal(
        await page.locator(':focus').getAttribute('id'),
        'generate-ticket',
      );
      await page.keyboard.press('Escape');
      assert.equal(
        await page.locator(':focus').getAttribute('id'),
        'open-slip',
      );
      await openSlip(page, width);
      await page.setViewportSize({ width, height: 450 });
      await page.locator('#bettor-phone').focus();
      await page.locator('#review-slip').scrollIntoViewIfNeeded();
      const box = await page.locator('#review-slip').boundingBox();
      assert.ok(box.y >= 0 && box.y + box.height <= 450);
      await page.setViewportSize({ width, height: 900 });
      await closeSlip(page, width);
    }
    await page.evaluate(() => {
      globalThis.scrollTo(0, 0);
      globalThis.document.getElementById('bet-slip').scrollTop = 0;
    });
    await page.screenshot({
      path: `${output}/catalog-${width}.png`,
      fullPage: false,
    });
    await openSlip(page, width === 360 ? 360 : 1440);
    await page.screenshot({
      path: `${output}/slip-${width}.png`,
      fullPage: false,
    });
    if (width === 1440) {
      const contrast = await page
        .locator('#review-slip, .selection-choice')
        .evaluateAll((elements) => {
          const canvas = globalThis.document.createElement('canvas');
          canvas.width = canvas.height = 1;
          const context = canvas.getContext('2d');
          function rgba(color) {
            context.clearRect(0, 0, 1, 1);
            context.fillStyle = color;
            context.fillRect(0, 0, 1, 1);
            return [...context.getImageData(0, 0, 1, 1).data].map(
              (value, index) => (index === 3 ? value / 255 : value),
            );
          }
          function luminance(rgb) {
            return rgb
              .slice(0, 3)
              .map((value) => {
                const channel = value / 255;
                return channel <= 0.04045
                  ? channel / 12.92
                  : ((channel + 0.055) / 1.055) ** 2.4;
              })
              .reduce(
                (sum, value, index) =>
                  sum + value * [0.2126, 0.7152, 0.0722][index],
                0,
              );
          }
          return elements.map((element) => {
            const chain = [];
            for (let parent = element; parent; parent = parent.parentElement)
              chain.unshift(parent);
            const background = chain.reduce(
              (previous, parent) => {
                const color = rgba(
                  globalThis.getComputedStyle(parent).backgroundColor,
                );
                return color
                  .slice(0, 3)
                  .map(
                    (value, index) =>
                      value * color[3] + previous[index] * (1 - color[3]),
                  );
              },
              [255, 255, 255],
            );
            const text = rgba(globalThis.getComputedStyle(element).color);
            const a = luminance(background),
              b = luminance(text);
            return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
          });
        });
      assert.ok(
        contrast.every((ratio) => ratio >= 4.5),
        `Button contrast: ${contrast}`,
      );
      await page.evaluate(() => {
        globalThis.document.documentElement.style.zoom = '2';
      });
      assert.equal(
        await page.evaluate(
          () =>
            globalThis.document.documentElement.scrollWidth <=
            globalThis.innerWidth,
        ),
        true,
      );
      await page.locator('#review-slip').scrollIntoViewIfNeeded();
      await page.screenshot({ path: `${output}/zoom-200.png` });
      await page.evaluate(() => {
        globalThis.document.documentElement.style.zoom = '';
      });
      await page.setViewportSize({ width: 720, height: 450 });
      await openSlip(page, 720);
      assert.equal(
        await page.evaluate(
          () =>
            globalThis.document.documentElement.scrollWidth <=
            globalThis.innerWidth,
        ),
        true,
      );
      await closeSlip(page, 720);
      await page.setViewportSize({ width, height: 900 });
    }
    await closeSlip(page, width);
    await cards
      .nth(0)
      .getByRole('button', {
        name: 'Vitória do visitante, cotação 4,50',
        exact: true,
      })
      .click();
    await openSlip(page, width);
    assert.match(
      await page.locator('#potential-return').textContent(),
      /112,50/,
    );
    assert.equal(await page.locator('#slip-items li').count(), 2);
    assert.equal(await page.locator('#review-summary').isHidden(), true);
    await page.locator('#slip-items button').first().click();
    assert.equal(await page.locator('#slip-items li').count(), 1);
    page.once('dialog', (dialog) => dialog.accept());
    await page.locator('#clear-slip').click();
    assert.match(
      await page.locator('#slip-count').textContent(),
      /^0 seleções/,
    );
    await page.reload();
    await cards.nth(2).waitFor();
    assert.equal(await page.locator('#bettor-name').inputValue(), '');
    assert.equal(
      await page.evaluate(
        () => globalThis.localStorage.length + globalThis.sessionStorage.length,
      ),
      0,
    );
    assert.equal(
      await page.evaluate(
        () =>
          globalThis.document.documentElement.scrollWidth <=
          globalThis.innerWidth,
      ),
      true,
    );
    assert.deepEqual(mutations, []);
    assert.deepEqual(errors, []);
    await context.close();
  }

  // Clearly synthetic browser fixtures: errors, pages, limits, changed versions and hostile text.
  const page = await browser.newPage({
    viewport: { width: 1440, height: 900 },
  });
  const config = {
    minStakeAmount: '5.00',
    maxStakeAmount: '500.00',
    maxPotentialReturn: '10000.00',
    maxSelections: 10,
    bankTimeZone: 'America/Sao_Paulo',
    rulesVersion: 'PREVIEW-2026-10-31-V1',
  };
  let version = 1,
    failure = 0,
    empty = false;
  const matches = Array.from({ length: 12 }, (_, index) => ({
    id: String(index + 1),
    matchVersion: 1,
    homeTeam:
      index === 0 ? '<img src=x onerror=alert(1)>' : `Time fictício ${index}`,
    awayTeam: 'Visitante fictício',
    competition: 'TESTE de navegador',
    startsAt: '2030-06-01T18:00:00Z',
  }));
  function paged(rows, url) {
    const index = Number(url.searchParams.get('page') || 0),
      size = Number(url.searchParams.get('size') || 20);
    return {
      content: rows.slice(index * size, (index + 1) * size),
      page: index,
      totalPages: Math.ceil(rows.length / size),
      totalElements: rows.length,
      size,
    };
  }
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url()),
      path = url.pathname;
    if (failure === -1) return route.abort();
    if (failure)
      return route.fulfill({
        status: failure,
        contentType: 'application/json',
        body: '{}',
      });
    let body;
    if (path === '/api/config') body = config;
    else if (path === '/api/matches') body = paged(empty ? [] : matches, url);
    else if (/\/api\/matches\/\d+$/.test(path))
      body = matches[Number(path.split('/').at(-1)) - 1];
    else if (path.endsWith('/markets')) {
      const id = path.split('/')[3];
      body = paged(
        [
          {
            id: `market-${id}`,
            matchId: id,
            matchVersion: 1,
            marketVersion: 1,
            displayName: 'Mercado fictício',
            period: 'FT',
            line: 'NONE',
            participant: 'NONE',
            ruleText: '<script>texto não executável</script>',
          },
        ],
        url,
      );
    } else if (path.endsWith('/selections')) {
      const id = path.split('/')[3].replace('market-', '');
      body = paged(
        [
          {
            id: `selection-${id}`,
            matchId: id,
            marketId: `market-${id}`,
            matchVersion: 1,
            marketVersion: 1,
            selectionVersion: version,
            displayName: 'Palpite fictício',
            odds: version === 1 ? '1.80' : '2.00',
          },
        ],
        url,
      );
    } else throw new Error(`Unexpected test path: ${path}`);
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(body),
    });
  });
  await page.goto(base);
  await page.locator('#match-list article').nth(9).waitFor();
  assert.equal(await page.locator('#match-list img').count(), 0);
  for (let index = 0; index < 10; index++) {
    const card = page.locator('#match-list article').nth(index);
    await expand(card);
    await card.locator('.selection-choice').click();
  }
  await page.locator('#catalog-next').click();
  await page.locator('#match-list article').nth(1).waitFor();
  await expand(page.locator('#match-list article').first());
  await page.locator('.selection-choice').first().click();
  assert.match(
    await page.locator('#catalog-feedback').textContent(),
    /até 10 partidas/,
  );
  assert.equal(await page.locator('#slip-items li').count(), 10);
  version = 2;
  await page.locator('#refresh-catalog').click();
  await page
    .locator('#slip-items')
    .getByText('Oferta indisponível ou alterada.', { exact: false })
    .first()
    .waitFor();
  assert.match(await page.locator('#slip-items').textContent(), /1,80/);
  await page.locator('#catalog-previous').click();
  await page.locator('#match-list article').nth(9).waitFor();
  await expand(page.locator('#match-list article').first());
  assert.equal(
    await page
      .locator('.selection-choice')
      .first()
      .getAttribute('aria-pressed'),
    'false',
  );
  await page.locator('.selection-choice').first().click();
  assert.match(
    await page.locator('#slip-items li').first().textContent(),
    /2,00/,
  );
  await page.locator('#stake').fill('500,00');
  await page.locator('#stake-error:not([hidden])').waitFor();
  empty = true;
  await page.locator('#refresh-catalog').click();
  await page
    .getByText('Nenhuma partida disponível', { exact: false })
    .waitFor();
  for (const status of [400, 403, 409, 429, 500, -1]) {
    failure = status;
    await page.reload();
    await page
      .getByRole('button', { name: 'Tentar novamente', exact: true })
      .waitFor();
    assert.equal(await page.locator('#review-slip').isDisabled(), true);
  }
  failure = 0;
  empty = false;
  await page
    .getByRole('button', { name: 'Tentar novamente', exact: true })
    .click();
  await page.locator('#match-list article').nth(9).waitFor();
  await page.close();
  console.log(
    'PASS: public catalog, markets, coupon arithmetic/replacement/removal, review, 360/1440, reduced viewport, keyboard, privacy, pagination, ten-item limit, stale offers, empty state, recovery and 400/403/409/429/500/network errors.',
  );
} finally {
  await browser.close();
}
