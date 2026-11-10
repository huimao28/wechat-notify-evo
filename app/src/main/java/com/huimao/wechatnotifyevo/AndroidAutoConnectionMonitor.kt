package com.huimao.wechatnotifyevo

import android.content.Context
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Connection state reported by the public Android for Cars API. */
enum class AndroidAutoConnectionType {
    NOT_CONNECTED,
    NATIVE,
    PROJECTION,
    UNKNOWN,
}

data class AndroidAutoConnectionState(
    val type: AndroidAutoConnectionType = AndroidAutoConnectionType.UNKNOWN,
    val updatedAtMillis: Long? = null,
    val source: String = SOURCE,
) {
    companion object {
        const val SOURCE = "官方 CarConnection"
    }
}

object AndroidAutoConnectionStateStore {
    private val mutableState = MutableStateFlow(AndroidAutoConnectionState())
    val state: StateFlow<AndroidAutoConnectionState> = mutableState.asStateFlow()

    fun current(): AndroidAutoConnectionState = mutableState.value

    internal fun update(type: AndroidAutoConnectionType, nowMillis: Long = System.currentTimeMillis()) {
        mutableState.value = AndroidAutoConnectionState(type, nowMillis)
    }
}

/** Observes CarConnection only while the diagnostics lifecycle is active. */
class AndroidAutoConnectionMonitor(private val context: Context) {
    private var liveData: LiveData<Int>? = null
    private var observer: Observer<Int>? = null

    fun start(owner: LifecycleOwner) {
        stop()
        try {
            val connectionTypes = CarConnection(context.applicationContext).type
            val typeObserver = Observer<Int> { rawType ->
                AndroidAutoConnectionStateStore.update(mapConnectionType(rawType))
            }
            liveData = connectionTypes
            observer = typeObserver
            connectionTypes.observe(owner, typeObserver)
        } catch (_: RuntimeException) {
            AndroidAutoConnectionStateStore.update(AndroidAutoConnectionType.UNKNOWN)
        } catch (_: LinkageError) {
            AndroidAutoConnectionStateStore.update(AndroidAutoConnectionType.UNKNOWN)
        }
    }

    fun stop() {
        val currentLiveData = liveData
        val currentObserver = observer
        if (currentLiveData != null && currentObserver != null) {
            currentLiveData.removeObserver(currentObserver)
        }
        liveData = null
        observer = null
    }

    companion object {
        fun mapConnectionType(rawType: Int?): AndroidAutoConnectionType = when (rawType) {
            CarConnection.CONNECTION_TYPE_NOT_CONNECTED -> AndroidAutoConnectionType.NOT_CONNECTED
            CarConnection.CONNECTION_TYPE_NATIVE -> AndroidAutoConnectionType.NATIVE
            CarConnection.CONNECTION_TYPE_PROJECTION -> AndroidAutoConnectionType.PROJECTION
            else -> AndroidAutoConnectionType.UNKNOWN
        }
    }
}
