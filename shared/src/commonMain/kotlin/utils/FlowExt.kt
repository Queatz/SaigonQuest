package utils

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

fun <T> Flow<T>.throttleLatest(periodMillis: Long): Flow<T> = channelFlow {
    var lastValue: T? = null
    var timer: Job? = null

    collect { value ->
        lastValue = value
        if (timer == null) {
            timer = launch {
                delay(periodMillis)
                lastValue?.let { send(it) }
                timer = null
            }
        }
    }
}
