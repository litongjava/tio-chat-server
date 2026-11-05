(() => {
  if (window.__bad) { return 'hooked'; }
  window.__bad = []; window.__calls = 0;
  const send = XMLHttpRequest.prototype.send, open = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function (m, u) { this.__m = m; this.__u = u; return open.apply(this, arguments); };
  XMLHttpRequest.prototype.send = function () {
    this.addEventListener('loadend', () => {
      window.__calls++;
      let code = null, msg = '';
      try { const d = JSON.parse(this.responseText); code = d.code; msg = d.msg; } catch (e) {}
      if (this.status !== 200 || (code !== null && code !== 200)) { window.__bad.push(this.__m + ' ' + String(this.__u).replace(/^.*proxy-/, '') + ' http=' + this.status + ' code=' + code + ' ' + String(msg).slice(0, 60)); }
    });
    return send.apply(this, arguments);
  };
  return 'hooked';
})()
