import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { runInNewContext } from 'node:vm';

const root = new URL('../', import.meta.url);
const html = await readFile(new URL('download.html', root), 'utf8');
const script = html.match(/<script type="module">([\s\S]*?)<\/script>/)[1];
const release = JSON.parse(await readFile(new URL('web-latest.json', root), 'utf8'));
const origin = 'https://saasolucoes001-cpu.github.io';

async function openPage({ manifest = release, failed = false, offline = false, invalidJson = false, timeout = false, supportsAbort = true } = {}) {
  const elements = Object.fromEntries(['status', 'download', 'retry', 'notice', 'integrity'].map(id => [id, {
    hidden: id === 'download' || id === 'retry', textContent: '',
    removeAttribute(name) { delete this[name]; },
    addEventListener(type, callback) { this[type] = callback; },
  }]));
  const requests = [];
  let next = { manifest, failed, offline, invalidJson, timeout };
  let timer;
  const context = {
    document: { querySelector(selector) { return elements[selector.slice(1)]; } },
    location: { origin, href: origin + '/rbw-one-android/download.html' },
    URL, AbortController: supportsAbort ? AbortController : undefined,
    fetch(url, options) {
      requests.push({ url, options });
      if (next.timeout) return new Promise(() => {});
      if (next.offline) return Promise.reject(new Error('offline'));
      return Promise.resolve({ ok: !next.failed, json: async () => { if (next.invalidJson) throw new Error('invalid JSON'); return next.manifest; } });
    },
    setTimeout(callback, delay) { timer = callback; if (next.timeout) queueMicrotask(callback); return delay; },
    clearTimeout() { timer = null; },
  };
  await runInNewContext('(async () => {' + script + '\n})()', context);
  return { elements, requests, setNext(value) { next = value; }, timer: () => timer };
}

test('APK payload matches release size, SHA-256 and unique first-party path', async () => {
  const url = new URL(release.apkUrl);
  assert.equal(url.origin, origin);
  assert.equal(url.pathname, '/rbw-one-android/rbw-one-web-1.0.4.apk');
  assert.equal(release.packageName, 'br.com.rbwone.web');
  assert.equal(release.versionCode, 5);
  const apk = await readFile(new URL(url.pathname.split('/').at(-1), root));
  assert.equal(apk.length, release.sizeBytes);
  assert.equal(createHash('sha256').update(apk).digest('hex'), release.sha256);
});

test('legacy manifests retain their own packages and original APK hashes', async () => {
  for (const [name, packageName, hash] of [
    ['latest.json', 'br.com.rbwone.app', 'cff0fd3a9f44ddd21af4cb3a7607dff667cd435db18e8ce534017f3b30028623'],
    ['native-latest.json', 'br.com.rbwone.nativepreview', '51c7720d5b44279fb8a5d63a0e11ff5f39e909209257cd8d422560cd6b73824e'],
  ]) {
    const legacy = JSON.parse(await readFile(new URL(name, root), 'utf8'));
    assert.equal(legacy.packageName, packageName); assert.equal(legacy.sha256, hash);
    const apk = await readFile(new URL(new URL(legacy.apkUrl).pathname.split('/').at(-1), root));
    assert.equal(apk.length, legacy.sizeBytes); assert.equal(createHash('sha256').update(apk).digest('hex'), hash);
  }
});

test('valid manifest exposes only the new download with integrity and no-store fetch', async () => {
  const page = await openPage();
  assert.equal(page.elements.download.hidden, false);
  assert.equal(page.elements.download.href, release.apkUrl);
  assert.equal(page.elements.retry.hidden, true);
  assert.match(page.elements.integrity.textContent, new RegExp(release.sha256));
  assert.equal(page.elements.notice.textContent, release.notice);
  assert.match(page.requests[0].url, /^\.\/web-latest\.json\?t=\d+$/);
  assert.equal(page.requests[0].options.cache, 'no-store');
  assert.equal(page.timer(), null);
});

for (const [label, options] of [['HTTP error', { failed: true }], ['offline', { offline: true }], ['invalid JSON', { invalidJson: true }], ['timeout', { timeout: true }]]) {
  test(label + ' keeps APK hidden and provides explicit retry instead of another package', async () => {
    const page = await openPage(options);
    assert.equal(page.elements.download.hidden, true); assert.equal(page.elements.download.href, undefined);
    assert.equal(page.elements.retry.hidden, false); assert.match(page.elements.status.textContent, /Não foi possível/);
    assert.equal(page.elements.integrity.textContent, ''); assert.equal(page.timer(), null);
  });
}

test('retry can recover network failure and clears a previous link if a later request fails', async () => {
  const page = await openPage({ offline: true });
  page.setNext({ manifest: release }); await page.elements.retry.click();
  assert.equal(page.elements.download.href, release.apkUrl); assert.equal(page.elements.download.hidden, false);
  page.setNext({ failed: true }); await page.elements.retry.click();
  assert.equal(page.elements.download.hidden, true); assert.equal(page.elements.download.href, undefined);
});

for (const [label, replacement] of [
  ['legacy package', { packageName: 'br.com.rbwone.app' }],
  ['preview package', { packageName: 'br.com.rbwone.nativepreview' }],
  ['other origin', { apkUrl: 'https://other.example/rbw-one-android/rbw-one-web-1.0.0.apk' }],
  ['URL credentials', { apkUrl: 'https://name:password@saasolucoes001-cpu.github.io/rbw-one-android/rbw-one-web-1.0.0.apk' }],
  ['another repository', { apkUrl: origin + '/other/rbw-one-web-1.0.0.apk' }],
  ['nested path', { apkUrl: origin + '/rbw-one-android/nested/rbw-one-web-1.0.0.apk' }],
  ['encoded path', { apkUrl: origin + '/rbw-one-android/%72bw-one-web-1.0.0.apk' }],
  ['query suffix', { apkUrl: release.apkUrl + '?download=1' }],
  ['fragment suffix', { apkUrl: release.apkUrl + '#x' }],
  ['mismatched filename/version', { versionName: '9.9.9' }],
  ['invalid hash', { sha256: 'not-a-checksum' }],
  ['invalid size', { sizeBytes: 0 }],
  ['unsupported schema', { schemaVersion: 2 }],
  ['invalid version', { versionCode: '1' }],
]) test(label + ' fails closed', async () => {
  const page = await openPage({ manifest: { ...release, ...replacement } });
  assert.equal(page.elements.download.hidden, true); assert.equal(page.elements.download.href, undefined);
  assert.equal(page.elements.retry.hidden, false);
});

test('manifest text is rendered as text, and older browsers can work without AbortController', async () => {
  const notice = '<img src=x onerror=alert(1)>';
  const page = await openPage({ manifest: { ...release, notice }, supportsAbort: false });
  assert.equal(page.elements.notice.textContent, notice); assert.equal(page.elements.notice.innerHTML, undefined);
  assert.equal(page.elements.download.hidden, false);
});

test('landing page preserves explicit legacy links, fallback anchor and hidden-button CSS', async () => {
  const index = await readFile(new URL('index.html', root), 'utf8');
  assert.match(index, /href="\.\/download\.html"/);
  assert.match(index, /id="versoes-anteriores"/);
  assert.match(index, /href="RBW-One-1\.0\.1\.apk" download/);
  assert.match(index, /href="RBW-One-Nativo-Previa-0\.2\.0\.apk" download/);
  assert.match(html, /href="\.\/index\.html#versoes-anteriores"/);
  assert.match(html, /\[hidden\]\{display:none!important\}/);
});
