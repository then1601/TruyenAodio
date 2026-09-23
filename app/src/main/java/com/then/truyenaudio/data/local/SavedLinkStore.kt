package com.then.truyenaudio.data.local

import android.content.Context

object SavedLinkStore {
    private const val PREFERENCES_NAME = "saved_story_links"
    private const val LINKS_KEY = "links"
    const val MAX_LINKS = 100

    fun load(context: Context): List<String> =
        preferences(context)
            .getString(LINKS_KEY, "")
            .orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .take(MAX_LINKS)
            .toList()

    fun save(context: Context, url: String): List<String> {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) return load(context)
        val links = (listOf(normalizedUrl) + load(context))
            .distinct()
            .take(MAX_LINKS)
        persist(context, links)
        return links
    }

    fun delete(context: Context, url: String): List<String> {
        val links = load(context).filterNot { it == url }
        persist(context, links)
        return links
    }

    private fun persist(context: Context, links: List<String>) {
        preferences(context)
            .edit()
            .putString(LINKS_KEY, links.joinToString("\n"))
            .apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
