// Aal Sikke — "Your data" on the Settings page (KAN-338)
// Loaded after i18n.js and dashboard.js on settings.html.

(function () {

    const TOKEN_KEY = 'aalsikke_token';
    const API_BASE = window.location.origin;
    const CONFIRMATION_WORD = 'DELETE';   // sent to the API as-is: never translate

    function el(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text !== undefined) node.textContent = text;
        return node;
    }

    function button(className, text) {
        const node = el('button', className, text);
        node.type = 'button';
        return node;
    }

    /** fetch with the signed-in user's token; 401 = session over, back to login. */
    async function call(path, options = {}) {
        const response = await fetch(`${API_BASE}${path}`, {
            ...options,
            headers: { Authorization: `Bearer ${localStorage.getItem(TOKEN_KEY)}`, ...(options.headers || {}) }
        });
        if (response.status === 401) {
            localStorage.removeItem(TOKEN_KEY);
            window.location.href = 'login.html';
            const expired = new Error(t('data.sessionExpired'));
            expired.sessionExpired = true;
            throw expired;
        }
        return response;
    }

    /** The API's error message(s): { message } or a list of { field, message }. */
    async function errorMessage(response) {
        const fallback = `${t('data.serverResponded')} ${response.status}`;
        try {
            const body = await response.json();
            if (Array.isArray(body)) return body.map(e => e.message).filter(Boolean).join(' ');
            return body.message || body.error || fallback;
        } catch (_) {
            return fallback;
        }
    }

    function fileNameFrom(response) {
        const header = response.headers.get('Content-Disposition') || '';
        const match = header.match(/filename="?([^";]+)"?/i);
        return match ? match[1] : `aal-sikke-data-${new Date().toISOString().slice(0, 10)}.json`;
    }

    document.addEventListener('DOMContentLoaded', () => {
        const after = document.getElementById('settings-palette-panel');
        if (!after || document.getElementById('settings-data-panel')) return;

        // ---------------- panel ----------------
        const panel = el('section', 'card settings-panel');
        panel.id = 'settings-data-panel';

        const intro = el('div');
        intro.append(el('h2', '', t('data.title')),
            el('p', 'card-subtitle', t('data.subtitle')));

        const actions = el('div', 'data-actions');
        const downloadBtn = button('btn-secondary', t('data.download'));
        downloadBtn.id = 'download-data-btn';
        const deleteBtn = button('btn-danger', t('data.delete'));
        deleteBtn.id = 'delete-account-btn';
        actions.append(downloadBtn, deleteBtn);

        const panelMessage = el('p', 'modal-message');
        panelMessage.id = 'data-message';
        panelMessage.setAttribute('role', 'status');
        panelMessage.setAttribute('aria-live', 'polite');

        panel.append(intro, actions, panelMessage);
        after.after(panel);

        function sayInPanel(text, type = '') {
            panelMessage.textContent = text;
            panelMessage.className = `modal-message${type ? ` is-${type}` : ''}`;
        }

        // ---------------- download ----------------
        downloadBtn.addEventListener('click', async () => {
            downloadBtn.disabled = true;
            sayInPanel(t('data.preparing'), 'info');
            try {
                const response = await call('/api/users/me/export');
                if (!response.ok) throw new Error(await errorMessage(response));
                const blob = await response.blob();
                const url = URL.createObjectURL(blob);
                const link = el('a');
                link.href = url;
                link.download = fileNameFrom(response);
                document.body.append(link);
                link.click();
                link.remove();
                setTimeout(() => URL.revokeObjectURL(url), 1000);
                sayInPanel(`${t('data.downloaded')} ${link.download}`, 'success');
            } catch (error) {
                if (error.sessionExpired) return;
                console.error('Data export failed:', error);
                sayInPanel(`${t('data.downloadFailed')} ${error.message}`, 'error');
            } finally {
                downloadBtn.disabled = false;
            }
        });

        // ---------------- delete window ----------------
        const modal = el('div', 'modal-backdrop');
        modal.id = 'delete-account-modal';
        modal.hidden = true;

        const card = el('div', 'modal-card');
        card.setAttribute('role', 'dialog');
        card.setAttribute('aria-modal', 'true');
        card.setAttribute('aria-labelledby', 'delete-account-title');

        const header = el('div', 'modal-header');
        const titles = el('div');
        const title = el('h2', '', t('data.delete'));
        title.id = 'delete-account-title';
        titles.append(title, el('p', '', t('data.modalSubtitle')));
        const closeBtn = button('modal-close', '✕');
        closeBtn.setAttribute('aria-label', t('data.close'));
        header.append(titles, closeBtn);

        const warning = el('div', 'delete-warning');
        warning.append(
            el('p', '', t('data.warning')),
            el('p', '', t('data.tip')));

        const form = el('form');
        form.noValidate = true;

        const passwordField = el('div', 'field');
        const passwordLabel = el('label', '', t('data.currentPassword'));
        passwordLabel.htmlFor = 'delete-password';
        const passwordWrap = el('div', 'input-wrap');
        const passwordInput = el('input');
        passwordInput.type = 'password';
        passwordInput.id = 'delete-password';
        passwordInput.autocomplete = 'current-password';
        passwordWrap.append(passwordInput);
        passwordField.append(passwordLabel, passwordWrap);

        const confirmField = el('div', 'field');
        const confirmLabel = el('label');
        confirmLabel.htmlFor = 'delete-confirmation';
        confirmLabel.append(t('data.typePrefix'), ' ', el('strong', '', CONFIRMATION_WORD), ' ', t('data.typeSuffix'));
        const confirmWrap = el('div', 'input-wrap');
        const confirmInput = el('input');
        confirmInput.type = 'text';
        confirmInput.id = 'delete-confirmation';
        confirmInput.autocomplete = 'off';
        confirmInput.spellcheck = false;
        confirmInput.dir = 'ltr';   // the word to type is always Latin
        confirmWrap.append(confirmInput);
        confirmField.append(confirmLabel, confirmWrap);

        const modalMessage = el('p', 'modal-message');
        modalMessage.setAttribute('role', 'alert');

        const formActions = el('div', 'form-actions');
        const cancelBtn = button('btn-secondary', t('data.cancel'));
        const confirmBtn = el('button', 'btn-danger', t('data.confirmDelete'));
        confirmBtn.type = 'submit';
        confirmBtn.id = 'confirm-delete-account-btn';
        confirmBtn.disabled = true;
        formActions.append(cancelBtn, confirmBtn);

        form.append(passwordField, confirmField, modalMessage, formActions);
        card.append(header, warning, form);
        modal.append(card);
        document.body.append(modal);

        function sayInModal(text, type = '') {
            modalMessage.textContent = text;
            modalMessage.className = `modal-message${type ? ` is-${type}` : ''}`;
        }

        function ready() {
            return passwordInput.value.length > 0 && confirmInput.value.trim() === CONFIRMATION_WORD;
        }

        function syncConfirmButton() {
            confirmBtn.disabled = !ready();
        }

        let busy = false;

        function openModal() {
            form.reset();
            sayInModal('');
            syncConfirmButton();
            modal.hidden = false;
            passwordInput.focus();
        }

        function closeModal() {
            if (busy) return;
            modal.hidden = true;
            form.reset();
            deleteBtn.focus();
        }

        passwordInput.addEventListener('input', syncConfirmButton);
        confirmInput.addEventListener('input', syncConfirmButton);
        deleteBtn.addEventListener('click', openModal);
        closeBtn.addEventListener('click', closeModal);
        cancelBtn.addEventListener('click', closeModal);
        modal.addEventListener('click', event => { if (event.target === modal) closeModal(); });
        document.addEventListener('keydown', event => {
            if (event.key === 'Escape' && !modal.hidden) closeModal();
        });

        form.addEventListener('submit', async event => {
            event.preventDefault();
            if (!ready() || busy) return;

            busy = true;
            confirmBtn.disabled = true;
            cancelBtn.disabled = true;
            confirmBtn.textContent = t('data.deleting');
            sayInModal('');

            try {
                const response = await call('/api/users/me', {
                    method: 'DELETE',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        currentPassword: passwordInput.value,
                        confirmation: confirmInput.value.trim(),
                        lang: document.documentElement.lang === 'ar' ? 'ar' : 'en'
                    })
                });
                if (response.status !== 204 && !response.ok) {
                    const problem = new Error(await errorMessage(response));
                    problem.expected = response.status === 400;
                    throw problem;
                }

                try {
                    localStorage.removeItem(TOKEN_KEY);
                    localStorage.removeItem('aalsikke_channel_prefs');
                } catch (_) { /* storage blocked: nothing to clear */ }
                window.location.href = 'login.html?account-deleted=1';
            } catch (error) {
                if (error.sessionExpired) return;
                if (!error.expected) console.error('Account deletion failed:', error);
                sayInModal(error.message, 'error');
                busy = false;
                cancelBtn.disabled = false;
                confirmBtn.textContent = t('data.confirmDelete');
                syncConfirmButton();
                passwordInput.focus();
            }
        });
    });
})();