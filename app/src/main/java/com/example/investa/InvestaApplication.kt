package com.example.investa

import android.app.Application
import com.example.investa.utils.ThemeManager

class InvestaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.apply(this)
    }
}
