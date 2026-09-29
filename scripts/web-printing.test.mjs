import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const java = readFileSync(new URL('../app/src/main/java/br/com/rbwone/web/WebPrinting.java', import.meta.url), 'utf8');
const expression = JSON.parse(java.match(/PRINT_READY = (".*");/)[1]);
const ready = (changes = {}) => runInNewContext(expression, { document: {
  body: { hasChildNodes: () => true }, readyState: 'complete', images: [], fonts: { status: 'loaded' }, ...changes,
} });

test('document.write content can print without a navigation completion event', () => {
  assert.equal(ready(), true);
  assert.equal(ready({ readyState: 'interactive', images: [{ complete: true }] }), true);
});
test('waits for the document, images and fonts instead of printing blank content', () => {
  assert.equal(ready({ body: null }), false);
  assert.equal(ready({ body: { hasChildNodes: () => false } }), false);
  assert.equal(ready({ readyState: 'loading' }), false);
  assert.equal(ready({ images: [{ complete: false }] }), false);
  assert.equal(ready({ fonts: { status: 'loading' } }), false);
});
test('printing does not require the optional font loading API', () => {
  assert.equal(ready({ fonts: undefined }), true);
});
