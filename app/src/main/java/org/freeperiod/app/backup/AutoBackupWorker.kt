package org.freeperiod.app.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import org.freeperiod.app.FreePeriodApp

/** Failures are recorded for the backup screen; the next period simply tries again. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as FreePeriodApp).container.autoBackup.run()
        return Result.success()
    }
}

private const val AUTO_BACKUP_WORK = "auto-backup"

/** Null turns automatic backup off. UPDATE keeps the current timing when nothing changed. */
fun scheduleAutoBackup(work: WorkManager, interval: AutoBackupInterval?) {
    if (interval == null) {
        work.cancelUniqueWork(AUTO_BACKUP_WORK)
        return
    }
    work.enqueueUniquePeriodicWork(AUTO_BACKUP_WORK, ExistingPeriodicWorkPolicy.UPDATE,
        PeriodicWorkRequestBuilder<AutoBackupWorker>(interval.days, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build())
}
