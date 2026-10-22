// KAN-385: one show/hide eye for every password field.
// Any <button class="icon-toggle" data-toggle-for="FIELD_ID"> shows or hides that field.
// When a modal closes (it gets the "hidden" attribute), its password fields are hidden again.
(function () {
    function setShown(input, btn, shown) {
        input.type = shown ? 'text' : 'password';
        btn.setAttribute('aria-pressed', String(shown));
    }

    // One listener for the whole page, so it also works for buttons in modals.
    document.addEventListener('click', (e) => {
        const btn = e.target.closest('[data-toggle-for]');
        if (!btn) return;
        const input = document.getElementById(btn.dataset.toggleFor);
        if (!input) return;
        setShown(input, btn, input.type === 'password');
    });

    function hideAllIn(root) {
        root.querySelectorAll('[data-toggle-for]').forEach((btn) => {
            const input = document.getElementById(btn.dataset.toggleFor);
            if (input) setShown(input, btn, false);
        });
    }

    document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('.modal-backdrop').forEach((modal) => {
            new MutationObserver(() => { if (modal.hidden) hideAllIn(modal); })
                .observe(modal, { attributes: true, attributeFilter: ['hidden'] });
        });
    });
})();