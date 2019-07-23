package com.andreasgift.edisco;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.document.FirebaseVisionCloudDocumentRecognizerOptions;
import com.google.firebase.ml.vision.document.FirebaseVisionDocumentText;
import com.google.firebase.ml.vision.document.FirebaseVisionDocumentTextRecognizer;

import java.util.Arrays;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private ImageView image;
    private TextView textView;

    private final int freeQuota = 50;
    private int unitRead;
    private SharedPreferences sharedPref;
    private final String KEY_PREFS = "unit read shared preferences";

    private final Locale language = Locale.ENGLISH;

    private final String TAG = "MainActivity";

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private TextToSpeech tts;

    private Bitmap samplebitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        unitRead = sharedPref.getInt(KEY_PREFS, 0);

        requestPermission(Manifest.permission.CAMERA);

        image = findViewById(R.id.image_view);
        textView = findViewById(R.id.text_view);
        samplebitmap = BitmapFactory.decodeResource(this.getResources(), R.drawable.samplebitmap);
    }

    /**
     * The sequence of method happen when README button is pressed
     * consist of method:
     * getPicture() extractText() textToSpeech()
     * @param v button view parents
     */
    public void OnClick (View v){
        if (unitRead <= freeQuota) {
            getPicture();
        } else {
            textView.setText(getResources().getText(R.string.quota_message));
            textToSpeech(getString(R.string.quota_message), getString(R.string.error_message));
        }
    }


    /**
     * This method will called intent to take picture
     * Upon success, it will call extractText() to process the image and read any text from it
     */
    private void getPicture() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null){
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap tempInput = (Bitmap) extras.get("data");
            image.setImageBitmap(tempInput);
            extractText(tempInput);
        }
    }


    /**
     * This method will extract any text from bitmap
     * Upon success, it will called textToSpeech() to read the text
     * @param input bitmap as input
     */
    private void extractText(Bitmap input) {
        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(input);
        FirebaseVisionCloudDocumentRecognizerOptions options =
                new FirebaseVisionCloudDocumentRecognizerOptions.Builder()
                        .setLanguageHints(Arrays.asList("en", "id"))
                        .build();
        FirebaseVisionDocumentTextRecognizer recognizer = FirebaseVision.getInstance().getCloudDocumentTextRecognizer(options);

        recognizer.processImage(image)
        .addOnSuccessListener(new OnSuccessListener<FirebaseVisionDocumentText>() {
            @Override
            public void onSuccess(FirebaseVisionDocumentText firebaseVisionDocumentText) {
                String resultText = firebaseVisionDocumentText.getText();
                Log.d(TAG,"Text : "+resultText);
                textToSpeech(resultText, getString(R.string.no_text_message));
            }
        })
        .addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.e(TAG,e.toString());
            }
        });
    }

    /**
     * This method will read input text
     * @param text      text that will be read by device
     * @param errorMssg error message wil be read by device when onInit fail
     */
    private void textToSpeech(final String text, final String errorMssg){
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS){
                    tts.setLanguage(language);
                    tts.setSpeechRate(0.6f);
                    if (text != null ){
                        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null,"TTS");
                        unitRead += 1;
                        sharedPref.edit().putInt(KEY_PREFS, unitRead).apply();
                        }
                    else {
                        tts.speak(errorMssg,TextToSpeech.QUEUE_FLUSH, null,"TTS");
                    }
                }
            }
        });
    }


    /**
     *  Request app permission for API 23/ Android 6.0
     * @param permission  string permission requested
     */
    @TargetApi(Build.VERSION_CODES.M)
    private void requestPermission(String permission){
        int MY_PERMISSIONS_REQUEST = 99;
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{permission},
                    MY_PERMISSIONS_REQUEST);
        }
    }


    /**
     * Release all the resources when the app is onPause state
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (tts != null){
            tts.stop();
            tts.shutdown();
        }
        image.setImageBitmap(null);
        textView.setText(null);
    }
}
