// Adds <link rel="canonical"> and og:url using the address the page is served from,
// so no domain has to be hard-coded in the HTML. Public pages only.
(function () {
    try {
        var path = location.pathname.replace(/index\.html$/, '');
        var url = location.origin + path;
        var link = document.createElement('link');
        link.rel = 'canonical';
        link.href = url;
        document.head.appendChild(link);
        var og = document.createElement('meta');
        og.setAttribute('property', 'og:url');
        og.content = url;
        document.head.appendChild(og);
        var img = document.querySelector('meta[property="og:image"]');
        if (img && !/^https?:/.test(img.content)) img.content = location.origin + '/' + img.content;
    } catch (e) { /* SEO tags are optional */ }
})();
