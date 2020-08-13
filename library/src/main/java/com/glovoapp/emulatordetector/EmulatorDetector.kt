package com.glovoapp.emulatordetector

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
class EmulatorDetector private constructor(private val context: Context) {

    var isDebug = false
        private set
    var isCheckTelephony = false
        private set
    var isCheckPackage = true
        private set

    val packageNameList: List<String>
        get() = packageNames

    private val packageNames: MutableList<String> = mutableListOf()

    fun setDebug(isDebug: Boolean): EmulatorDetector {
        this.isDebug = isDebug
        return this
    }

    fun setCheckTelephony(telephony: Boolean): EmulatorDetector {
        isCheckTelephony = telephony
        return this
    }

    fun setCheckPackage(chkPackage: Boolean): EmulatorDetector {
        isCheckPackage = chkPackage
        return this
    }

    fun addPackageName(pPackageName: String): EmulatorDetector {
        packageNames.add(pPackageName)
        return this
    }

    fun addPackageName(packageNames: List<String>): EmulatorDetector {
        this.packageNames.addAll(packageNames)
        return this
    }

    fun detect(pOnEmulatorDetectorListener: OnEmulatorDetectorListener) {
        Thread(Runnable {
            val isEmulator = detect()
            log("This System is Emulator: $isEmulator")
            pOnEmulatorDetectorListener?.onResult(isEmulator)
        }).start()
    }

    private fun detect(): Boolean {
        var result = false
        log(deviceInfo)
        if (!result) { // Check Basic
            result = checkBasic()
            log("Check basic $result")
        }
        if (!result) { // Check Advanced
            result = checkAdvanced()
            log("Check Advanced $result")
        }
        if (!result) { // Check Package Name
            result = checkPackageName()
            log("Check Package Name $result")
        }
        return result
    }

