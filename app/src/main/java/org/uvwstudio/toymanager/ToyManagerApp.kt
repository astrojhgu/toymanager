package org.uvwstudio.toymanager

import android.app.Application
import android.util.Log
import java.io.File

class ToyManagerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val imageDir = File(filesDir, "images")
        if (!imageDir.exists()) {
            imageDir.mkdirs()
            Log.d("ToyManagerApp", "Image directory created at ${imageDir.absolutePath}")
        } else {
            Log.d("ToyManagerApp", "Image directory already exists: ${imageDir.absolutePath}")
        }
    }
}
