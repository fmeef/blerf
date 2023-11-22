package net.ballmerlabs.lesnoop

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import io.reactivex.rxjava3.plugins.RxJavaPlugins

@HiltAndroidApp
class MainApp : Application() {
    init {
        RxJavaPlugins.setErrorHandler { err: Throwable ->
            Log.e("global", "unhandled rxjava exception: $err")
            err.printStackTrace()
        }
    }
}