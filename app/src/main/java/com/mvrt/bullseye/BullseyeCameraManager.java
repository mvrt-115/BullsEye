package com.mvrt.bullseye;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.util.Log;
import android.util.Size;
import android.util.SizeF;
import android.view.Surface;

import com.mvrt.bullseye.util.CameraUtils;
import com.mvrt.bullseye.util.Notifier;

public class BullseyeCameraManager implements MainActivity.CameraPermissionsListener, CameraUtils.CameraStateListener, CameraUtils.SizeListener, MVRTCameraView.SurfaceReadyListener, CameraUtils.CaptureCallbacks, CameraUtils.CVLoadListener, CVProcessor.ProcessedMatListener {

    Context appContext;

    CameraManager cameraManager;
    CameraDevice cameraDevice;
    Size cameraPreviewSize;
    Size imageReaderSize;
    SizeF cameraFOV;
    Surface previewSurface;
    ImageReader imageReader;
    CameraCaptureSession cameraCaptureSession;
    CaptureRequest.Builder cameraCaptureRequestBuilder;

    MVRTCameraView cameraView;
    ProcessingOutputView outputView;
    CVProcessor processor;

    private boolean readyToOpenCamera = false;

    public BullseyeCameraManager(Context appContext, MVRTCameraView mvrtCameraView, ProcessingOutputView outputView){
        cameraView = mvrtCameraView;
        this.appContext = appContext;
        this.outputView = outputView;
        processor = CVProcessor.getCvProcessor();
    }

    public void init(){
        processor.setProcessedMatListener(this);
    }


    public void pause(){
        if(cameraDevice != null){
            cameraDevice.close();
            cameraDevice = null;
            Notifier.log(getClass(), "Camera Device Closed");
        }

        if(cameraCaptureSession != null){
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }

        cameraView.releaseSurface();

        if(processor != null){
            processor.close();
        }

    }

    public void resume(){
        if(readyToOpenCamera){
           openCamera();
        }
    }

    public void close(){
        if(cameraCaptureSession != null){
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }

        if(cameraDevice != null){
            cameraDevice.close();
            cameraDevice = null;
        }

        cameraView.releaseSurface();

        if(processor != null) {
            processor.close();
        }

        if(imageReader != null){
            imageReader.close();
            imageReader = null;
        }
    }


    @Override
    public void onCameraPermissionsGranted(CameraManager manager) {
        cameraManager = manager;
        Notifier.log(getClass(), "Camera Permissions Granted");
        loadCV();
    }

    public void loadCV(){
        CameraUtils.loadCV(appContext, this);
    }

    @Override
    public void onCVLoaded() {
        calculateCaptureSize();
    }

    public void calculateCaptureSize(){
        CameraUtils.initCameraSizes(appContext, cameraManager, this);
    }

    @Override
    public void onCameraSizeCalculated(Size previewSize, Size imgReaderSize, SizeF fovSize) {
        cameraPreviewSize = previewSize;
        imageReaderSize = imgReaderSize;
        cameraFOV = fovSize;
        readyToOpenCamera = true;
        openCamera();
    }

    public void openCamera(){
        CameraUtils.openCamera(appContext, cameraManager, this);
    }

    //region CameraUtils.CameraStateListener
    @Override
    public void onCameraConnected(CameraDevice cameraDevice) {
        this.cameraDevice = cameraDevice;
        Notifier.log(getClass(), "Camera Device Connected, id:" + cameraDevice.getId());
        initViews();
    }

    @Override
    public void onCameraDisconnected(CameraDevice cameraDevice) {
        close();
        Notifier.log(getClass(),  "Camera Disconnected, id:" + cameraDevice.getId());
    }
    //endregion


    public void initViews(){
        cameraView.init(this, cameraPreviewSize);
        outputView.init(cameraPreviewSize);
    }

    //region MVRTCameraView.SurfaceReadyListener
    @Override
    public void onSurfaceReady(Surface surface) {
        previewSurface = surface;
        initProcessor();
        initCapture();
    }
    //endregion

    public void initProcessor(){
        processor.init(appContext, imageReaderSize, cameraFOV, this);
    }

    public void initCapture(){
        Notifier.log(getClass(), "Initializing Capture, previewSurface = " + previewSurface.toString() + ", " + previewSurface.isValid());
        CameraUtils.initCapture(imageReaderSize, cameraDevice, this, previewSurface);
    }

    long exposureTime = 5 * 1000 * 1000;
    int iso = 100;

    public CaptureRequest.Builder configureCaptureRequest(CaptureRequest.Builder requestBuilder){
        requestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF);
        requestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f);
        requestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime);
        requestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
        //requestBuilder.set(CaptureRequest.BLACK_LEVEL_LOCK, true);

        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics("0");
            StreamConfigurationMap configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            long frameDuration = configs.getOutputMinFrameDuration(ImageFormat.YUV_420_888, cameraPreviewSize);
            requestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, frameDuration);
            Notifier.log(getClass(), "Frame Duration (ns): " + frameDuration);
        } catch (CameraAccessException e) { e.printStackTrace(); }

        return requestBuilder;
    }


    //region CameraUtils.CaptureCallbacks
    @Override
    public CaptureRequest.Builder editCaptureRequest(CaptureRequest.Builder builder) {
        return configureCaptureRequest(builder);
    }

    @Override
    public void onCaptureSessionConfigured(CameraCaptureSession session, CaptureRequest.Builder captureRequestBuilder, ImageReader imgReader) {
        cameraCaptureSession = session;
        cameraCaptureRequestBuilder = captureRequestBuilder;
        imageReader = imgReader;
        Notifier.log(getClass(), "Capture Session Configured");
        startCapture();
    }

    public void startCapture(){
        CameraUtils.startCapture(cameraCaptureSession, cameraCaptureRequestBuilder);
    }

    @Override
    public void onImageCaptured(byte[] data) {
        processor.processMat(data);
    }

    public void onImgProcessed(Bitmap out) {
        outputView.setBitmap(out);
    }

    //endregion

}
