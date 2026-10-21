// Aal Sikke — Settings page (KAN-313, KAN-314)
// dashboard.js (loaded first) already handles the profile modal, the top-bar menus,
// the Event alerts modal, and the close / Escape / backdrop behavior of every modal.
// This file wires the change-password button and the WhatsApp / SMS channel form.

document.addEventListener('DOMContentLoaded', () => {
  const $ = id => document.getElementById(id);

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
  const VALID_PRIMARY = ['WHATSAPP', 'SMS', 'IN_APP'];

  function loadChannelPrefs() {
    try {
      const p = { ...DEFAULTS, ...JSON.parse(localStorage.getItem(CHANNEL_KEY) || '{}') };
      p.whatsappEnabled = p.whatsappEnabled === true;
      p.smsEnabled = p.smsEnabled === true;
      if (!VALID_PRIMARY.includes(p.primaryChannel)) p.primaryChannel = DEFAULTS.primaryChannel;
      return p;
    } catch (_) {
      return { ...DEFAULTS };
    }
  }

  function saveChannelPrefs(prefs) {
    try {
      localStorage.setItem(CHANNEL_KEY, JSON.stringify(prefs));
      return true;
    } catch (_) {
      return false;
    }
  }

  function showMessage(text, type = '') {
    message.textContent = text;
    message.className = `modal-message${type ? ` is-${type}` : ''}`;
  }

  // Only allow a primary channel that is switched on
  function syncPrimaryOptions() {
    const on = { WHATSAPP: whatsapp.checked, SMS: sms.checked, IN_APP: true };
    [...primary.options].forEach(o => { o.disabled = !on[o.value]; });
    if (!on[primary.value]) primary.value = 'IN_APP';
  }

  const saved = loadChannelPrefs();
  whatsapp.checked = saved.whatsappEnabled;
  sms.checked = saved.smsEnabled;
  primary.value = saved.primaryChannel;
  syncPrimaryOptions();

  [whatsapp, sms].forEach(input =>
    input.addEventListener('change', () => { showMessage(''); syncPrimaryOptions(); }));
  primary.addEventListener('change', () => showMessage(''));

  form.addEventListener('submit', event => {
    event.preventDefault();

    // Safety net: the primary channel has to be switched on
    if (primary.value === 'WHATSAPP' && !whatsapp.checked) {
      showMessage(t('settings.notif.needWhatsapp'), 'error');
      return;
    }
    if (primary.value === 'SMS' && !sms.checked) {
      showMessage(t('settings.notif.needSms'), 'error');
      return;
    }

    const ok = saveChannelPrefs({
      whatsappEnabled: whatsapp.checked,
      smsEnabled: sms.checked,
      primaryChannel: primary.value
    });
    if (!ok) {
      showMessage(t('settings.notif.storageFailed'), 'error');
      return;
    }
    showMessage('');
    window.AalSikke.showToast(t('settings.notif.saved'));
  });
});