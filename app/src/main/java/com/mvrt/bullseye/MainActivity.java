package com.mvrt.bullseye;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.mvrt.bullseye.util.Notifier;

public class MainActivity extends AppCompatActivity  {

    private static final int REQUEST_PERMISSIONS_CAMERA = 1;

    BullseyeCameraManager bullseyeCameraManager;
    OutputSocketServer socketServer;

    CameraPermissionsListener listener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MVRTCameraView cameraView = (MVRTCameraView)findViewById(R.id.mvrt_cameraview);
        ProcessingOutputView processingOutputView = (ProcessingOutputView)findViewById(R.id.mvrt_processingoutput);

        setupOptionButtons();

        socketServer = new OutputSocketServer(5801);
        socketServer.start();

        bullseyeCameraManager = new BullseyeCameraManager(getApplicationContext(), cameraView, processingOutputView, socketServer);
        listener = bullseyeCameraManager;

        bullseyeCameraManager.init();

        Notifier.v(getClass(), "On Create");

        initCameraPerms();
    }

    private void setupOptionButtons() {
        ImageButton settingsDrawer = (ImageButton) findViewById(R.id.imagebutton_settingsdrawer);
        settingsDrawer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsDrawer();
            }
        });
    }

    @Override
    public void onResume(){
        super.onResume();
        Notifier.v(getClass(), "On Resume");
        bullseyeCameraManager.resume();
        setUIFlags();
    }

    @Override
    public void onPause(){
        super.onPause();
        Notifier.v(getClass(), "On Pause");
        bullseyeCameraManager.pause();
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        socketServer.stop();
        Notifier.v(getClass(), "On Destroy");
        bullseyeCameraManager.close();
    }

    private void setUIFlags() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
                    Notifier.v(this, "Yay! We got permission!");
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

    public void showSettingsDrawer() {
        View dialogView = getLayoutInflater().inflate(R.layout.settings_drawer, null);

        Dialog d = new Dialog(this);
        d.setContentView(dialogView);
        d.setCancelable(true);
        d.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        d.getWindow().setGravity(Gravity.BOTTOM);

        /* The following code allows us to remain in immersive mode when the dialog opens. Treat like magic. */
        d.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        d.show(); //oh, and show the dialog
        d.getWindow().getDecorView().setSystemUiVisibility(this.getWindow().getDecorView().getSystemUiVisibility());
        d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        /* End magic */

    }


}
