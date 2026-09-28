import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../app/src/main/assets/downloads.js', import.meta.url), 'utf8');
function harness({ fail, responseBlob, foreign = false } = {}) {
  const messages = [], notices = [], listeners = new Map();
  let originalClicks = 0, fetches = 0;
  class Anchor {
    constructor(href, name = 'relatório.pdf') { this.href = href; this.download = name; }
    hasAttribute(name) { return name === 'download' && this.download !== null; }
    click() { originalClicks++; }
    dispatchEvent() { originalClicks++; return true; }
    closest() { return this; }
  }
  const bridge = { postMessage(raw) {
    const message = JSON.parse(raw); messages.push(message);
    if (!message.requestId) return;
    queueMicrotask(() => bridge.onmessage({ data: JSON.stringify({ requestId: message.requestId, ok: message.action !== fail, error: 'Falha simulada' }) }));
  }};
  const window = { RBWDownloads: bridge, alert: message => notices.push(message) };
  window.top = foreign ? {} : window;
  const context = { window, location: { origin: 'https://rbwone.com.br' },
    HTMLAnchorElement: Anchor, document: { addEventListener: (name, fn) => listeners.set(name, fn) },
    fetch: async () => { fetches++; return { ok: true, blob: async () => responseBlob ?? new Blob([new Uint8Array([0, 128, 255])], { type: 'application/pdf' }) }; },
    Uint8Array, setTimeout, clearTimeout, btoa, Date, Math };
  vm.runInNewContext(source, context);
  return { Anchor, messages, notices, listeners, clicks: () => originalClicks, fetches: () => fetches };
}
const tick = () => new Promise(resolve => setTimeout(resolve, 20));
test('FileSaver-style detached dispatchEvent exports reach the bridge', async () => {
  const h = harness(); const anchor = new h.Anchor('blob:https://rbwone.com.br/id');
  const event = { type: 'click', defaultPrevented: false, preventDefault() { this.defaultPrevented = true; } };
  assert.equal(anchor.dispatchEvent(event), false); await tick();
  assert.equal(h.messages.at(-1).action, 'finish'); assert.equal(h.clicks(), 0);
});
test('detached programmatic anchor preserves filename, MIME and binary content', async () => {
  const h = harness(); new h.Anchor('blob:https://rbwone.com.br/id').click();
  assert.equal(h.fetches(), 1, 'fetch starts before the caller can revoke the URL');
  await tick();
  assert.deepEqual(h.messages.map(x => x.action), ['begin', 'chunk', 'finish']);
  assert.equal(h.messages[0].name, 'relatório.pdf');
  assert.equal(h.messages[0].mime, 'application/pdf');
  assert.deepEqual([...Buffer.from(h.messages[1].data, 'base64')], [0, 128, 255]);
  assert.equal(h.clicks(), 0);
});
test('chunks are ordered and byte-exact for larger exports', async () => {
  const bytes = new Uint8Array(120000).map((_, i) => i % 256);
  const h = harness({ responseBlob: new Blob([bytes]) });
  new h.Anchor('blob:https://rbwone.com.br/id', 'export.xlsx').click(); await tick();
  const chunks = h.messages.filter(x => x.action === 'chunk');
  assert.deepEqual(chunks.map(x => x.sequence), [0, 1, 2]);
  assert.deepEqual(Buffer.concat(chunks.map(x => Buffer.from(x.data, 'base64'))), Buffer.from(bytes));
});
test('normal URLs and preview links retain browser behavior', () => {
  const h = harness();
  new h.Anchor('https://example.com/document.pdf').click();
  new h.Anchor('blob:https://rbwone.com.br/preview', null).click();
  new h.Anchor('blob:https://other.example/id').click();
  assert.equal(h.clicks(), 3); assert.equal(h.fetches(), 0);
});
test('user download click is intercepted once and data exports work', async () => {
  const h = harness(); let prevented = false;
  h.listeners.get('click')({ target: new h.Anchor('data:application/pdf;base64,AA=='), defaultPrevented: false,
    preventDefault: () => { prevented = true; } });
  await tick(); assert.equal(prevented, true); assert.equal(h.fetches(), 1);
});
test('oversized files fail visibly without beginning a native transfer', async () => {
  const h = harness({ responseBlob: { size: 64 * 1024 * 1024 + 1 } });
  new h.Anchor('blob:https://rbwone.com.br/id').click(); await tick();
  assert.equal(h.messages.some(x => x.action === 'begin'), false);
  assert.match(h.notices[0], /64 MB/);
});
test('transfer errors cancel native state and permit retry', async () => {
  const h = harness({ fail: 'chunk' });
  new h.Anchor('blob:https://rbwone.com.br/id').click(); await tick();
  assert.equal(h.messages.at(-1).action, 'cancel'); assert.equal(h.notices.length, 1);
  new h.Anchor('blob:https://rbwone.com.br/id2').click(); await tick(); assert.equal(h.fetches(), 2);
});
test('concurrent exports do not interleave', async () => {
  const h = harness(); const anchor = new h.Anchor('blob:https://rbwone.com.br/id');
  anchor.click(); anchor.click(); await tick();
  assert.equal(h.fetches(), 1); assert.match(h.notices[0], /download atual/);
});
test('subframes do not install the download bridge', () => {
  const h = harness({ foreign: true }); new h.Anchor('blob:https://rbwone.com.br/id').click();
  assert.equal(h.clicks(), 1); assert.equal(h.listeners.size, 0);
});
