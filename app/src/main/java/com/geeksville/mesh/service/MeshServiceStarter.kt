package com.geeksville.mesh.service

import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.work.*
import com.geeksville.andlib.BuildConfig
import java.util.concurrent.TimeUnit

/**
 * Helper that calls MeshService.startService()
 */
class ServiceStarter(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result = try {
        MeshService.startService(this.applicationContext)

        // Indicate whether the task finished successfully with the Result
        Result.success()
    } catch (ex: Exception) {
        MeshService.errormsg("failure starting service, will retry", ex)
        Result.retry()
    }
}

private fun Context.startMeshService(): ComponentName? {
    val intent = MeshService.createIntent()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(intent)
    } else {
        startService(intent)
    }
}

/**
 * Just after boot the android OS is super busy, so if we call startForegroundService then, our
 * thread might be stalled long enough to expose this Google/Samsung bug:
 * https://issuetracker.google.com/issues/76112072#comment56
 */
fun MeshService.Companion.startServiceLater(context: Context) {
    // No point in even starting the service if the user doesn't have a device bonded
    info("Received boot complete announcement, starting mesh service in two minutes")
    val delayRequest = OneTimeWorkRequestBuilder<ServiceStarter>()
        .setInitialDelay(2, TimeUnit.MINUTES)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
        .addTag("startLater")
        .build()

    WorkManager.getInstance(context).enqueue(delayRequest)
}

/// Helper function to start running our service
fun MeshService.Companion.startService(context: Context) {
    // Bind to our service using the same mechanism an external client would use (for testing coverage)
    // The following would work for us, but not external users:
    // val intent = Intent(this, MeshService::class.java)
    // intent.action = IMeshService::class.java.name

    // Before binding we want to explicitly create - so the service stays alive forever (so it can keep
    // listening for the bluetooth packets arriving from the radio. And when they arrive forward them
    // to Signal or whatever.
    info("Trying to start service debug=${BuildConfig.DEBUG}")
    requireNotNull(context.startMeshService()) { "Failed to start service" }
}
