package com.mekronize.app

import android.app.Application
import com.mekronize.app.data.Repo

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
    }
}
