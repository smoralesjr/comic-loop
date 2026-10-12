package com.comicloop.app

import android.app.Application

class ComicLoopApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: Application
            private set
    }
}
