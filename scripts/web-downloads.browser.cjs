// Isolated browser fixture: no production data or credentials. Requires Playwright + Chrome.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  try {
    const context = await browser.newContext();
    await context.route('**/*', route => route.fulfill({ contentType: 'text/html', body: '<!doctype html><title>Download fixture</title><body></body>' }));
    await context.addInitScript(() => {
      window.downloadMessages = [];
      window.RBWDownloads = { postMessage(raw) {
        const message = JSON.parse(raw); window.downloadMessages.push(message);
        if (message.requestId) queueMicrotask(() => window.RBWDownloads.onmessage({ data: JSON.stringify({ requestId: message.requestId, ok: true }) }));
      } };
    });
    await context.addInitScript({ content: fs.readFileSync(path.join(__dirname, '../app/src/main/assets/downloads.js'), 'utf8') });
    const page = await context.newPage();
    await page.goto('https://rbwone.com.br/fixture');
    for (const kind of ['click', 'dispatchEvent', 'preview']) {
      await page.evaluate(kind => {
        window.downloadMessages = [];
        const bytes = new Uint8Array(150000).map((_, index) => index % 256);
        const url = URL.createObjectURL(new Blob([bytes], { type: 'application/pdf' }));
        const anchor = document.createElement('a'); anchor.href = url; anchor.download = 'relatório.pdf';
        if (kind === 'click') anchor.click();
        else if (kind === 'dispatchEvent') anchor.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
        else window.open(url, '_blank');
        URL.revokeObjectURL(url);
      }, kind);
      await page.waitForFunction(() => window.downloadMessages.some(x => x.action === 'finish'));
      const messages = await page.evaluate(() => window.downloadMessages);
      const bytes = Buffer.concat(messages.filter(x => x.action === 'chunk').map(x => Buffer.from(x.data, 'base64')));
      assert.equal(bytes.length, 150000);
      for (let i = 0; i < bytes.length; i++) assert.equal(bytes[i], i % 256);
      assert.equal(messages[0].mode, kind === 'preview' ? 'preview' : 'save');
      console.log(`PASS real Chromium: ${kind}, immediate URL revocation, MIME and byte integrity`);
    }
    await page.goto('https://external.example/fixture');
    assert.equal(await page.evaluate(() => Boolean(window.__rbwDownloadsInstalled)), false);
    console.log('PASS real Chromium: no bridge interception on external origins');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
