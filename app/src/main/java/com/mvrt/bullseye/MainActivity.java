package com.mvrt.bullseye;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.mvrt.bullseye.util.Notifier;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.Map;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2{

    CameraBridgeViewBase cvCameraView;

    private static final int REQUEST_PERMISSIONS_CAMERA = 1;

    private boolean cv_initialized = false;

    Mat srcMat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getPermissions();
        setUIFlags();

        cvCameraView = (JavaCameraView)findViewById(R.id.opencv_cameraview);
        cvCameraView.enableFpsMeter();
        cvCameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, this, cvLoaderCallback);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (cvCameraView != null)cvCameraView.disableView();
    }

    public void onDestroy() {
        super.onDestroy();
        Notifier.log(this, Log.ERROR, "FINISHED");
        if (cvCameraView != null)cvCameraView.disableView();
    }

    private void setUIFlags(){
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    public void onCameraViewStarted(int width, int height) {
    }

    public void onCameraViewStopped() {
        Notifier.log(this, Log.WARN, "CAMERA VIEW STOPPED");
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        srcMat = inputFrame.rgba();
        return srcMat;
    }

    private BaseLoaderCallback cvLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch(status) {
                case LoaderCallbackInterface.SUCCESS:
                    onCvInitialized();
                    break;
                default:
                    super.onManagerConnected(status);
            }
            super.onManagerConnected(status);
        }
    };

    private void onCvInitialized(){
        Notifier.log(MainActivity.this, Log.INFO, "OpenCV Initialized");
        cv_initialized = true;

        srcMat = new Mat();

        if(isCameraPermissionGranted()) {
            cvCameraView.enableView();
        }
    }

    private void getPermissions(){
        if (!isCameraPermissionGranted()){
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        REQUEST_PERMISSIONS_CAMERA);

        }
    }

    private boolean isCameraPermissionGranted(){
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSIONS_CAMERA: {
                if(cvCameraView != null)cvCameraView.disableView();
                if (grantResults.length > 0  && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Notifier.log(this, Log.INFO, "Yay! We got permission!");
                    if(cv_initialized && cvCameraView != null)cvCameraView.enableView();
                } else {
                    Notifier.snack(this, "We need your permission to use the camera :(", Snackbar.LENGTH_LONG);
                }
                return;
            }
        }
    }

}
