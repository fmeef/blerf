package net.ballmerlabs.lesnoop.scan

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectIgnoreHelper @Inject constructor() {
    private val ignoreSet = ConcurrentHashMap<String, Boolean>()
    private val ignoreQueue = ConcurrentLinkedQueue<String>()


    companion object {
        const val MAX_SIZE = 2048
    }

    fun shouldIgnore(mac: String): Boolean {
        ignoreQueue.add(mac)
        if (ignoreQueue.size > MAX_SIZE) {
            val remov = ignoreQueue.poll()
            if (remov != null) {
                ignoreSet.remove(remov)
            }
        }
        return ignoreSet.put(mac, true) == null
    }

    fun dump() {
        ignoreSet.clear()
        ignoreQueue.clear()
    }

    fun forget(mac: String) {
        ignoreSet.remove(mac)
    }
}