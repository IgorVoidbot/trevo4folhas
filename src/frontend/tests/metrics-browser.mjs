import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
const { chromium } = await import(
  process.env.PLAYWRIGHT_MODULE || 'playwright'
);
const base = process.env.TEST_BASE_URL;
assert.equal(new URL(base).hostname, '127.0.0.1');
const output = 'target/verification-sprint5';
await mkdir(output, { recursive: true });
const browser = await chromium.launch({
  executablePath: '/usr/bin/google-chrome',
  headless: true,
});
try {
  for (const width of [360, 1440]) {
    const context = await browser.newContext({
      viewport: { width, height: 900 },
      timezoneId: 'UTC',
    });
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.goto(`${base}/admin/login`);
    await page.locator('[name=username]').fill(process.env.TEST_ADMIN_USERNAME);
    await page.locator('[name=password]').fill(process.env.TEST_ADMIN_PASSWORD);
    await page.locator('button[type=submit]').click();
    await page.waitForURL(`${base}/admin`);
    await page.getByRole('link', { name: 'Indicadores operacionais' }).click();
    await page
      .locator('#metrics-feedback')
      .filter({ hasText: 'Indicadores atualizados.' })
      .waitFor();
    assert.match(
      await page.locator('#metrics-zone').textContent(),
      /America\/Sao_Paulo/,
    );
    assert.match(
      await page.locator('#cohort-state').textContent(),
      /Coorte aberta|encerrada|Sem emissões/,
    );
    assert.doesNotMatch(
      await page.locator('#confirmed-values').textContent(),
      /NaN|undefined|Infinity/,
    );
    assert.equal(
      await page.evaluate(
        () =>
          globalThis.document.documentElement.scrollWidth <=
          globalThis.innerWidth,
      ),
      true,
    );
    await page.screenshot({
      path: `${output}/metrics-${width}.png`,
      fullPage: true,
    });
    await page.locator('[name=from]').fill('2000-01-01');
    await page.locator('[name=to]').fill('2000-01-01');
    await page.locator('#load-metrics').click();
    await page
      .locator('#cohort-state')
      .filter({ hasText: 'Sem emissões' })
      .waitFor();
    assert.match(
      await page.locator('#confirmed-values').textContent(),
      /R\$ 0,00/,
    );
    assert.match(await page.locator('#confirmed-values').textContent(), /—/);
    assert.deepEqual(errors, []);
    await context.close();
  }
  // Experimental mobile scenario; not a physical Android/iOS or an agreed production SLO.
  const samples = [];
  for (let n = 0; n < 10; n++) {
    const context = await browser.newContext({
      viewport: { width: 360, height: 800 },
      isMobile: true,
      deviceScaleFactor: 2,
    });
    const page = await context.newPage();
    const cdp = await context.newCDPSession(page);
    await cdp.send('Network.enable');
    await cdp.send('Network.setCacheDisabled', { cacheDisabled: true });
    await cdp.send('Network.emulateNetworkConditions', {
      offline: false,
      latency: 150,
      downloadThroughput: 200000,
      uploadThroughput: 93750,
    });
    await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });
    await page.addInitScript(() => {
      globalThis.__lastLcp = 0;
      new globalThis.PerformanceObserver((list) => {
        for (const entry of list.getEntries())
          globalThis.__lastLcp = entry.startTime;
      }).observe({ type: 'largest-contentful-paint', buffered: true });
    });
    await page.goto(base, { waitUntil: 'networkidle' });
    await page.locator('#match-list article').first().waitFor();
    await page.waitForTimeout(500);
    const lcp = await page.evaluate(() => globalThis.__lastLcp);
    assert.ok(lcp > 0);
    samples.push(lcp);
    await context.close();
  }
  samples.sort((a, b) => a - b);
  const report = {
    scope:
      'experimental local Chrome mobile emulation; not an agreed device/network scenario',
    viewport: '360x800',
    cpuSlowdown: 4,
    latencyMs: 150,
    downloadBytesPerSecond: 200000,
    cache: 'disabled',
    samplesMs: samples.map((v) => Math.round(v)),
    p75Ms: Math.round(samples[Math.ceil(samples.length * 0.75) - 1]),
    targetMs: 2500,
  };
  await writeFile(`${output}/lcp-report.json`, JSON.stringify(report, null, 2));
  console.log(
    'PASS: private metrics dashboard 360/1440, empty values and separate temporal labels. Mobile LCP experiment:',
    JSON.stringify(report),
  );
} finally {
  await browser.close();
}
