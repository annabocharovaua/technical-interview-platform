class Router {
    constructor() {
        this.routes = {};
        this.views = {};
        this.routeDocs = {};
        this.loadingViews = {};
        this.navigationToken = 0;
        this.appDiv = document.getElementById('app');
        if (!this.appDiv) return;
        window.addEventListener('popstate', () => this.handleRoute());
        document.addEventListener('click', e => {
            if (e.defaultPrevented) return;
            const link = e.target.closest('a[href], [data-link][href]');
            if (!link) return;
            if (link.target && link.target !== '_self') return;
            if (link.hasAttribute('download')) return;
            const rawHref = link.getAttribute('href');
            if (!rawHref || rawHref.startsWith('mailto:') || rawHref.startsWith('tel:')) return;
            if (rawHref === '#' || rawHref.startsWith('#')) return;
            const url = new URL(rawHref, window.location.href);
            if (url.origin !== window.location.origin) return;
            if (url.pathname.includes('.') && !url.pathname.endsWith('.html')) return;
            e.preventDefault();
            this.navigate(url.pathname + url.search + url.hash);
        });
    }
    addRoute(path, templatePath) {
        this.routes[path] = { templatePath };
    }
    normalizePath(path) {
        const clean = (path || '').split('?')[0].split('#')[0];
        return clean.replace(/\/$/, '') || '/';
    }
    navigate(path) {
        const url = new URL(path, window.location.origin);
        const normalized = this.normalizePath(url.pathname);
        const browserPath = `${normalized}${url.hash || ''}`;
        if (`${window.location.pathname}${window.location.hash}` !== browserPath) {
            window.history.pushState({ path: normalized }, '', browserPath);
        }
        this.handleRoute();
    }
    async ensureRouteLoaded(path, route) {
        if (this.views[path]) {
            const cachedDoc = this.routeDocs[path];
            if (cachedDoc) {
                await this.loadHeadAssets(cachedDoc, path);
            }
            return this.views[path];
        }
        if (this.loadingViews[path]) {
            return this.loadingViews[path];
        }
        const loadPromise = (async () => {
            const response = await fetch(route.templatePath);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const html = await response.text();
            const parser = new DOMParser();
            const doc = parser.parseFromString(html, 'text/html');
            this.routeDocs[path] = doc;
            const view = document.createElement('div');
            view.dataset.route = path;
            view.style.display = 'none';
            const content = (doc.querySelector('[data-content]') || doc.querySelector('main') || doc.body).cloneNode(true);
            content.querySelectorAll('script').forEach(s => s.remove());
            content.removeAttribute('style');
            view.innerHTML = '';
            if (content.tagName && content.tagName.toLowerCase() === 'body') {
                view.innerHTML = content.innerHTML;
            } else {
                view.appendChild(content);
            }
            this.appDiv.appendChild(view);
            this.views[path] = view;
            await this.loadHeadAssets(doc, path);
            await this.runScripts(doc, path);
            return view;
        })();
        this.loadingViews[path] = loadPromise;
        try {
            return await loadPromise;
        } finally {
            delete this.loadingViews[path];
        }
    }
    async loadHeadAssets(doc, path) {
        const links = Array.from(doc.querySelectorAll('head link[rel="stylesheet"]'));
        for (const link of links) {
            const absHref = new URL(link.getAttribute('href'), window.location.origin).toString();
            if (document.head.querySelector(`link[data-spa-style="${absHref}"]`)) continue;
            const el = document.createElement('link');
            el.rel = 'stylesheet';
            el.href = absHref;
            el.setAttribute('data-spa-style', absHref);
            el.setAttribute('data-spa-route', path);
            document.head.appendChild(el);
        }
        const styles = Array.from(doc.querySelectorAll('head style'));
        styles.forEach((style, index) => {
            const key = `${path}-${index}`;
            if (document.head.querySelector(`style[data-spa-style="${key}"]`)) return;
            const el = document.createElement('style');
            el.textContent = style.textContent;
            el.setAttribute('data-spa-style', key);
            el.setAttribute('data-spa-route', path);
            document.head.appendChild(el);
        });
    }
    async runScripts(doc, path) {
        const scripts = Array.from(doc.querySelectorAll('script'));
        for (const script of scripts) {
            if (script.src) {
                const absSrc = new URL(script.getAttribute('src'), window.location.origin).toString();
                if (document.querySelector(`script[src="${absSrc}"]`) ||
                    document.head.querySelector(`script[data-spa-src="${absSrc}"]`)) continue;
                await new Promise((resolve, reject) => {
                    const el = document.createElement('script');
                    el.src = absSrc;
                    el.async = false;
                    el.setAttribute('data-spa-src', absSrc);
                    el.setAttribute('data-spa-route', path);
                    el.onload = resolve;
                    el.onerror = reject;
                    document.head.appendChild(el);
                });
            } else if (script.textContent && script.textContent.trim()) {
                const el = document.createElement('script');
                el.textContent = script.textContent;
                el.setAttribute('data-spa-route', path);
                this.appDiv.appendChild(el);
            }
        }
        document.dispatchEvent(new CustomEvent('spa:scripts:loaded', { detail: { path } }));
    }
    cleanupHeadAssets(activePath) {
        const selector = 'link[data-spa-route], style[data-spa-route]';
        document.head.querySelectorAll(selector).forEach(el => {
            if (el.getAttribute('data-spa-route') !== activePath) {
                el.remove();
            }
        });
    }
    async handleRoute() {
        const navToken = ++this.navigationToken;
        const path = this.normalizePath(window.location.pathname);
        const route = this.routes[path] || this.routes['/'];
        const globalHeader = document.getElementById('globalUserHeader');
        if (globalHeader) {
            const hideHeader = (path === '/' || path === '/home' || path === '/home.html');
            globalHeader.style.display = hideHeader ? 'none' : '';
        }
        if (!route) {
            this.appDiv.innerHTML = '<h1>404 - Page Not Found</h1>';
            return;
        }
        try {
            const view = await this.ensureRouteLoaded(path, route);
            if (navToken !== this.navigationToken) return;
            Object.values(this.views).forEach(v => {
                v.style.display = 'none';
            });
            view.style.display = 'block';
            this.cleanupHeadAssets(path);
            document.dispatchEvent(new CustomEvent('spa:route', { detail: { path } }));
            window.scrollTo(0, 0);
        } catch (err) {
            this.appDiv.innerHTML = `<div class="error"><h2>Error Loading Page</h2><p>${err.message}</p></div>`;
        }
    }
}
const router = new Router();
window.appRouter = router;
window.navigateTo = function(path) {
    if (window.appRouter) {
        window.appRouter.navigate(path);
    } else {
        window.location.href = path;
    }
};
router.addRoute('/', '/home.html');
router.addRoute('/home', '/home.html');
router.addRoute('/login', '/login.html');
router.addRoute('/profile', '/profile.html');
router.addRoute('/interview', '/interview.html');
router.addRoute('/avatar', '/avatar.html');
router.addRoute('/results', '/results.html');
router.addRoute('/live-coding', '/live-coding.html');
router.addRoute('/payment', '/payment.html');
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => router.handleRoute(), { once: true });
} else {
    router.handleRoute();
}