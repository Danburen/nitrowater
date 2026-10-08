/* Shared helpers for the NitroWater SSO hosted auth pages (no framework). */
(function () {
  'use strict';

  var FP_KEY = 'nw_device_fp';

  function deviceFp() {
    var v = null;
    try { v = localStorage.getItem(FP_KEY); } catch (e) { /* ignore */ }
    if (!v) {
      v = (window.crypto && crypto.randomUUID)
        ? crypto.randomUUID().replace(/-/g, '').substring(0, 16)
        : 'nw' + Date.now();
      try { localStorage.setItem(FP_KEY, v); } catch (e) { /* ignore */ }
    }
    return v;
  }

  function refreshCaptcha(img) {
    if (img) {
      img.src = '/api/auth/captcha?t=' + Date.now();
    }
  }

  function showError(msg) {
    var box = document.getElementById('nw-error');
    if (!box) return;
    if (msg) {
      box.textContent = msg;
      box.hidden = false;
    } else {
      box.textContent = '';
      box.hidden = true;
    }
  }

  /** POST JSON and unwrap the {success, code, message, data} envelope. */
  function postJson(url, body) {
    return fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify(body)
    }).then(function (res) {
      return res.text().then(function (text) {
        var json = {};
        try { json = text ? JSON.parse(text) : {}; } catch (e) { json = {}; }
        if (!res.ok || json.success === false) {
          var parts = [];
          if (json.message) parts.push(json.message);
          if (Array.isArray(json.errors) && json.errors.length) parts.push(json.errors.join('；'));
          throw new Error(parts.join('：') || ('请求失败 (' + res.status + ')'));
        }
        return json.data;
      });
    });
  }

  window.NW = {
    deviceFp: deviceFp,
    refreshCaptcha: refreshCaptcha,
    showError: showError,
    postJson: postJson
  };
})();
