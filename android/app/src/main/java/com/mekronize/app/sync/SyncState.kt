package com.mekronize.app.sync

import kotlinx.coroutines.flow.MutableStateFlow

data class SyncUi(
    val running: Boolean = false,
    val message: String = "",
    val doneItems: Int = 0,
    val totalItems: Int = 0,
    val itemName: String = "",
    val itemPct: Int = 0,
    val errors: List<String> = emptyList(),
    val finished: Boolean = false,
)

object SyncState {
    val flow = MutableStateFlow(SyncUi())
    inline fun update(f: (SyncUi) -> SyncUi) { flow.value = f(flow.value) }
}
