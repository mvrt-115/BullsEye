package com.mvrt.bullseye;

import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.ImageReader;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import com.mvrt.bullseye.util.CameraUtils;
import com.mvrt.bullseye.util.Notifier;

public class BullseyeCameraManager implements MainActivity.CameraPermissionsListener, CameraUtils.CameraStateListener, CameraUtils.SizeListener, MVRTCameraView.SurfaceReadyListener, CameraUtils.CaptureCallbacks, CameraUtils.CVLoadListener, CVProcessor.ProcessedMatListener {

    Context appContext;

    CameraManager cameraManager;
    CameraDevice cameraDevice;
    Size cameraPreviewSize;
    Size imageReaderSize;
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
    }

    public void resume(){
        if(readyToOpenCamera)openCamera();
    }

    public void close(){
        /*if(cameraCaptureSession != null){
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }

        if(cameraDevice != null){
            cameraDevice.close();
            cameraDevice = null;
        }

        if(imageReader != null){
            imageReader.close();
            imageReader = null;
        }
        processor.close(); */
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
    public void onCameraSizeCalculated(Size previewSize, Size imgReaderSIze) {
        cameraPreviewSize = previewSize;
        imageReaderSize = imgReaderSIze;
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
    }

    @Override
    public void onCameraDisconnected(CameraDevice cameraDevice) {
        close();
        Notifier.log(getClass(),  "Camera Disconnected, id:" + cameraDevice.getId());
    }
    //endregion

    public void initViews(){
        cameraView.init(this, cameraPreviewSize);
        outputView.setAspectRatio(cameraPreviewSize.getWidth(), cameraPreviewSize.getHeight());
        outputView.init(cameraPreviewSize);
    }

    //region MVRTCameraView.SurfaceReadyListener
    @Override
    public void onSurfaceReady(Surface surface) {
        previewSurface = surface;
    }
    //endregion

    public void initProcessor(){
        processor.init(cameraPreviewSize, this);
    }

    public void initCapture(){
        CameraUtils.initCapture(cameraPreviewSize, cameraDevice, this, previewSurface);
    }

    public void startCapture(){
        CameraUtils.startCapture(cameraCaptureSession, cameraCaptureRequestBuilder);
    }

    long exposureTime = 14000000; /** 7/500 seconds -> tested OPTIMAL (6/12/16) by @akhil99 */
    long frameDuration = 1000;
    int iso = 100;

    public CaptureRequest.Builder configureCaptureRequest(CaptureRequest.Builder requestBuilder){
        requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
        requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
        requestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);
        requestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime);
        requestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, frameDuration);
        requestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
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
