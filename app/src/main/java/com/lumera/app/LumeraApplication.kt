package com.lumera.app

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import org.acra.config.httpSender
import org.acra.config.toast
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import org.acra.sender.HttpSender
import javax.inject.Inject

@HiltAndroidApp
class LumeraApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var startupOptimizer: StartupOptimizer

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        initAcra {
            reportFormat = StringFormat.JSON

            httpSender {
                uri = BuildConfig.ACRA_URL
                httpMethod = HttpSender.Method.POST
                basicAuthLogin = "acra"
                basicAuthPassword = BuildConfig.ACRA_TOKEN
            }

            toast {
                text = getString(R.string.acra_toast_text)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startupOptimizer.warmup()
    }

    override fun newImageLoader(): ImageLoader = imageLoader
}