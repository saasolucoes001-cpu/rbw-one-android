(() => {
  if (window !== window.top || location.origin !== 'https://rbwone.com.br' ||
      !window.RBWDownloads || window.__rbwDownloadsInstalled) return;
  window.__rbwDownloadsInstalled = true;
  const bridge = window.RBWDownloads;
  const limit = 512 * 1024 * 1024;
  let busy = false;
  let waiter = null;
  bridge.onmessage = event => {
    try {
      const reply = JSON.parse(event.data);
      if (!waiter || reply.requestId !== waiter.id) return;
      const current = waiter; waiter = null; clearTimeout(current.timer);
      reply.ok ? current.resolve(reply) : current.reject(new Error(reply.error || 'Falha ao salvar arquivo.'));
    } catch (_) { /* Ignore unrelated messages. */ }
  };
  function request(payload, timeout = 30000) {
    return new Promise((resolve, reject) => {
      const requestId = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
      const timer = setTimeout(() => {
        if (waiter && waiter.id === requestId) waiter = null;
        reject(new Error('O download demorou demais. Tente novamente.'));
      }, timeout);
      waiter = { id: requestId, resolve, reject, timer };
      try { bridge.postMessage(JSON.stringify({ ...payload, requestId })); }
      catch (error) { clearTimeout(timer); waiter = null; reject(error); }
    });
  }
  function notice(message) { window.alert(message); }
  async function save(url, name, mode = 'save') {
    if (busy) { notice('Conclua o download atual antes de iniciar outro.'); return; }
    busy = true;
    const id = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
    let streamReader = null;
    try {
      // Start fetching synchronously: the caller may revoke the object URL immediately after click().
      const response = await fetch(url);
      if (!response.ok) throw new Error('Não foi possível ler o arquivo.');
      // Stream the response instead of allocating a second full copy of large files.
      const reader = response.body && response.body.getReader ? response.body.getReader() : null;
      streamReader = reader;
      const blob = reader ? null : await response.blob();
      const header = response.headers && response.headers.get('Content-Length');
      const size = blob ? blob.size : (header && /^\d+$/.test(header) ? Number(header) : -1);
      const mime = blob ? blob.type : (response.headers.get('Content-Type') || 'application/octet-stream').split(';')[0];
      if (size > limit) { if (reader) await reader.cancel(); throw new Error('O limite por arquivo no aplicativo é 512 MB.'); }
      const fallback = mime === 'application/pdf' ? 'documento.pdf' : 'download';
      await request({ action: 'begin', id, size, name: name || fallback, mime, mode });
      let sequence = 0;
      let total = 0;
      async function send(bytes) {
        total += bytes.length;
        if (total > limit) throw new Error('O limite por arquivo no aplicativo é 512 MB.');
        let binary = '';
        for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
        await request({ action: 'chunk', id, sequence: sequence++, data: btoa(binary) });
      }
      if (reader) {
        try {
          while (true) {
            const { value, done } = await reader.read();
            if (done) break;
            for (let offset = 0; offset < value.length; offset += 48 * 1024) await send(value.subarray(offset, offset + 48 * 1024));
          }
        } finally { await reader.cancel(); }
      } else {
        for (let offset = 0; offset < blob.size; offset += 48 * 1024) {
          await send(new Uint8Array(await blob.slice(offset, offset + 48 * 1024).arrayBuffer()));
        }
      }
      await request({ action: 'finish', id }, 10 * 60 * 1000);
    } catch (error) {
      try { bridge.postMessage(JSON.stringify({ action: 'cancel', id })); } catch (_) { }
      notice(error.message || 'Não foi possível salvar o arquivo. Tente novamente.');
    } finally {
      if (streamReader) try { await streamReader.cancel(); } catch (_) { }
      busy = false;
    }
  }
  function handles(anchor) {
    return anchor && anchor.hasAttribute('download') &&
      (anchor.href.startsWith('blob:https://rbwone.com.br/') || anchor.href.startsWith('data:'));
  }
  // Libraries also click detached anchors, which do not reach document listeners.
  const originalClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () {
    if (handles(this)) { void save(this.href, this.download); return; }
    return originalClick.call(this);
  };
  // FileSaver-style libraries dispatch MouseEvent on anchors outside the document.
  const originalDispatch = HTMLAnchorElement.prototype.dispatchEvent;
  HTMLAnchorElement.prototype.dispatchEvent = function (event) {
    if (!this.isConnected && event.type === 'click' && !event.defaultPrevented && handles(this)) {
      event.preventDefault(); void save(this.href, this.download);
      return !event.defaultPrevented;
    }
    return originalDispatch.call(this, event);
  };
  document.addEventListener('click', event => {
    const anchor = event.target && event.target.closest ? event.target.closest('a') : null;
    if (!event.defaultPrevented && handles(anchor)) {
      event.preventDefault(); void save(anchor.href, anchor.download);
    }
  });
  const originalOpen = window.open;
  window.open = function (url, ...args) {
    if (typeof url === 'string' && url.startsWith('blob:https://rbwone.com.br/')) {
      void save(url, '', 'preview');
      return { closed: false, focus() {}, close() {} };
    }
    return originalOpen.call(window, url, ...args);
  };
  // Same-origin blob PDF frames can delegate their print action to an Android document viewer.
  const types = new Map();
  if (typeof URL !== 'undefined' && URL.createObjectURL) {
    const create = URL.createObjectURL.bind(URL), revoke = URL.revokeObjectURL.bind(URL);
    URL.createObjectURL = blob => { const url = create(blob); types.set(url, blob.type); return url; };
    URL.revokeObjectURL = url => { types.delete(url); return revoke(url); };
  }
  document.addEventListener('load', event => {
    const frame = event.target;
    if (!frame || frame.tagName !== 'IFRAME' || types.get(frame.src) !== 'application/pdf') return;
    const url = frame.src;
    try { frame.contentWindow.print = () => { void save(url, 'documento.pdf', 'preview'); }; } catch (_) { }
    if (frame.clientWidth > 0 && frame.clientHeight > 0 && !frame.dataset.rbwOpen) {
      frame.dataset.rbwOpen = 'true';
      const button = document.createElement('button'); button.type = 'button';
      button.textContent = 'Abrir documento no aplicativo de PDF';
      button.addEventListener('click', () => { void save(frame.src, 'documento.pdf', 'preview'); });
      frame.insertAdjacentElement('afterend', button);
    }
  }, true);
})();
