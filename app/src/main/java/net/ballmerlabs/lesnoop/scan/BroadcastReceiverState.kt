package net.ballmerlabs.lesnoop.scan

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BroadcastReceiverState @Inject constructor() {
    val busy = AtomicBoolean()
}