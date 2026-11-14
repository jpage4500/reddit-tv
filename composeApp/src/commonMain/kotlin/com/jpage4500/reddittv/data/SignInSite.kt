package com.jpage4500.reddittv.data

/**
 * a site the user signs in to in a browser inside the app ([com.jpage4500.reddittv.ui.dialog.SignInDialog]).
 * that browser keeps its cookies in the platform cookie store, which [com.jpage4500.reddittv.util.WebSession]
 * reads so [com.jpage4500.reddittv.manager.RedditClient] can fetch pages as that user
 *
 * @param sessionCookie present only while signed in
 */
enum class SignInSite(
    val label: String,
    val loginUrl: String,
    val homeUrl: String,
    val sessionCookie: String,
    private val hosts: List<String>,
) {
    REDDIT("Reddit", "https://www.reddit.com/login/", "https://www.reddit.com/", "reddit_session", listOf("reddit.com", "redd.it")),
    ;

    companion object {
        private val HOST = Regex("""^https?://([^/?#:]+)""", RegexOption.IGNORE_CASE)

        /** the site [url] is on, or null */
        fun fromUrl(url: String): SignInSite? {
            val host = HOST.find(url)?.groupValues?.get(1)?.lowercase() ?: return null
            return entries.firstOrNull { site -> site.hosts.any { host == it || host.endsWith(".$it") } }
        }
    }
}
