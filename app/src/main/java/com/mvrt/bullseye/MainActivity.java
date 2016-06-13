package com.mvrt.bullseye;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.mvrt.bullseye.util.Notifier;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.Camera2Renderer;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private CameraBridgeViewBase cvCameraView;
    private CaptureRequest.Builder cameraPreviewRequestBuilder;
    private CaptureRequest cameraPreviewRequest;


    private static final int REQUEST_PERMISSIONS_CAMERA = 1;

    Mat srcMat;

    MVRTCameraView mvrtCameraView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setUIFlags();

        mvrtCameraView = (MVRTCameraView)findViewById(R.id.mvrt_cameraview);
    }

    @Override
    public void onResume() {
        super.onResume();
        initCamera();
    }

    @Override
    public void onPause() {
        super.onPause();
        mvrtCameraView.closeCamera();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mvrtCameraView.closeCamera();
    }

    private void setUIFlags() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    public void onCameraViewStarted(int width, int height) {
    }

    public void onCameraViewStopped() {
        Notifier.log(this, Log.WARN, "Camera View Stopped");
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        srcMat = inputFrame.rgba();
        return srcMat;
    }

    private BaseLoaderCallback cvLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    onCVLoaded();
                    break;
            }
            super.onManagerConnected(status);
        }
    };

    private void onCVLoaded() {
        Notifier.log(MainActivity.this, Log.INFO, "OpenCV Library Loaded");

        srcMat = new Mat();

        if(mvrtCameraView != null)mvrtCameraView.init();
    }

    private void initCamera() {
        if (!isCameraPermissionGranted()) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_PERMISSIONS_CAMERA);
        } else {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, this, cvLoaderCallback);
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
                    initCamera();
                } else {
                    Notifier.snack(this, "We need your permission to use the camera :(", Snackbar.LENGTH_LONG);
                }
                return;
            }
        }
    }

//    long exposureTime = 100;
//    long frameDuration = 1000;
//    int iso = 2000;
//
//    CameraDevice.StateCallback cameraCallback = new CameraDevice.StateCallback() {
//        @Override
//        public void onOpened(CameraDevice camera) {
//            try {
//                cameraPreviewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
//                cameraPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
//                cameraPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime);
//               // cameraPreviewRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, frameDuration);
//                cameraPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
//
//
//
//                Notifier.log(MainActivity.this, Log.DEBUG, "Camera Capture Requests Set");
//            } catch (CameraAccessException e) {
//                e.printStackTrace();
//            }
//        }
//
//        @Override
//        public void onDisconnected(CameraDevice camera) {
//
//        }
//
//        @Override
//        public void onError(CameraDevice camera, int error) {
//
//        }
//    };

}
