package com.tans.tlocalvideochat.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tans.tlocalvideochat.R
import com.tans.tuiutils.systembar.annotation.ContentViewFitSystemWindow
import com.tans.tuiutils.systembar.annotation.SystemBarStyle

@SystemBarStyle
@ContentViewFitSystemWindow
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}