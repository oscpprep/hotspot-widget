package com.altmansoftwaredesign.hotspotwidget

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.altmansoftwaredesign.hotspotwidget.util.appVersionName

/** Small launcher page explaining the single-switch widget. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<TextView>(R.id.version_info).text =
            getString(R.string.setup_version, appVersionName())
    }
}
