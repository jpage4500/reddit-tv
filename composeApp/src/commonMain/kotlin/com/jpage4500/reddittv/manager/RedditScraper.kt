package com.jpage4500.reddittv.manager

import com.jpage4500.reddittv.util.LoggerConfig
import com.jpage4500.reddittv.util.PageScraper

private val log = LoggerConfig.createLogger("RedditScraper")

/**
 * the fallback for a `.json` request reddit turns away: the same page from old.reddit.com, loaded in
 * the app's hidden browser ([PageScraper]) with the session's cookies, read back into the shape the
 * `.json` view has — so [RedditParser] reads it unchanged.
 *
 * old reddit renders server-side and keeps most of a post in data-* attributes. what it lacks is
 * media detail (video urls, galleries): [RedditClient] asks /api/info.json for that afterwards
 */
object RedditScraper {
    private const val OLD_BASE = "https://old.reddit.com"

    val isSupported: Boolean get() = PageScraper.isSupported

    /** a feed page: [pathAndQuery] is the listing's path (no .json) plus its query */
    suspend fun listing(pathAndQuery: String): String? = scrape("$OLD_BASE$pathAndQuery", LISTING_JS)

    /** a post and its comments: [permalink] without .json */
    suspend fun post(permalink: String, limit: Int): String? =
        scrape("$OLD_BASE${permalink.trimEnd('/')}/?limit=$limit", POST_JS)

    private suspend fun scrape(url: String, script: String): String? {
        log.d("scrape: $url")
        return PageScraper.scrape(url, COMMON_JS + script)
    }

    // helpers shared by both scripts. ES5 only: Fire TV WebViews can be old
    private const val COMMON_JS = """
        var RTV = {
          abs: function (u) {
            if (!u) return '';
            if (u.indexOf('//') == 0) return 'https:' + u;
            if (u.charAt(0) == '/') return 'https://www.reddit.com' + u;
            return u;
          },
          text: function (el) { return el ? (el.innerText || el.textContent || '').trim() : ''; },
          // the up/down arrows' column says how the user voted
          likes: function (thing) {
            var col = thing.querySelector(':scope > .midcol');
            if (!col) return null;
            if (col.classList.contains('likes')) return true;
            if (col.classList.contains('dislikes')) return false;
            return null;
          },
          ready: function () {
            return document.readyState != 'loading' && location.hostname.indexOf('reddit.com') >= 0;
          },
          // a url's path, lower case, without its query
          path: function (u) { return (u || '').split('?')[0].split('#')[0].toLowerCase(); },
          isImage: function (u) { return /\.(jpe?g|png|gif|webp)/.test(RTV.path(u).slice(-5)); },
          num: function (el, name) { return el ? (parseInt(el.getAttribute(name)) || 0) : 0; },
          // a post's media, in the fields the .json view uses (preview, secure_media, gallery_data,
          // media_metadata). read from its expando: inline on a comments page, data-cachedhtml in a
          // feed — parsed with DOMParser, so nothing in it loads
          media: function (t, url, thumbUrl) {
            var out = {};
            var ex = t.querySelector('.expando');
            var root = null;
            if (ex) {
              var html = ex.getAttribute('data-cachedhtml');
              root = html ? new DOMParser().parseFromString(html, 'text/html') : ex;
            }
            var previewEl = root ? root.querySelector('img.preview, .media-preview img, video[poster]') : null;
            var previewUrl = previewEl ? RTV.abs(previewEl.getAttribute('src') || previewEl.getAttribute('poster')) : '';
            var w = RTV.num(previewEl, 'width'), h = RTV.num(previewEl, 'height');
            var poster = previewUrl || thumbUrl;
            if (poster) out.preview = { images: [{ source: { url: poster, width: w, height: h }, resolutions: [] }] };

            // reddit video: the player's HLS url, else the one v.redd.it/<id> always has
            var hls = '', mp4 = '';
            if (root) {
              var hv = root.querySelector('[data-hls-url]');
              if (hv) hls = hv.getAttribute('data-hls-url');
              var mv = root.querySelector('[data-mp4-url]');
              if (mv) mp4 = mv.getAttribute('data-mp4-url');
              if (!hls) {
                var src = root.documentElement ? root.documentElement.innerHTML : root.innerHTML;
                var mm = /https:\/\/v\.redd\.it\/[^"'\s<>]+\.m3u8[^"'\s<>]*/.exec(src || '');
                if (mm) hls = mm[0].replace(/&amp;/g, '&');
              }
            }
            var vp = RTV.path(url);
            if (!hls && vp.indexOf('https://v.redd.it/') == 0) {
              var vid = vp.substring('https://v.redd.it/'.length).split('/')[0];
              if (vid) hls = 'https://v.redd.it/' + vid + '/HLSPlaylist.m3u8';
            }
            if (hls) {
              out.secure_media = { reddit_video: { hls_url: hls, fallback_url: mp4 || null, width: w, height: h, duration: 0 } };
              return out;
            }

            // gallery: each tile's image, once (the strip and the main view repeat them)
            if (root) {
              var imgs = root.querySelectorAll('.media-gallery img, .gallery-tile img, .gallery-preview img');
              var items = [], meta = {}, seen = {};
              for (var i = 0; i < imgs.length; i++) {
                var img = imgs[i];
                var s = RTV.abs(img.getAttribute('src') || img.getAttribute('data-src'));
                if (!s || seen[RTV.path(s)]) continue;
                seen[RTV.path(s)] = true;
                var a = img.closest('a');
                var full = a && RTV.isImage(a.getAttribute('href')) ? RTV.abs(a.getAttribute('href')) : s;
                var id = 'm' + items.length;
                items.push({ media_id: id });
                meta[id] = { status: 'valid', s: { u: full, x: RTV.num(img, 'width'), y: RTV.num(img, 'height') }, p: [{ u: s, x: 640 }] };
              }
              if (items.length) {
                out.is_gallery = true;
                out.gallery_data = { items: items };
                out.media_metadata = meta;
                return out;
              }
            }

            // an image the link doesn't name as one (ie: a preview linking to the full size)
            if (previewEl && previewEl.tagName == 'IMG' && !RTV.isImage(url)) {
              var pa = previewEl.closest('a');
              if (pa && RTV.isImage(pa.getAttribute('href'))) {
                out.post_hint = 'image';
                out.url_overridden_by_dest = RTV.abs(pa.getAttribute('href'));
              }
            }
            return out;
          },
          post: function (t) {
            var d = t.dataset;
            var thumb = t.querySelector(':scope > a.thumbnail img');
            var thumbUrl = thumb ? RTV.abs(thumb.getAttribute('src')) : '';
            var flair = t.querySelector('.linkflairlabel');
            var self = t.querySelector(':scope > .entry .expando .usertext-body .md');
            var data = {
              id: (d.fullname || '').replace('t3_', ''),
              name: d.fullname,
              title: RTV.text(t.querySelector('a.title')),
              author: d.author || '[deleted]',
              subreddit: d.subreddit || '',
              score: parseInt(d.score) || 0,
              num_comments: parseInt(d.commentsCount) || 0,
              created_utc: (parseInt(d.timestamp) || 0) / 1000,
              permalink: d.permalink || '',
              url: RTV.abs(d.url),
              domain: d.domain || '',
              selftext: RTV.text(self),
              is_self: t.classList.contains('self'),
              over_18: d.nsfw == 'true',
              spoiler: d.spoiler == 'true',
              stickied: t.classList.contains('stickied'),
              link_flair_text: flair ? RTV.text(flair) : null,
              thumbnail: thumbUrl,
              likes: RTV.likes(t)
            };
            // one post's odd media shouldn't lose the post
            try {
              var media = RTV.media(t, data.url, thumbUrl);
              for (var k in media) data[k] = media[k];
            } catch (e) {}
            return { kind: 't3', data: data };
          }
        };
    """

