package com.charles.cruiseapp.util

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace
import kotlinx.coroutines.CancellationException

/**
 * Centralized Firebase Crashlytics & Performance helpers.
 * Wraps common operations with safe try/catch so app never crashes due to Firebase.
 */
object FirebaseCrashlyticsUtils {

    private const val TAG = "FirebaseCrash"

    fun log(message: String) {
        try {
            FirebaseCrashlytics.getInstance().log(message)
            Log.d(TAG, message)
        } catch (e: Exception) {
            Log.w(TAG, "Crashlytics log failed", e)
        }
    }

    fun recordException(throwable: Throwable) {
        // Don't record coroutine cancellation as crash
        if (throwable is CancellationException) return
        try {
            FirebaseCrashlytics.getInstance().recordException(throwable)
            Log.w(TAG, "Recorded non-fatal", throwable)
        } catch (e: Exception) {
            Log.w(TAG, "recordException failed", e)
        }
    }

    fun logNonFatal(throwable: Throwable, context: String? = null) {
        if (throwable is CancellationException) return
        try {
            if (context != null) FirebaseCrashlytics.getInstance().log("Non-fatal context: $context")
            FirebaseCrashlytics.getInstance().recordException(throwable)
        } catch (e: Exception) {
            Log.w(TAG, "logNonFatal failed", e)
        }
    }

    fun setCustomKey(key: String, value: String) {
        try { FirebaseCrashlytics.getInstance().setCustomKey(key, value) } catch (_: Exception) {}
    }
    fun setCustomKey(key: String, value: Int) {
        try { FirebaseCrashlytics.getInstance().setCustomKey(key, value) } catch (_: Exception) {}
    }
    fun setCustomKey(key: String, value: Long) {
        try { FirebaseCrashlytics.getInstance().setCustomKey(key, value) } catch (_: Exception) {}
    }
    fun setCustomKey(key: String, value: Boolean) {
        try { FirebaseCrashlytics.getInstance().setCustomKey(key, value) } catch (_: Exception) {}
    }
    fun setUserId(userId: String) {
        try { FirebaseCrashlytics.getInstance().setUserId(userId) } catch (_: Exception) {}
    }

    /** Force a test crash (debug only) */
    fun forceTestCrash(msg: String = "Test crash from FirebaseCrashlyticsUtils") {
        try {
            FirebaseCrashlytics.getInstance().log("Forcing test crash: $msg")
            throw RuntimeException(msg)
        } catch (e: RuntimeException) {
            // Record as non-fatal for testing pipeline without killing app, plus throw if you want real crash
            FirebaseCrashlytics.getInstance().recordException(e)
            throw e
        }
    }
}

object FirebasePerfUtils {

    private const val TAG = "FirebasePerf"

    /** Start a custom trace. Caller must call trace.stop() in finally. */
    fun startTrace(name: String): Trace? {
        return try {
            val trace = FirebasePerformance.getInstance().newTrace(name)
            trace.start()
            trace
        } catch (e: Exception) {
            Log.w(TAG, "startTrace $name failed", e)
            FirebaseCrashlyticsUtils.recordException(e)
            null
        }
    }

    /** Inline helper to trace a suspend block automatically. */
    suspend fun <T> trace(
        name: String,
        attributes: Map<String, String> = emptyMap(),
        block: suspend () -> T
    ): T {
        val trace = startTrace(name)
        attributes.forEach { (k, v) -> try { trace?.putAttribute(k, v) } catch (_: Exception) {} }
        val start = System.currentTimeMillis()
        return try {
            val result = block()
            trace?.putMetric("success", 1)
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            trace?.putMetric("error", 1)
            try { trace?.putAttribute("error", e.message ?: e.javaClass.simpleName) } catch (_: Exception) {}
            FirebaseCrashlyticsUtils.recordException(e)
            throw e
        } finally {
            try {
                val duration = System.currentTimeMillis() - start
                trace?.putMetric("duration_ms", duration)
                trace?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "trace stop failed", e)
            }
        }
    }

    /** Trace a synchronous block */
    inline fun <T> traceSync(
        name: String,
        attributes: Map<String, String> = emptyMap(),
        block: () -> T
    ): T {
        val trace = startTrace(name)
        attributes.forEach { (k, v) -> try { trace?.putAttribute(k, v) } catch (_: Exception) {} }
        val start = System.currentTimeMillis()
        return try {
            val result = block()
            trace?.putMetric("success", 1)
            result
        } catch (e: Exception) {
            trace?.putMetric("error", 1)
            try { trace?.putAttribute("error", e.message ?: e.javaClass.simpleName) } catch (_: Exception) {}
            FirebaseCrashlyticsUtils.recordException(e)
            throw e
        } finally {
            try {
                val duration = System.currentTimeMillis() - start
                trace?.putMetric("duration_ms", duration)
                trace?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "traceSync stop failed", e)
            }
        }
    }

    /** Helper for HTTP/network tracing (wraps OkHttp or Retrofit). Use for manual instrumentation. */
    fun newHttpMetric(url: String, method: String) = try {
        FirebasePerformance.getInstance().newHttpMetric(url, method)
    } catch (e: Exception) {
        Log.w(TAG, "newHttpMetric failed", e)
        null
    }
}
