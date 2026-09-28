(() => {
  if (window !== window.top || location.origin !== 'https://rbwone.com.br' ||
      !window.RBWDownloads || window.__rbwDownloadsInstalled) return;
  window.__rbwDownloadsInstalled = true;
  const bridge = window.RBWDownloads;
  const limit = 64 * 1024 * 1024;
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
  async function save(url, name) {
    if (busy) { notice('Conclua o download atual antes de iniciar outro.'); return; }
    busy = true;
    const id = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
    try {
      // Start fetching synchronously: the caller may revoke the object URL immediately after click().
      const response = await fetch(url);
      if (!response.ok) throw new Error('Não foi possível ler o arquivo.');
      const blob = await response.blob();
      if (blob.size > limit) throw new Error('O limite por arquivo no aplicativo é 64 MB. Baixe este arquivo pelo navegador.');
      await request({ action: 'begin', id, size: blob.size, name: name || 'download', mime: blob.type });
      let sequence = 0;
      for (let offset = 0; offset < blob.size; offset += 48 * 1024) {
        const bytes = new Uint8Array(await blob.slice(offset, offset + 48 * 1024).arrayBuffer());
        let binary = '';
        for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
        await request({ action: 'chunk', id, sequence: sequence++, data: btoa(binary) });
      }
      await request({ action: 'finish', id }, 10 * 60 * 1000);
    } catch (error) {
      try { bridge.postMessage(JSON.stringify({ action: 'cancel', id })); } catch (_) { }
      notice(error.message || 'Não foi possível salvar o arquivo. Tente novamente.');
    } finally { busy = false; }
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
})();
