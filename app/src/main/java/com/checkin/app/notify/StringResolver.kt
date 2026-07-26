package com.checkin.app.notify

import android.content.Context

/**
 * Resolves a string resource id to text.
 *
 * The engagement layer holds its copy as resource ids so it stays localizable; this seam is what
 * keeps reading them from dragging a `Context` into
 * [com.checkin.app.notify.engagement.NudgeDispatcher], which is the one class in that layer with a
 * silent failure mode and so the one that most needs JVM tests.
 */
fun interface StringResolver {
    fun get(resId: Int): String
}

class AndroidStringResolver(private val context: Context) : StringResolver {
    override fun get(resId: Int): String = context.getString(resId)
}
