package com.example.investa

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.investa.utils.ThemeManager

class SplashActivity : AppCompatActivity() {
    private val splashHandler = Handler(Looper.getMainLooper())
    private val openMainActivity = Runnable {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        splashHandler.postDelayed(openMainActivity, 1_200L)
    }

    override fun onDestroy() {
        splashHandler.removeCallbacks(openMainActivity)
        super.onDestroy()
    }
}
