package com.glovoapp.emulatordetector

import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.PermissionRequest
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.framgia.android.emulator.BuildConfig

class MainActivity : AppCompatActivity() {
    private var textView: TextView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        textView = findViewById<View>(R.id.text) as TextView
        textView!!.text = "Checking...."
    }

    //    @NeedsPermission(Manifest.permission.READ_PHONE_STATE)
    fun checkEmulatorDetector() {
        checkWith(true)
    }

    //    @OnShowRationale(Manifest.permission.READ_PHONE_STATE)
    fun showRationaleForCamera(request: PermissionRequest?) {
//        new AlertDialog.Builder(this)
//            .setMessage("Need READ_PHONE_STATE permission for check with Telephony function")
//            .setPositiveButton("Allow", (dialog, button) -> request.proceed())
//            .setNegativeButton("Deny", (dialog, button) -> request.cancel())
//            .show();
    }

    private fun checkWith(telephony: Boolean) {
        EmulatorDetector.with(this)
                .setCheckTelephony(telephony)
                .addPackageName("com.bluestacks")
                .setDebug(true)
                .detect(object : OnEmulatorDetectorListener {
                    override fun onResult(isEmulator: Boolean) {
                        runOnUiThread {
                            if (isEmulator) {
                                textView!!.text = ("This device is emulator"
                                        + checkInfo)
                            } else {
                                textView!!.text = ("This device is not emulator"
                                        + checkInfo)
                            }
                        }
                        Log.d(javaClass.name, "Running on emulator --> $isEmulator")
                    }
                })
    }

    //    @OnPermissionDenied(Manifest.permission.READ_PHONE_STATE)
    fun showDeniedForCamera() {
        checkWith(false)
        Toast.makeText(this, "We check without Telephony function", Toast.LENGTH_SHORT).show()
    }

    //    @OnNeverAskAgain(Manifest.permission.READ_PHONE_STATE)
    fun showNeverAskForCamera() {
        Toast.makeText(this, "Never check with Telephony function", Toast.LENGTH_SHORT).show()
    }

    private val checkInfo: String
        private get() = """

            Telephony enable is ${EmulatorDetector.with(this@MainActivity).isCheckTelephony}


            ${EmulatorDetector.deviceInfo}

            Emulator Detector version ${BuildConfig.VERSION_NAME}
            """.trimIndent()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        //        MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }
}