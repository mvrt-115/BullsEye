package com.mvrt.bullseye;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
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

    HandlerThread mBackgroundThread;
    Handler mBackgroundHandler;

    private boolean readyToOpenCamera = false;

    public BullseyeCameraManager(Context appContext, MVRTCameraView mvrtCameraView, ProcessingOutputView outputView, OutputSocketServer socketServer){
        cameraView = mvrtCameraView;
        this.appContext = appContext;
        this.outputView = outputView;
        processor = CVProcessor.getCvProcessor();
        processor.setOutputSocketServer(socketServer);
    }

    public void init(){
        processor.setProcessedMatListener(this);
        startBackgroundThread();
    }

    public void pause(){
        if(cameraDevice != null){
            cameraDevice.close();
            cameraDevice = null;
            Notifier.v(getClass(), "Camera Device Closed");
        }

        if(cameraCaptureSession != null){
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }

        cameraView.releaseSurface();

        stopBackgroundThread();
    }

    public void resume(){
        startBackgroundThread();
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

        if(imageReader != null){
            imageReader.close();
            imageReader = null;
        }

        stopBackgroundThread();
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCameraPermissionsGranted(CameraManager manager) {
        cameraManager = manager;
        Notifier.v(getClass(), "Camera Permissions Granted");
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
        CameraUtils.openCamera(appContext, cameraManager, this, mBackgroundHandler);
    }

    //region CameraUtils.CameraStateListener
    @Override
    public void onCameraConnected(CameraDevice cameraDevice) {
        this.cameraDevice = cameraDevice;
        Notifier.v(getClass(), "Camera Device Connected, id:" + cameraDevice.getId());

        (new Handler(Looper.getMainLooper())).post(new Runnable() {
            @Override
            public void run() {
                initViews();
            }
        });

    }

    @Override
    public void onCameraDisconnected(CameraDevice cameraDevice) {
        close();
        Notifier.v(getClass(),  "Camera Disconnected, id:" + cameraDevice.getId());
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
        Notifier.v(getClass(), "Initializing Capture, previewSurface = " + previewSurface.toString() + ", " + previewSurface.isValid());
        CameraUtils.initCapture(imageReaderSize, cameraDevice, this, mBackgroundHandler, previewSurface);
    }

    long exposureTime = 5 * 1000 * 1000;
    int iso = 100;

    public CaptureRequest.Builder configureCaptureRequest(CaptureRequest.Builder requestBuilder){
        requestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF);
        requestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
        requestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
        requestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f);
        requestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime);
        requestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, iso);

        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics("0");
            StreamConfigurationMap configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            long frameDuration = configs.getOutputMinFrameDuration(ImageFormat.YUV_420_888, cameraPreviewSize);
            requestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, frameDuration);
            Notifier.d(getClass(), "Frame Duration (ns): " + frameDuration);
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
        Notifier.v(getClass(), "Capture Session Configured");
        startCapture();
    }

    public void startCapture(){
        CameraUtils.startCapture(cameraCaptureSession, cameraCaptureRequestBuilder, mBackgroundHandler);
    }

    @Override
    public void onImageCaptured(byte[] data) {
        processor.processMat(data, System.currentTimeMillis(), outputView);
    }

    public void onImgProcessed(final Bitmap out) {
//        (new Handler(Looper.getMainLooper())).post(new Runnable() {
//            @Override
//            public void run() {
//                outputView.setBitmap(out);
//            }
//        });
    }

    //endregion

}
