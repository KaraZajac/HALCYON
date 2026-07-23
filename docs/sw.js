// HALCYON service worker — the page is one self-contained file, so offline is
// simply: precache the shell, serve navigations network-first (fresh when
// online, cached in a tunnel), everything else cache-first.
var CACHE = 'halcyon-v1';
var SHELL = ['/', '/manifest.webmanifest', '/icon-192.png', '/icon-512.png', '/apple-touch-icon.png'];

self.addEventListener('install', function (e) {
	e.waitUntil(
		caches.open(CACHE).then(function (c) { return c.addAll(SHELL); }).then(function () {
			return self.skipWaiting();
		})
	);
});

self.addEventListener('activate', function (e) {
	e.waitUntil(
		caches.keys().then(function (keys) {
			return Promise.all(keys.filter(function (k) { return k !== CACHE; }).map(function (k) {
				return caches.delete(k);
			}));
		}).then(function () { return self.clients.claim(); })
	);
});

self.addEventListener('fetch', function (e) {
	var req = e.request;
	if (req.method !== 'GET') return;
	if (req.mode === 'navigate') {
		e.respondWith(
			fetch(req).then(function (res) {
				var copy = res.clone();
				caches.open(CACHE).then(function (c) { c.put('/', copy); });
				return res;
			}).catch(function () { return caches.match('/'); })
		);
		return;
	}
	e.respondWith(
		caches.match(req).then(function (hit) {
			return hit || fetch(req).then(function (res) {
				if (res.ok && new URL(req.url).origin === location.origin) {
					var copy = res.clone();
					caches.open(CACHE).then(function (c) { c.put(req, copy); });
				}
				return res;
			});
		})
	);
});
