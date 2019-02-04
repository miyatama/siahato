package com.example.miyatama.siahato

import android.content.Context
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Toast
import com.google.ar.core.Session
import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
import android.net.Uri
import android.os.*
import android.os.Build.VERSION_CODES
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log.*
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Button
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.exceptions.*
import com.google.ar.core.*
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import org.opencv.android.Utils
import org.opencv.core.CvType
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import org.opencv.core.Mat

class ShiahatoActivity: AppCompatActivity(){
    private val TAG = ShiahatoActivity::class.java.simpleName
    private lateinit var arSceneView:ArSceneView
    private lateinit var standOnView: View
    private lateinit var nextStage: Button
    private lateinit var siahatoRenderable: ModelRenderable
    private lateinit var handler: Handler
    private var installRequested = false
    private val RC_PERMISSIONS = 0x123

    private val DO_DETECT_SIAHATO = 1000
    private val DETECT_LEFT_HAND = 1001
    private val DETECT_SIAHATO = 1002

    private var status = 0
    private val STATUS_NORMAL = 0
    private val STATUS_DOING = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!checkIsSupportedDeviceOrFinish(this)) {
            return
        }
        requestCameraPermission(this, RC_PERMISSIONS)

        setContentView(R.layout.activity_siahato)
        arSceneView = findViewById(R.id.ar_scene_view)
        standOnView = findViewById(R.id.standOnView)
        nextStage = findViewById(R.id.nextStage)

        standOnView.visibility = View.INVISIBLE

        nextStage.setOnClickListener {
            val standOnMessage = Message.obtain()
            standOnMessage.what = DETECT_SIAHATO
            handler.sendMessage(standOnMessage)
        }

        handler = @SuppressLint("HandlerLeak")
        object: Handler() {
            override fun handleMessage(msg: Message?) {
                if (msg != null) {
                    when {
                        msg.what == DO_DETECT_SIAHATO -> {
                            if(detectLeftHandFromArSceneView()){
                                val message = Message.obtain()
                                message.what = DETECT_LEFT_HAND
                                handler.sendMessage(message)
                            }
                        }
                        msg.what == DETECT_LEFT_HAND -> {
                            status = STATUS_DOING
                            standOnView.visibility = View.VISIBLE
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                            val recognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext)
                            recognizer.setRecognitionListener(object: RecognitionListener {
                                override fun onReadyForSpeech(p0: Bundle?) { }
                                override fun onRmsChanged(p0: Float) { }
                                override fun onBufferReceived(p0: ByteArray?) { }
                                override fun onPartialResults(p0: Bundle?) { }
                                override fun onEvent(p0: Int, p1: Bundle?) { }
                                override fun onBeginningOfSpeech() { }
                                override fun onEndOfSpeech() { }
                                override fun onError(p0: Int) {
                                    standOnView.visibility = View.INVISIBLE
                                    status = STATUS_NORMAL
                                }
                                override fun onResults(results: Bundle?) {
                                    if (results == null) {
                                        status = STATUS_NORMAL
                                        return
                                    }
                                    val texts = results!!.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                                    var detectKeyword = false
                                    for (text in texts) {
                                        Log.w(TAG, "recognize: $text")
                                        if (text == "シエアハートアタック" || text == "シアハートアタック" || text == "第二の爆弾"){
                                            standOnView.visibility = View.INVISIBLE
                                            val standOnMessage = Message.obtain()
                                            standOnMessage.what = DETECT_SIAHATO
                                            handler.sendMessage(standOnMessage)
                                            detectKeyword = true
                                        }
                                    }
                                    if (!detectKeyword){
                                        status = STATUS_NORMAL
                                    }
                                }
                            })
                            recognizer.startListening(intent)
                        }
                        msg.what == DETECT_SIAHATO -> {
                            val frame = arSceneView.arFrame
                            if (frame != null) {
                                trySiahato(frame)
                            }
                            status = STATUS_NORMAL
                        }
                    }
                }
            }
        }
        val timerRunnable = object: Runnable {
            override fun run() {
                if (status == STATUS_NORMAL){
                    val message = Message.obtain()
                    message.what = DO_DETECT_SIAHATO
                    handler.sendMessage(message)
                }
                handler.postDelayed(this, 5000)
            }
        }
        handler.post(timerRunnable)

        val siahatoStage =
        ModelRenderable.builder().setSource(
            this,
            Uri.parse("car_02.sfb")).build() as CompletableFuture<ModelRenderable>
        siahatoStage
            .handle { _, throwable ->
                if (throwable != null) {
                    displayError(this, "Unable to load renderable", throwable)
                    return@handle
                }

                try {
                    siahatoRenderable = siahatoStage.get()
                } catch (ex :InterruptedException ) {
                    displayError(this, "Unable to load renderable", ex)
                }catch(ex:ExecutionException ){
                    displayError(this, "Unable to load renderable", ex)
                }
                return@handle
            }

    }

    override fun onResume() {
        super.onResume()

        if (arSceneView.session == null) {
            try {
                val session = createArSession(this, installRequested)
                if (session == null) {
                    installRequested = hasCameraPermission(this)
                    return
                } else {
                    arSceneView.setupSession(session)
                }
            } catch (e :UnavailableException) {
                handleSessionException(this, e)
            }
        }

        try {
            status = STATUS_NORMAL
            arSceneView.resume()
        } catch (ex:CameraNotAvailableException ) {
            finish()
            return
        }
    }

    override fun onPause() {
        super.onPause()
        arSceneView.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        arSceneView.destroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!hasCameraPermission(this)) {
            if (!shouldShowRequestPermissionRationale(this)) {
                launchPermissionSettings(this)
            } else {
                Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show()
            }
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window
                .decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun trySiahato(frame: Frame){
        if (frame.camera.trackingState == TrackingState.TRACKING) {
            val posX = arSceneView.width / 2.0
            val posY = arSceneView.height / 2.0
            frame.hitTest(posX.toFloat(), posY.toFloat()).forEach { it ->
                val trackable = it.trackable
                when {
                    trackable is Plane ->{
                       if (trackable.isPoseInPolygon(it.hitPose)){
                           val anchor = it.createAnchor()
                           val anchorNode = AnchorNode(anchor)
                           anchorNode.setParent(arSceneView.scene)
                           val siahato = createSiahato()
                           anchorNode.addChild(siahato)
                           return@forEach
                       }
                    }
                }
            }
        }
    }

    private fun createSiahato(): Node{
        val base = Node()

        val siahato = Siahato(this, 0.1f, siahatoRenderable)
        siahato.setParent(base)
        siahato.localPosition= Vector3(0.0f, 0.0f, 0.2f)

        return base
    }

    private fun detectLeftHandFromArSceneView() : Boolean {
        var bitmap = createBitmap(arSceneView.width, arSceneView.height, Bitmap.Config.ARGB_8888)

        PixelCopy.request(arSceneView, bitmap, {
            if (it == PixelCopy.SUCCESS) {
                var mat = Mat(bitmap.width, bitmap.height, CvType.CV_8UC4)
                Utils.bitmapToMat(bitmap, mat)
                val matAddress = mat.nativeObjAddr

                val sp = getSharedPreferences("siahato_data", Context.MODE_PRIVATE)
                val colorThresholdUpper = sp.getInt("color_threshold_upper", 0)
                val colorThresholdLower = sp.getInt("color_threshold_lower", 0)
                val detected = detectLeftHand(matAddress, colorThresholdUpper, colorThresholdLower)
                if (detected) {
                    val message = Message.obtain()
                    message.what = DETECT_LEFT_HAND
                    handler.sendMessage(message)
                }
            }else{
                e(TAG, "copy bitmap error")
            }
        },
        handler)
        return true
    }

    // From Demo Utils
    private val MIN_OPENGL_VERSION = 3.0
    private fun displayError(context: Context, errorMsg:String, problem: Throwable? ) {
        val tag = context::class.java.simpleName
        var toastText: String
        if (problem != null && problem!!.message != null) {
            e(tag, errorMsg, problem)
            toastText = errorMsg + ": " + problem.message
        } else if (problem != null) {
            e(tag, errorMsg, problem)
            toastText = errorMsg
        } else {
            e(tag, errorMsg)
            toastText = errorMsg
        }

        Handler(Looper.getMainLooper())
            .post {
                val toast = Toast.makeText(context, toastText, Toast.LENGTH_LONG)
                toast.setGravity(Gravity.CENTER, 0, 0)
                toast.show()
            }
    }

    private fun createArSession(activity:AppCompatActivity, installRequested: Boolean):Session? {
        var session:Session? = null
        if (hasCameraPermission(activity)) {
            val permissionState = (ArCoreApk.getInstance().requestInstall(activity, !installRequested))
            when (permissionState) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> return null
            }
            session = Session(activity)
            var config = Config(session)
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            session.configure(config)
        }
        return session
    }

    private fun requestCameraPermission(activity: AppCompatActivity, requestCode: Int) {
        ActivityCompat.requestPermissions(
            activity, arrayOf(Manifest.permission.CAMERA), requestCode)
    }

    private fun hasCameraPermission(activity : AppCompatActivity) :Boolean{
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun shouldShowRequestPermissionRationale(activity: AppCompatActivity):Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    }

    private fun launchPermissionSettings(activity: AppCompatActivity) {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        intent.data = Uri.fromParts("package", activity.packageName, null)
        activity.startActivity(intent)
    }

    private fun handleSessionException(activity: AppCompatActivity, sessionException: UnavailableException) {

        var message: String
        when (sessionException) {
            is UnavailableArcoreNotInstalledException -> message = "Please install ARCore"
            is UnavailableApkTooOldException -> message = "Please update ARCore"
            is UnavailableSdkTooOldException -> message = "Please update this app"
            is UnavailableDeviceNotCompatibleException -> message = "This device does not support AR"
            else -> {
                message = "Failed to create AR session"
                e(TAG, "Exception: $sessionException")
            }
        }
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    private fun checkIsSupportedDeviceOrFinish(activity: AppCompatActivity):Boolean {
        if (Build.VERSION.SDK_INT < VERSION_CODES.N) {
            e(TAG, "Sceneform requires Android N or later")
            Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show()
            activity.finish()
            return false
        }

        val openGlVersionString = (activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .deviceConfigurationInfo
            .glEsVersion
        if (openGlVersionString.toDouble() < MIN_OPENGL_VERSION) {
            e(TAG, "Sceneform requires OpenGL ES 3.0 later")
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG).show()
            activity.finish()
            return false
        }
        return true
    }

    // OpenCV
    external fun detectLeftHand(
        matAddress: Long,
        colorThresholdUpper: Int,
        colorThresholdLower: Int): Boolean

    companion object {

        // Used to load the 'native-lib' library on application startup.
        init {
            System.loadLibrary("native-lib")
        }
    }
}
