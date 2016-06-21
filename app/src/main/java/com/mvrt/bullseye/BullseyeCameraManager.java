package com.mvrt.bullseye;

import android.content.Context;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.util.Size;
import android.view.Surface;

import com.mvrt.bullseye.util.CameraUtils;
import com.mvrt.bullseye.util.Notifier;

import org.opencv.core.Mat;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BullseyeCameraManager implements MainActivity.CameraPermissionsListener, CameraUtils.CameraStateListener, CameraUtils.SizeListener, MVRTCameraView.SurfaceReadyListener, CameraUtils.CaptureCallbacks, CameraUtils.CVLoadListener, CVProcessor.ProcessedMatListener {

    Context appContext;
    State state;

    CameraManager cameraManager;
    CameraDevice cameraDevice;
    Size cameraCaptureSize;
    Surface previewSurface;
    CameraCaptureSession cameraCaptureSession;
    CaptureRequest.Builder cameraCaptureRequestBuilder;

    MVRTCameraView cameraView;
    ProcessingOutputView outputView;
    CVProcessor processor;

    public BullseyeCameraManager(Context appContext, MVRTCameraView mvrtCameraView, ProcessingOutputView outputView){
        state = new State();
        cameraView = mvrtCameraView;
        this.appContext = appContext;
        this.outputView = outputView;
        processor = CVProcessor.getCvProcessor();
    }

    public void init(){
        loadCV();
        processor.setProcessedMatListener(this);
    }

    public void loadCV(){
        CameraUtils.loadCV(appContext, this);
    }

    public void openCamera(){
        CameraUtils.openCamera(appContext, cameraManager, this);
    }

    public void calculateCaptureSize(){
        CameraUtils.initCameraSizes(appContext, cameraManager, this);
    }

    public void initViews(){
        cameraView.init(this, cameraCaptureSize);
        outputView.setAspectRatio(cameraCaptureSize.getWidth(), cameraCaptureSize.getHeight());
        outputView.init(cameraCaptureSize);
    }

    public void initCapture(){
        if(state.get(State.CAPTURE_INITIALIZED))return; //just make sure we haven't initialized capture already
        CameraUtils.initCapture(cameraCaptureSize, cameraDevice, this, previewSurface);
    }
    public void startCapture(){
        CameraUtils.startCapture(cameraCaptureSession, cameraCaptureRequestBuilder);
    }

    public void close(){
        if(cameraDevice != null)cameraDevice.close();
        if(previewSurface != null)previewSurface.release();
        if(cameraCaptureSession != null)cameraCaptureSession.close();
        outputView.close();
        cameraView.close();
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

    //region OpenCV Listeners (not an interface)
    @Override
    public void onCVLoaded() {
        state.setCompleted(State.CV_LOADED);
    }
    //endregion  (

    //region MainActivity.CameraPermissionsListener
    @Override
    public void onCameraPermissionsGranted(CameraManager manager) {
        cameraManager = manager;
        state.setCompleted(State.CAMERA_PERMISSIONS_GRANTED);
    }
    //endregion

    //region CameraUtils.CameraStateListener
    @Override
    public void onCameraConnected(CameraDevice cameraDevice) {
        this.cameraDevice = cameraDevice;
        state.setCompleted(State.CAMERA_OPENED);
    }

    @Override
    public void onCameraDisconnected(CameraDevice cameraDevice) {
        close();
    }
    //endregion

    //region CameraUtils.SizeListener
    @Override
    public void onCameraSizeCalculated(Size c) {
        cameraCaptureSize = c;
        state.setCompleted(State.CAPTURE_SIZE_CALCULATED);
    }
    //endregion

    //region MVRTCameraView.SurfaceReadyListener
    @Override
    public void onSurfaceReady(Surface surface) {
        previewSurface = surface;
        state.setCompleted(State.VIEWS_INITIALIZED);
    }
    //endregion

    //region CameraUtils.CaptureCallbacks
    @Override
    public CaptureRequest.Builder editCaptureRequest(CaptureRequest.Builder builder) {
        return configureCaptureRequest(builder);
    }

    @Override
    public void onCaptureSessionConfigured(CameraCaptureSession session, CaptureRequest.Builder captureRequestBuilder) {
        cameraCaptureSession = session;
        cameraCaptureRequestBuilder = captureRequestBuilder;
        state.setCompleted(State.CAPTURE_INITIALIZED);
    }

    @Override
    public void onImageCaptured(Mat rgbMat) {
        processor.processMat(rgbMat);
    }

    @Override
    public void onMatProcessed(Mat out) {
        outputView.setMat(out);
    }

    //endregion

    //region State-tracking stuff
    public void onStateUpdated(int key, boolean newValue) {
        Notifier.log(this, "State Updated: " + getUserFriendlyStateKeyString(key) + " -> " + newValue);

        if(!newValue)return; //we only care about tasks being completed

        if(key == State.CAMERA_PERMISSIONS_GRANTED) {
            openCamera();
            calculateCaptureSize();
        } else if(key == State.CAPTURE_SIZE_CALCULATED){
            initViews();
        } else if(key == State.CAPTURE_INITIALIZED){
            startCapture();
        } else if(state.get(State.CV_LOADED) && state.get(State.CAMERA_OPENED) && state.get(State.VIEWS_INITIALIZED)){
            initCapture();
        }
    }

    private class State{
        public static final int CAMERA_PERMISSIONS_GRANTED = 0;
        public static final int CV_LOADED = 1;
        public static final int CAMERA_OPENED = 2;
        public static final int CAPTURE_SIZE_CALCULATED = 3;
        public static final int VIEWS_INITIALIZED = 4;
        public static final int CAPTURE_INITIALIZED = 5;
        public static final int CAPTURE_STARTED = 6;

        Map<Integer, Boolean> currentState;

        public State(){
            currentState = new ConcurrentHashMap<>();
        }

        public void setCompleted(int key){
            changeState(key, true);
        }

        public void changeState(int key, boolean state){
            if(get(key) == state)return; //no change
            currentState.put(key, state);
            onStateUpdated(key, state);
        }

        public boolean get(int key){
            Boolean b = currentState.get(key);
            return (b != null && b); //if the state hasn't been set, return false
        }
    }

    public String getUserFriendlyStateKeyString(int key){
        switch(key){
            case State.CAMERA_PERMISSIONS_GRANTED: return "Camera Permissions";
            case State.CV_LOADED: return "CV Loaded";
            case State.CAMERA_OPENED: return "Camera Opened";
            case State.CAPTURE_SIZE_CALCULATED: return "Capture Size Calculated";
            case State.VIEWS_INITIALIZED: return "Views Initialized";
            case State.CAPTURE_INITIALIZED: return "Capture Initialized";
            case State.CAPTURE_STARTED: return "Capture Started";
            default: return "Unknown State Key";
        }
    }

    //endregion

}
