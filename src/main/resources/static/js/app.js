// 页面小动作：提示条自动消失、操作前用系统自带样式的弹窗确认、搜索框聚焦全选
document.addEventListener('DOMContentLoaded', function () {

  // 操作成功的提示 5 秒后自动收起（红色/黄色提示保留，需要人看到）
  document.querySelectorAll('.flash-ok').forEach(function (el) {
    setTimeout(function () {
      el.style.transition = 'opacity .4s, transform .4s';
      el.style.opacity = '0';
      el.style.transform = 'translateY(-6px)';
      setTimeout(function () { el.remove(); }, 420);
    }, 5000);
  });

  // 所有带 data-confirm 的表单，提交前弹出应用自己的确认框（不用浏览器那种老式弹窗）
  document.querySelectorAll('form[data-confirm]').forEach(function (form) {
    form.addEventListener('submit', function (e) {
      if (form.dataset.confirmed === '1') { return; }   // 已经确认过，放行
      e.preventDefault();
      openConfirm(form);
    });
  });

  // 搜索框聚焦时全选内容，方便直接改关键词
  document.querySelectorAll('input[data-select-on-focus]').forEach(function (input) {
    input.addEventListener('focus', function () { input.select(); });
  });
});

function openConfirm(form) {
  var message = form.getAttribute('data-confirm') || '确定要继续吗？';
  var title = form.getAttribute('data-confirm-title') || '请确认';
  var okText = form.getAttribute('data-confirm-ok') || '确定';
  var danger = form.hasAttribute('data-confirm-danger');
  // 有些操作需要先让用户填一个数字（比如续借天数）
  var inputLabel = form.getAttribute('data-confirm-input');
  var inputName = form.getAttribute('data-confirm-input-name') || 'days';
  var inputValue = form.getAttribute('data-confirm-input-value') || '';
  var inputEl = null;

  var backdrop = document.createElement('div');
  backdrop.className = 'modal-backdrop';

  var box = document.createElement('div');
  box.className = 'modal';
  box.setAttribute('role', 'dialog');
  box.setAttribute('aria-modal', 'true');

  var head = document.createElement('div');
  head.className = 'modal-head';
  var h = document.createElement('h3');
  h.className = 'modal-title';
  h.textContent = title;
  head.appendChild(h);

  var body = document.createElement('div');
  body.className = 'modal-body';
  body.textContent = message;

  if (inputLabel) {
    var field = document.createElement('div');
    field.className = 'modal-field';
    var label = document.createElement('label');
    label.className = 'field-label';
    label.textContent = inputLabel;
    inputEl = document.createElement('input');
    inputEl.type = 'number';
    inputEl.className = 'input';
    inputEl.value = inputValue;
    inputEl.min = '1';
    inputEl.max = '365';
    field.appendChild(label);
    field.appendChild(inputEl);
    body.appendChild(field);
    var hint = document.createElement('div');
    hint.className = 'field-hint';
    hint.textContent = '填 1~365 之间的天数，从现在的应还日期往后延。';
    body.appendChild(hint);
  }

  var foot = document.createElement('div');
  foot.className = 'modal-foot';

  var cancel = document.createElement('button');
  cancel.type = 'button';
  cancel.className = 'btn';
  cancel.textContent = '取消';

  var ok = document.createElement('button');
  ok.type = 'button';
  ok.className = 'btn ' + (danger ? 'btn-danger-solid' : 'btn-primary');
  ok.textContent = okText;

  function close() {
    backdrop.remove();
    document.removeEventListener('keydown', onKey);
    window.removeEventListener('popstate', close);
  }
  function onKey(ev) {
    if (ev.key === 'Escape') { close(); }
    if (ev.key === 'Enter' && document.activeElement !== cancel) { ok.click(); }
  }
  function accept() {
    if (inputEl) {
      var hidden = form.querySelector('input[data-modal-field="' + inputName + '"]');
      if (!hidden) {
        hidden = document.createElement('input');
        hidden.type = 'hidden';
        hidden.name = inputName;
        hidden.setAttribute('data-modal-field', inputName);
        form.appendChild(hidden);
      }
      hidden.value = inputEl.value || inputValue;
    }
    close();
    form.dataset.confirmed = '1';
    if (typeof form.requestSubmit === 'function') { form.requestSubmit(); }
    else { form.submit(); }
  }

  cancel.addEventListener('click', close);
  ok.addEventListener('click', accept);
  backdrop.addEventListener('click', function (ev) { if (ev.target === backdrop) { close(); } });
  document.addEventListener('keydown', onKey);

  foot.appendChild(cancel);
  foot.appendChild(ok);
  box.appendChild(head);
  box.appendChild(body);
  box.appendChild(foot);
  backdrop.appendChild(box);
  document.body.appendChild(backdrop);
  if (inputEl) { inputEl.focus(); inputEl.select(); } else { ok.focus(); }
}
