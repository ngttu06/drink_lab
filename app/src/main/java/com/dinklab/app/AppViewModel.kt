package com.dinklab.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dinklab.app.data.*
import com.dinklab.app.domain.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val db = LocalDatabase.open(application)
    val repository = DinkRepository(db)
    val items = repository.items.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val events = repository.events.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val actorId = MutableStateFlow<String?>(null)
    val ready = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val simulateDelay = MutableStateFlow(false)
    val simulateFailure = MutableStateFlow(false)
    val actor: StateFlow<Item?> = combine(items, actorId) { all, id -> all.find { it.id == id && !it.archived && it.status == "Hoạt động" } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            try {
                repository.initialize()
                simulateDelay.value = repository.setting("delay") == "true"
                ready.value = true
            } catch (e: Exception) { message.value = e.message ?: "Không thể mở dữ liệu" }
        }
    }

    fun login(id: String) { actorId.value = id }
    fun logout() { actorId.value = null }
    fun notify(text: String) { message.value = text }
    fun operation(success: String, done: () -> Unit = {}, action: suspend (String) -> Unit) {
        if (busy.value) return
        val id = actorId.value ?: return
        busy.value = true
        viewModelScope.launch {
            try {
                if (simulateDelay.value) delay(900)
                if (simulateFailure.value) {
                    simulateFailure.value = false
                    error("Lỗi demo: thao tác chưa được lưu. Nội dung form được giữ lại.")
                }
                action(id)
                message.value = success
                done()
            } catch (e: Exception) { message.value = e.message ?: "Thao tác thất bại" }
            finally { busy.value = false }
        }
    }

    fun save(item: Item, reason: String, done: () -> Unit) = operation("Đã lưu ${item.kind.singular}", done) { repository.save(item, it, reason) }
    fun delayEnabled(enabled: Boolean) {
        simulateDelay.value = enabled
        viewModelScope.launch { repository.setting("delay", enabled.toString()) }
    }

    override fun onCleared() { db.close(); super.onCleared() }
}