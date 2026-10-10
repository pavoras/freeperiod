package org.freeperiod.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.freeperiod.app.R
import org.freeperiod.app.backup.AutoBackup
import org.freeperiod.app.backup.AutoBackupInterval
import org.freeperiod.app.data.SettingsStore

data class AutoBackupUiState(
    val on: Boolean = false,
    val folderName: String? = null,
    val interval: AutoBackupInterval = AutoBackupInterval.WEEKLY,
    val last: LocalDate? = null,
    val failed: Boolean = false,
    val busy: Boolean = false,
    val message: Int? = null,
    val error: Boolean = false,
)

class AutoBackupViewModel(private val backup: AutoBackup, settings: SettingsStore) : ViewModel() {
    private val local = MutableStateFlow(AutoBackupUiState())
    val state = combine(settings.settings, local) { device, ui ->
        ui.copy(on = device.autoBackupFolder != null, folderName = device.autoBackupFolderName,
            interval = if (device.autoBackupFolder != null) device.autoBackupInterval else ui.interval,
            last = device.autoBackupLast, failed = device.autoBackupFailed)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AutoBackupUiState())
    private val requests = Channel<Unit>(Channel.CONFLATED)
    /** One event per request to open the system folder picker. */
    val folderRequests = requests.receiveAsFlow()
    private var password: CharArray? = null

    fun choose(interval: AutoBackupInterval) {
        if (state.value.on) work { backup.setInterval(interval) } else local.update { it.copy(interval = interval) }
    }

    /** Checks the password first, so the folder picker only opens for a setup that can succeed. */
    fun start(password: String, confirm: String) {
        when {
            password.length < 8 -> message(R.string.backup_password_short, true)
            password != confirm -> message(R.string.backup_password_mismatch, true)
            else -> {
                clearPassword()
                this.password = password.toCharArray()
                local.update { it.copy(message = null, error = false) }
                requests.trySend(Unit)
            }
        }
    }

    fun folderChosen(uri: String?) {
        val secret = password ?: return
        password = null
        if (uri == null) { secret.fill('\u0000'); return }
        work {
            val ok = try { backup.enable(uri, secret, local.value.interval) } finally { secret.fill('\u0000') }
            message(if (ok) R.string.auto_backup_on_done else R.string.auto_backup_folder_error, !ok)
        }
    }

    fun backUpNow() = work {
        val ok = backup.run()
        message(if (ok) R.string.backup_saved else R.string.auto_backup_failed, !ok)
    }

    fun turnOff() = work { backup.turnOff(); message(R.string.auto_backup_off_done) }

    private fun work(action: suspend () -> Unit) {
        if (local.value.busy) return
        viewModelScope.launch {
            local.update { it.copy(busy = true, message = null, error = false) }
            try { action() } finally { local.update { it.copy(busy = false) } }
        }
    }

    private fun message(text: Int, error: Boolean = false) = local.update { it.copy(message = text, error = error) }

    private fun clearPassword() { password?.fill('\u0000'); password = null }

    override fun onCleared() { clearPassword(); requests.close() }
}
