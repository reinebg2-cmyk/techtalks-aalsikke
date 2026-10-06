// Aal Sikke — Settings page (KAN-313, KAN-314)
// dashboard.js (loaded first) already handles the profile modal, the top-bar menus,
// and the close / Escape / backdrop behavior of every modal.
// This file wires the Settings panel buttons and the WhatsApp / SMS channel form.

document.addEventListener('DOMContentLoaded', () => {
  const $ = id => document.getElementById(id);

  // ---- Panel buttons open the shared modals ----
  function openModal(id) {
    const modal = $(id);
    if (modal) modal.hidden = false;
  }
  $('settings-change-password-btn')?.addEventListener('click', () => openModal('password-modal'));
  $('settings-notif-prefs-btn')?.addEventListener('click', () => openModal('notif-prefs-modal'));

  // ---- WhatsApp / SMS channel preferences (KAN-314) ----
  const form = $('channel-prefs-form');
  if (!form) return;

  const whatsapp = $('pref-whatsapp-enabled');
  const sms = $('pref-sms-enabled');
  const primary = $('pref-primary-channel');
  const message = $('channel-prefs-message');

  // TEMPORARY: stored in this browser only until the backend has an endpoint for it.
  // Replace loadChannelPrefs / saveChannelPrefs with API calls when that exists.
  const CHANNEL_KEY = 'aalsikke_channel_prefs';
  const DEFAULTS = { whatsappEnabled: false, smsEnabled: false, primaryChannel: 'IN_APP' };

  function loadChannelPrefs() {
    try {
      return { ...DEFAULTS, ...JSON.parse(localStorage.getItem(CHANNEL_KEY) || '{}') };
    } catch (_) {
      return { ...DEFAULTS };
    }
  }

  function saveChannelPrefs(prefs) {
    localStorage.setItem(CHANNEL_KEY, JSON.stringify(prefs));
  }

  function showMessage(text, type = '') {
    message.textContent = text;
    message.className = `modal-message${type ? ` is-${type}` : ''}`;
  }

  function showToast(text) {
    const toast = $('toast');
    if (!toast) return;
    toast.textContent = text;
    toast.classList.add('is-visible', 'show');
    setTimeout(() => toast.classList.remove('is-visible', 'show'), 4000);
  }

  const saved = loadChannelPrefs();
  whatsapp.checked = saved.whatsappEnabled;
  sms.checked = saved.smsEnabled;
  primary.value = saved.primaryChannel;

  [whatsapp, sms, primary].forEach(input =>
    input.addEventListener('change', () => showMessage('')));

  form.addEventListener('submit', event => {
    event.preventDefault();

    // The primary channel has to be switched on, otherwise nothing would ever be sent through it
    if (primary.value === 'WHATSAPP' && !whatsapp.checked) {
      showMessage('Turn on WhatsApp notifications, or choose a different primary channel.', 'error');
      return;
    }
    if (primary.value === 'SMS' && !sms.checked) {
      showMessage('Turn on SMS notifications, or choose a different primary channel.', 'error');
      return;
    }

    saveChannelPrefs({
      whatsappEnabled: whatsapp.checked,
      smsEnabled: sms.checked,
      primaryChannel: primary.value
    });
    showMessage('');
    showToast('Notification channels saved.');
  });
});