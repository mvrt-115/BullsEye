package com.mvrt.bullseye;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.mvrt.bullseye.util.Notifier;

public class MainActivity extends AppCompatActivity  {

    private static final int REQUEST_PERMISSIONS_CAMERA = 1;

    MVRTCameraView cameraView;
    ProcessingOutputView processingOutputView;

    BullseyeCameraManager bullseyeCameraManager;

    CameraPermissionsListener listener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraView = (MVRTCameraView)findViewById(R.id.mvrt_cameraview);
        processingOutputView = (ProcessingOutputView)findViewById(R.id.mvrt_processingoutput);

        bullseyeCameraManager = new BullseyeCameraManager(getApplicationContext(), cameraView, processingOutputView);
        listener = bullseyeCameraManager;

        initCameraPerms();
    }

    @Override
    public void onResume(){
        super.onResume();
        bullseyeCameraManager.init();
        setUIFlags();
    }

    @Override
    public void onPause(){
        super.onPause();
        bullseyeCameraManager.close();
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        bullseyeCameraManager.close();
    }

    private void setUIFlags() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    /**
     * Initializes camera by ensuring permissions are granted, then
     */
    private void initCameraPerms() {
        //check if we have permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            //if we don't, request permissions
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_PERMISSIONS_CAMERA);
        } else {
            //permissions have been granted
            CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            listener.onCameraPermissionsGranted(cameraManager);
        }
    }

    /**
     * Called on permissions request result, called from initCameraPerms() if needed
     * Calls initCameraPerms() again on success
     * */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSIONS_CAMERA: {
                if (grantResults.length > 0  && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Notifier.log(Log.INFO, this, "Yay! We got permission!");
                    initCameraPerms();
                } else {
                    Notifier.snack(this, "We need your permission to use the camera :(", Snackbar.LENGTH_LONG);
                }
            }
        }
    }


    public interface CameraPermissionsListener{
        void onCameraPermissionsGranted(CameraManager manager);
    }


}
