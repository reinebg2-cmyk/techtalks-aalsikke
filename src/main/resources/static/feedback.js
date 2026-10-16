// Aal Sikke — "Send feedback" button (Sprint 6, real-user round)
//
// Adds one small button to every app page (dashboard, tasks, calendar, settings).
// It opens the feedback form in a new tab, so nobody loses their place in the app.
//
// SETUP: paste the address of the feedback form in FEEDBACK_URL (Reine owns the form).
// While it is empty the button is not shown, so no broken link can go live.
(function () {

    const FEEDBACK_URL = '';

    if (!FEEDBACK_URL) {
        return;
    }

    function init() {
        if (document.getElementById('feedback-btn')) return;

        const link = document.createElement('a');
        link.id = 'feedback-btn';
        link.className = 'feedback-btn';
        link.href = FEEDBACK_URL;
        link.target = '_blank';
        link.rel = 'noopener noreferrer';
        link.setAttribute('aria-label', 'Send feedback (opens in a new tab)');

        const icon = document.createElement('span');
        icon.setAttribute('aria-hidden', 'true');
        icon.textContent = '\u{1F4AC}';

        link.append(icon, document.createTextNode(' Send feedback'));
        document.body.append(link);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
