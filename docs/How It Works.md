# How It Works

RedditTV reads Reddit the way Reddit's own web page does, as a signed-in browser — no API key, no
OAuth app, nothing hosted.

## Session

- The user signs in on `https://www.reddit.com/login/` inside the app's own browser
  (`SignInBrowser`: a WebView on Android, the Chrome/Edge window CDP drives on desktop).
- That browser keeps its cookies in the platform store. `WebSession` reads them back:
  `cookies(url)` for a request, `isSignedIn(SignInSite.REDDIT)` checks for `reddit_session`.
- `RedditClient` sends those cookies plus the browser's own user agent on every call. A session is
  safest used by the client that made it.
- Signing out drops the site's cookies (`WebSession.signOut`).

Signed out, reddit turns `.json` away from most networks (403 "blocked by network security",
checked 11/11 from a Mac with curl, any User-Agent), and `old.reddit.com` redirects a signed-out
visitor to `/login/?reason=lor2` — so neither the API nor the scrape works. See **Signed out:
RSS** below.

## Reading

Every reddit page has a JSON view: add `.json`. All requests add `raw_json=1` so text isn't
HTML-escaped.

| What | URL |
|---|---|
| Home feed | `/{sort}.json?limit=25&after=…` |
| A community | `/r/{name}/{sort}.json` |
| A post + comments | `{permalink}.json?limit=200` → `[post listing, comment listing]` |
| Subscriptions | `/subreddits/mine/subscriber.json` (signed out: `/subreddits/popular.json`) |
| Who's signed in + modhash | `/api/me.json` |

Reddit turns some `.json` paths away with a 403 "blocked by network security" page even with a
good session — seen 11/11 on a Fire TV: `/hot.json`, `/new.json`, `/rising.json` (and the same
under `/r/x/`) are refused while `/.json`, `/r/x.json`, `/top.json` and `/best.json` answer. So:

- **Hot uses the bare listing** (`/r/x.json`, `/.json`), which is hot order anyway. `?sort=` on
  it is ignored, so it can't stand in for the other sorts.
- **Anything that still fails falls back to scraping** (`RedditScraper`): the same page from
  `old.reddit.com` is loaded in the hidden browser (`PageScraper`: an off-screen WebView on
  Android, a headless CDP tab on desktop, none on iOS) with the session's cookies, and a script
  reads it back into the `.json` shape so `RedditParser` is unchanged. Old reddit keeps most of a
  post in `data-*` attributes but not its media, so scraped posts are then looked up in
  `/api/info.json?id=t3_…` for the full post; if that's refused too, they show as links with
  thumbnails.

### Signed out: RSS

When a `.json` request fails and there's no session, `RedditClient` reads the page's RSS (Atom)
feed instead (`RedditRss`), the same route CastToTV takes for a shared reddit post:

- A feed is `{path}/.rss?limit=25&after=t3_…` (home: `/.rss`); a post is `{permalink}/.rss`, whose
  first entry is the post and the rest (up to ~50) its comments.
- An entry has the title, author, community, time, thumbnail and the post's `[link]` — **no
  score, comment count or media detail**. Counts are `Post.UNKNOWN_COUNT` and the UI hides them.
  The link gives the media: `i.redd.it` → image (thumbnail as the preview), `v.redd.it/<id>` →
  video (`<id>/HLSPlaylist.m3u8`, no token needed), `reddit.com/gallery/…` → its cover image only,
  the post itself → text post (body from the entry's HTML), anything else → link.
- Comments come flat (no reply depth) and without scores.
- RSS is rate limited hard (a second request straight after the first gets 429). Requests go one
  at a time, at least 2s apart, and a 429 waits out `x-ratelimit-reset` (8s at most) once.
- The menu's communities (`/subreddits/popular.json`, also refused) fall back to a fixed list.
  Search (`subreddit_autocomplete_v2`) has no fallback.

`RedditParser` maps a post's media:

- `secure_media.reddit_video` → `PostMedia.Video` with the **HLS** url (the mp4 fallback has no
  audio). ExoPlayer plays it on Android.
- `is_gallery` → `PostMedia.Gallery`, ordered by `gallery_data`, urls from `media_metadata`.
- `post_hint == image` or an image extension → `PostMedia.Image` (+ a ~960px preview for tiles).
- a crosspost reads its media from `crosspost_parent_list[0]`.
- otherwise a self post (`None`) or a `Link` with a thumbnail.

i.redd.it / v.redd.it answer 403 without a reddit referer: media requests send
`REDDIT_MEDIA_HEADERS`.

## Writing

Votes and saves post a form to `/api/vote` / `/api/save` with the session cookies and the
modhash (`uh` field + `X-Modhash` header) from `/api/me.json`. The UI updates first and rolls back
on failure.

## Signing in on a TV with a phone

Typing a password with a remote is miserable, and we don't host anything, so the TV is the server:

1. `SignInScreen` starts `PhoneSignInServer` — ktor-server-cio on port 8765 — and shows a QR code
   for `http://<tv-lan-ip>:8765/s/<random-token>`.
2. The phone opens it, the user types their username and password, the page posts them back.
3. The TV shows reddit's login page in its WebView and runs `loginAutofillScript()` on it: it walks
   the page's shadow roots (reddit's form is `faceplate-*` web components), fills both fields and
   presses Log In.
4. Anything extra (2FA, a captcha) is finished on the TV with the remote.
5. The WebView sees the session cookie once it leaves `/login` and calls back; the server stops.

The token stops other devices on the LAN from posting; the page is plain HTTP on the LAN, and the
credentials only live in memory until the WebView has used them. The user can also sign in on the
TV directly, or browse signed out.

## Known gaps / next steps

- The old.reddit scrape scripts (`RedditScraper`) need checking against the live pages on a real
  TV; an NSFW community's "are you over 18" page reads as an empty feed.
- The autofill selectors (`input[name=username|password]`, a "Log In" button) need checking against
  reddit's live login page on a real TV.
- iOS has no sign-in browser yet (`WebSession.isSupported` is false) — it browses signed out.
- "N more replies" comment stubs aren't loaded (`/api/morechildren`).
- Video tiles show a poster; autoplay on focus (muted) in the grid isn't built yet — videos play on
  the post screen.
- Markdown in self text and comments is shown as plain text.
