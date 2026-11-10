package com.druggradientchip.android.ui.assistant

import com.druggradientchip.android.ui.UiText

enum class AssistantHistoryRole { USER, ASSISTANT, SYSTEM }

data class AssistantHistoryEntry(val role: AssistantHistoryRole, val content: UiText)

object AssistantConversationStore {
    private val lock = Any()
    private val entries = mutableListOf<AssistantHistoryEntry>()

    var inputText: String = ""
        @Synchronized get
        @Synchronized set

    var lastPresentationKey: String? = null
        @Synchronized get
        @Synchronized set

    fun snapshot(): List<AssistantHistoryEntry> = synchronized(lock) { entries.toList() }

    fun add(entry: AssistantHistoryEntry) = synchronized(lock) {
        entries += entry
        if (entries.size > MAX_HISTORY) entries.removeAt(0)
    }

    fun clear() = synchronized(lock) {
        entries.clear()
        inputText = ""
        lastPresentationKey = null
    }

    private const val MAX_HISTORY = 100
}
