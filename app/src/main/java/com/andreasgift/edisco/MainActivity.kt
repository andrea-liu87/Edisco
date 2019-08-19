package com.andreasgift.edisco

import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.document.FirebaseVisionCloudDocumentRecognizerOptions
import com.google.firebase.ml.vision.document.FirebaseVisionDocumentText
import com.google.firebase.ml.vision.document.FirebaseVisionDocumentTextRecognizer

import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private var image: ImageView? = null
    private var textView: TextView? = null
    lateinit var mAdView: AdView

    private val freeQuota = 50
    private var unitRead: Int = 0
    private var sharedPref: SharedPreferences? = null
    private val KEY_PREFS = "unit read shared preferences"

    private val language = Locale.ENGLISH
    private val REQUEST_IMAGE_CAPTURE = 1

    private val TAG = "MainActivity"
    private var tts: TextToSpeech? = null

    private var imagePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        MobileAds.initialize(this)

        mAdView = findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        mAdView.loadAd(adRequest)

        sharedPref = this.getPreferences(Context.MODE_PRIVATE)
        unitRead = sharedPref!!.getInt(KEY_PREFS, 0)

        requestPermission(Manifest.permission.CAMERA)

        image = findViewById(R.id.image_view)
        textView = findViewById(R.id.text_view)
    }

    /**
     * The sequence of method happen when README button is pressed
     * consist of method:
     * getPicture() extractText() textToSpeech()
     * @param v button view parents
     */
    fun OnClick(v: View) {
        if (unitRead <= freeQuota) {
            try {
                getPicture()
            } catch (e: IOException) {
                e.printStackTrace()
            }

        } else {
            textView!!.text = resources.getText(R.string.quota_message)
            textToSpeech(getString(R.string.quota_message), getString(R.string.error_message))
        }
    }


    /**
     * This method will called intent to take picture
     * Upon success, it will call extractText() to process the image and read any text from it
     */
    @Throws(IOException::class)
    private fun getPicture() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Create the File where the photo should go
                val photoFile: File? = try {
                    createImageFile()
                } catch (e: IOException) {
                    e.printStackTrace()
                    null
                }
                // Continue only if the File was successfully created
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                            this,
                            "com.andreasgift.edisco",
                            it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }
    }

        @Throws(IOException::class)
        private fun createImageFile(): File {
            val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            return File.createTempFile(
                    "JPEG_${timeStamp}_", /* prefix */
                    ".jpg", /* suffix */
                    storageDir /* directory */
            ).apply {
                // Save a file: path for use with ACTION_VIEW intents
                imagePath = absolutePath
            }
        }


        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
                val bitmap = BitmapFactory.decodeFile(imagePath)
                image!!.setImageBitmap(bitmap)
                extractText(bitmap)
            }
        }


        /**
         * This method will extract any text from bitmap
         * Upon success, it will called textToSpeech() to read the text
         * @param input bitmap as input
         */
        private fun extractText(input: Bitmap) {
            val image = FirebaseVisionImage.fromBitmap(input)
            val options = FirebaseVisionCloudDocumentRecognizerOptions.Builder()
                    .setLanguageHints(Arrays.asList("en", "id"))
                    .build()
            val recognizer = FirebaseVision.getInstance().getCloudDocumentTextRecognizer(options)

            recognizer.processImage(image)
                    .addOnSuccessListener { firebaseVisionDocumentText ->
                        val resultText = firebaseVisionDocumentText.text
                        Log.d(TAG, "Text : $resultText")
                        textToSpeech(resultText, getString(R.string.no_text_message))
                    }
                    .addOnFailureListener { e -> Log.e(TAG, e.toString()) }
        }

        /**
         * This method will read input text
         * @param text      text that will be read by device
         * @param errorMssg error message wil be read by device when onInit fail
         */
        private fun textToSpeech(text: String?, errorMssg: String) {
            tts = TextToSpeech(this, TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts!!.language = language
                    tts!!.setSpeechRate(0.6f)
                    if (text != null) {
                        tts!!.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TTS")
                        unitRead += 1
                        sharedPref!!.edit().putInt(KEY_PREFS, unitRead).apply()
                    } else {
                        tts!!.speak(errorMssg, TextToSpeech.QUEUE_FLUSH, null, "TTS")
                    }
                }
            })
        }


        /**
         * Request app permission for API 23/ Android 6.0
         * @param permission  string permission requested
         */
        @TargetApi(Build.VERSION_CODES.M)
        private fun requestPermission(permission: String) {
            val MY_PERMISSIONS_REQUEST = 99
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        arrayOf(permission),
                        MY_PERMISSIONS_REQUEST)
            }
        }


        /**
         * Release all the resources when the app is onPause state
         */
        override fun onPause() {
            super.onPause()
            if (tts != null) {
                tts!!.stop()
                tts!!.shutdown()
            }
        }
    }