    private fun checkBasic(): Boolean {
        var result = (Build.FINGERPRINT.startsWith("generic")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.toLowerCase().contains("droid4x")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HARDWARE == "goldfish" || Build.HARDWARE == "vbox86"
                || Build.PRODUCT == "sdk"
                || Build.PRODUCT == "google_sdk"
                || Build.PRODUCT == "sdk_x86"
                || Build.PRODUCT == "vbox86p"
                || Build.BOARD.toLowerCase().contains("nox")
                || Build.BOOTLOADER.toLowerCase().contains("nox")
                || Build.HARDWARE.toLowerCase().contains("nox")
                || Build.PRODUCT.toLowerCase().contains("nox")
                || Build.SERIAL.toLowerCase().contains("nox"))
        if (result) return true
        result = result or (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
        if (result) return true
        result = result or ("google_sdk" == Build.PRODUCT)
        return result
    }

    private fun checkAdvanced(): Boolean {
        return (checkTelephony()
                || checkFiles(GENY_FILES, "Geny")
                || checkFiles(ANDY_FILES, "Andy")
                || checkFiles(NOX_FILES, "Nox")
                || checkQEmuDrivers()
                || checkFiles(PIPES, "Pipes")
                || checkIp()
                || checkQEmuProps()
                && checkFiles(X86_FILES, "X86"))
    }

    private fun checkPackageName(): Boolean {
        if (!isCheckPackage || packageNames.isEmpty()) {
            return false
        }
        val packageManager = context.packageManager
        for (pkgName in packageNames) {
            val tryIntent = packageManager.getLaunchIntentForPackage(pkgName)
            if (tryIntent != null) {
                val resolveInfos = packageManager.queryIntentActivities(tryIntent, PackageManager.MATCH_DEFAULT_ONLY)
                if (!resolveInfos.isEmpty()) {
                    return true
                }
            }
        }
        return false
    }

    private fun checkTelephony(): Boolean {
        return if ((ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                        == PackageManager.PERMISSION_GRANTED) && isCheckTelephony && isSupportTelePhony) {
            (checkPhoneNumber() || checkDeviceId() || checkImsi() || checkOperatorNameAndroid())
        } else false
    }

    private fun checkPhoneNumber(): Boolean {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        @SuppressLint("HardwareIds") val phoneNumber = telephonyManager.line1Number
        for (number in PHONE_NUMBERS) {
            if (number.equals(phoneNumber, ignoreCase = true)) {
                log(" check phone number is detected")
                return true
            }
        }
        return false
    }

    private fun checkDeviceId(): Boolean {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        @SuppressLint("HardwareIds") val deviceId = telephonyManager.deviceId
        for (known_deviceId in DEVICE_IDS) {
            if (known_deviceId.equals(deviceId, ignoreCase = true)) {
                log("Check device id is detected")
                return true
            }
        }
        return false
    }

    private fun checkImsi(): Boolean {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        @SuppressLint("HardwareIds") val imsi = telephonyManager.subscriberId
        for (known_imsi in IMSI_IDS) {
            if (known_imsi.equals(imsi, ignoreCase = true)) {
                log("Check imsi is detected")
                return true
            }
        }
        return false
    }

    private fun checkOperatorNameAndroid(): Boolean {
        val operatorName = (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).networkOperatorName
        if (operatorName.equals("android", ignoreCase = true)) {
            log("Check operator name android is detected")
            return true
        }
        return false
    }

    private fun checkQEmuDrivers(): Boolean {
        for (drivers_file in arrayOf(File("/proc/tty/drivers"), File("/proc/cpuinfo"))) {
            if (drivers_file.exists() && drivers_file.canRead()) {
                val data = ByteArray(1024)
                try {
                    val inputStream: InputStream = FileInputStream(drivers_file)
                    inputStream.read(data)
                    inputStream.close()
                } catch (exception: Exception) {
                    exception.printStackTrace()
                }
                val driverData = String(data)
                for (known_qemu_driver in QEMU_DRIVERS) {
                    if (driverData.contains(known_qemu_driver)) {
                        log("Check QEmuDrivers is detected")
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun checkFiles(targets: Array<String>, type: String): Boolean {
        for (pipe in targets) {
            val qemuFile = File(pipe)
            if (qemuFile.exists()) {
                log("Check $type is detected")
                return true
            }
        }
        return false
    }

    private fun checkQEmuProps(): Boolean {
        var foundProps = 0
        for ((name, seek_value) in PROPERTIES) {
            val propertyValue = getProp(context, name)
            if (seek_value == null && propertyValue != null) {
                foundProps++
            }
            if (seek_value != null
                    && propertyValue?.contains(seek_value) == true) {
                foundProps++
            }
        }
        if (foundProps >= MIN_PROPERTIES_THRESHOLD) {
            log("Check QEmuProps is detected")
            return true
        }
        return false
    }

    private fun checkIp(): Boolean {
        var ipDetected = false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED) {
            val args = arrayOf("/system/bin/netcfg")
            val stringBuilder = StringBuilder()
            try {
                val builder = ProcessBuilder(*args)
                builder.directory(File("/system/bin/"))
                builder.redirectErrorStream(true)
                val process = builder.start()
                val inputStream = process.inputStream
                val re = ByteArray(1024)
                while (inputStream.read(re) != -1) {
                    stringBuilder.append(String(re))
                }
                inputStream.close()
            } catch (ex: Exception) {
                // empty catch
            }
            val netData = stringBuilder.toString()
            log("netcfg data -> $netData")
            if (!TextUtils.isEmpty(netData)) {
                val array = netData.split("\n".toRegex()).toTypedArray()
                for (lan in array) {
                    if ((lan.contains("wlan0") || lan.contains("tunl0") || lan.contains("eth0"))
                            && lan.contains(IP)) {
                        ipDetected = true
                        log("Check IP is detected")
                        break
                    }
                }
            }
        }
        return ipDetected
    }

    private fun getProp(context: Context, property: String): String? {
        try {
            val classLoader = context.classLoader
            val systemProperties = classLoader.loadClass("android.os.SystemProperties")
            val get = systemProperties.getMethod("get", String::class.java)
            val params = arrayOfNulls<Any>(1)
            params[0] = property
            return get.invoke(systemProperties, *params) as String
        } catch (exception: Exception) {
            // empty catch
        }
        return null
    }

    private val isSupportTelePhony: Boolean
        get() {
            val packageManager = context.packageManager
            val isSupport = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
            log("Supported TelePhony: $isSupport")
            return isSupport
        }

    private fun log(str: String) {
        if (isDebug) {
            Log.d(javaClass.name, str)
        }
    }

    companion object {
        private val PHONE_NUMBERS = arrayOf(
                "15555215554", "15555215556", "15555215558", "15555215560", "15555215562", "15555215564",
                "15555215566", "15555215568", "15555215570", "15555215572", "15555215574", "15555215576",
                "15555215578", "15555215580", "15555215582", "15555215584"
        )
        private val DEVICE_IDS = arrayOf(
                "000000000000000",
                "e21833235b6eef10",
                "012345678912345"
        )
        private val IMSI_IDS = arrayOf(
                "310260000000000"
        )
        private val GENY_FILES = arrayOf(
                "/dev/socket/genyd",
                "/dev/socket/baseband_genyd"
        )
        private val QEMU_DRIVERS = arrayOf("goldfish")
        private val PIPES = arrayOf(
                "/dev/socket/qemud",
                "/dev/qemu_pipe"
        )
        private val X86_FILES = arrayOf(
                "ueventd.android_x86.rc",
                "x86.prop",
                "ueventd.ttVM_x86.rc",
                "init.ttVM_x86.rc",
                "fstab.ttVM_x86",
                "fstab.vbox86",
                "init.vbox86.rc",
                "ueventd.vbox86.rc"
        )
        private val ANDY_FILES = arrayOf(
                "fstab.andy",
                "ueventd.andy.rc"
        )
        private val NOX_FILES = arrayOf(
                "fstab.nox",
                "init.nox.rc",
                "ueventd.nox.rc"
        )
        private val PROPERTIES = arrayOf(
                Property("init.svc.qemud", null),
                Property("init.svc.qemu-props", null),
                Property("qemu.hw.mainkeys", null),
                Property("qemu.sf.fake_camera", null),
                Property("qemu.sf.lcd_density", null),
                Property("ro.bootloader", "unknown"),
                Property("ro.bootmode", "unknown"),
                Property("ro.hardware", "goldfish"),
                Property("ro.kernel.android.qemud", null),
                Property("ro.kernel.qemu.gles", null),
                Property("ro.kernel.qemu", "1"),
                Property("ro.product.device", "generic"),
                Property("ro.product.model", "sdk"),
                Property("ro.product.name", "sdk"),
                Property("ro.serialno", null)
        )
        private const val IP = "10.0.2.15"
        private const val MIN_PROPERTIES_THRESHOLD = 0x5

        @SuppressLint("StaticFieldLeak") //Since we use application context now this won't leak memory anymore. This is only to please Lint
        private lateinit var mEmulatorDetector: EmulatorDetector
        fun with(context: Context): EmulatorDetector {
            if (!::mEmulatorDetector.isInitialized) mEmulatorDetector = EmulatorDetector(context.applicationContext)
            return mEmulatorDetector
        }

        val deviceInfo: String
            get() = """
                Build.PRODUCT: ${Build.PRODUCT}
                Build.MANUFACTURER: ${Build.MANUFACTURER}
                Build.BRAND: ${Build.BRAND}
                Build.DEVICE: ${Build.DEVICE}
                Build.MODEL: ${Build.MODEL}
                Build.HARDWARE: ${Build.HARDWARE}
                Build.FINGERPRINT: ${Build.FINGERPRINT}
                """.trimIndent()
    }

    init {
        packageNames.add("com.google.android.launcher.layouts.genymotion")
        packageNames.add("com.bluestacks")
        packageNames.add("com.bignox.app")
    }
}
