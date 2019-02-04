package com.example.miyatama.siahato

import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.SeekBar
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat

class MainActivity : AppCompatActivity(), ActivityCompat.OnRequestPermissionsResultCallback {

    private val tag = MainActivity::class.java.simpleName
    private lateinit var cameraBridgeViewBase: CameraBridgeViewBase
    private lateinit var loaderCallback :BaseLoaderCallback
    private var colorThresholdUpper = 255
    private var colorThresholdLower = 0
    private val cvCameraViewListener = object: CvCameraViewListener2{
        override fun onCameraViewStopped() {
            Log.d(tag, "onCameraViewStopped")
        }

        override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
            if (inputFrame == null) {
                return Mat()
            }
            val inputMat = inputFrame.rgba()
            val matAddress = inputMat.nativeObjAddr
            // Log.w(tag, "call detect left hand.upper: $colorThresholdUpper, lower: $colorThresholdLower")
            val detected = detectLeftHand(matAddress, colorThresholdUpper, colorThresholdLower)
            if (detected) {
                val sp = getSharedPreferences("siahato_data", Context.MODE_PRIVATE)
                val editor = sp.edit()
                editor.putInt("color_threshold_upper", colorThresholdUpper)
                editor.putInt("color_threshold_lower", colorThresholdLower)
                editor.apply()
            }
            return inputMat
        }

        override fun onCameraViewStarted(width: Int, height: Int) {
            Log.w(tag, "onCameraViewStarted.{width: $width,height:$height}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val seekBarUpper = findViewById<SeekBar>(R.id.seekBarUpper)
        seekBarUpper.thumbOffset = 255
        seekBarUpper.setOnSeekBarChangeListener(
            object: SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    colorThresholdUpper = progress
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                }
            }
        )
        val seekBarLower = findViewById<SeekBar>(R.id.seekBarLower)
        seekBarLower.thumbOffset = 0
        seekBarLower.setOnSeekBarChangeListener(
            object: SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    colorThresholdLower = progress
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                }

            }
        )
        val btnThresholdConfirm = findViewById<Button>(R.id.btnThresholdConfirm)
        btnThresholdConfirm.setOnClickListener {
            cameraBridgeViewBase.disableView()
            val intent = Intent(applicationContext, CcbyActivity::class.java)
            startActivity(intent)
        }

        cameraBridgeViewBase = findViewById(R.id.surfaceView)
        cameraBridgeViewBase.visibility = android.view.SurfaceView.VISIBLE
        cameraBridgeViewBase.setMaxFrameSize(720, 480)
        // cameraBridgeViewBase.setMaxFrameSize(256, 170)
        cameraBridgeViewBase.setCvCameraViewListener(cvCameraViewListener )
        loaderCallback = object:BaseLoaderCallback(this){
            override fun onManagerConnected(status: Int) {
                when (status) {
                    LoaderCallbackInterface.SUCCESS -> {
                        cameraBridgeViewBase.enableView()
                    }
                    else -> {
                        super.onManagerConnected(status)
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        cameraBridgeViewBase.disableView()
    }

    override fun onResume() {
        super.onResume()
        if (!OpenCVLoader.initDebug()){
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, loaderCallback )
        } else {
            loaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraBridgeViewBase.disableView()
    }
    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    external fun detectLeftHand(matAddr: Long, upperThreshold: Int, lowerThreshold: Int): Boolean

    companion object {

        // Used to load the 'native-lib' library on application startup.
        init {
            System.loadLibrary("native-lib")
        }
    }
}