    // a feed page → {"kind":"Listing","data":{"after":…,"children":[t3…]}}
    private const val LISTING_JS = """
        (function () {
          if (!RTV.ready()) return null;
          var table = document.getElementById('siteTable');
          if (!table) return document.readyState == 'complete' ? '' : null;
          var children = [];
          var things = table.querySelectorAll(':scope > div.thing.link');
          for (var i = 0; i < things.length; i++) {
            var t = things[i];
            if (t.classList.contains('promoted') || t.dataset.promoted == 'true') continue;
            children.push(RTV.post(t));
          }
          var next = document.querySelector('.nav-buttons .next-button a');
          var after = null;
          if (next) {
            var m = /[?&]after=([^&]+)/.exec(next.getAttribute('href') || '');
            if (m) after = decodeURIComponent(m[1]);
          }
          return JSON.stringify({ kind: 'Listing', data: { after: after, children: children } });
        })()
    """

    // a comments page → [post listing, comment listing], comments nested under "replies"
    private const val POST_JS = """
        (function () {
          if (!RTV.ready()) return null;
          var postEl = document.querySelector('#siteTable > div.thing.link');
          if (!postEl) return document.readyState == 'complete' ? '' : null;

          function comments(table, depth) {
            var out = [];
            if (!table) return out;
            var things = table.querySelectorAll(':scope > div.thing');
            for (var i = 0; i < things.length; i++) {
              var t = things[i];
              if (t.classList.contains('morechildren') || t.classList.contains('morerecursion')) {
                var m = /(\d+)/.exec(RTV.text(t.querySelector('a')));
                out.push({ kind: 'more', data: { id: t.dataset.fullname || '', name: t.dataset.fullname || '',
                  count: m ? parseInt(m[1]) : 1, depth: depth } });
                continue;
              }
              if (!t.classList.contains('comment')) continue;
              var entry = t.querySelector(':scope > .entry');
              var score = entry ? entry.querySelector('.tagline .score.unvoted') : null;
              var time = entry ? entry.querySelector('.tagline time') : null;
              var replies = comments(t.querySelector(':scope > .child > .sitetable'), depth + 1);
              out.push({ kind: 't1', data: {
                id: (t.dataset.fullname || '').replace('t1_', ''),
                name: t.dataset.fullname || '',
                author: t.dataset.author || '[deleted]',
                body: entry ? RTV.text(entry.querySelector('.usertext-body .md')) : '',
                score: score ? (parseInt(score.getAttribute('title')) || 0) : 0,
                created_utc: time ? Date.parse(time.getAttribute('datetime')) / 1000 : 0,
                depth: depth,
                likes: RTV.likes(t),
                replies: replies.length ? { kind: 'Listing', data: { children: replies } } : ''
              } });
            }
            return out;
          }

          var post = { kind: 'Listing', data: { children: [RTV.post(postEl)] } };
          var list = { kind: 'Listing', data: { children: comments(document.querySelector('.commentarea > .sitetable'), 0) } };
          return JSON.stringify([post, list]);
        })()
    """
}
