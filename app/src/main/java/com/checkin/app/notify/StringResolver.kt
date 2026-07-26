package com.checkin.app.notify

import android.content.Context

/**
 * Resolves a string resource id to text.
 *
 * The engagement layer holds its copy as resource ids so it stays localizable, but needing a
 * `Context` to read them is what kept [com.checkin.app.notify.engagement.NudgeDispatcher] off the
 * JVM test suite — and it is the one class in that layer whose failure is silent, since a nudge that
 * is logged but never posted looks identical in the data to one nobody acted on.
 */
fun interface StringResolver {
    fun get(resId: Int): String
}

class AndroidStringResolver(private val context: Context) : StringResolver {
    override fun get(resId: Int): String = context.getString(resId)
}
