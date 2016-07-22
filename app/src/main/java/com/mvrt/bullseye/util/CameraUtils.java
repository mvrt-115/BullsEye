package com.mvrt.bullseye.util;


import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import java.util.ArrayList;
import java.util.Arrays;

public class CameraUtils {

    /** Configuration Variables */
    final static Size MAX_PREVIEW_SIZE = new Size(1280, 720);
    final static Size MAX_IMGREADER_SIZE = new Size(1280, 720);
    /** End config vars */

    //region Load OpenCV
    /**
     * Loads the OpenCV libraries asynchronously
     */
    public static void loadCV(Context appContext, final CVLoadListener cvLoadListener){
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, appContext, new CVLoaderCallback(appContext, cvLoadListener));
        Notifier.log(CameraUtils.class, "CV Loaded");
    }

    public interface CVLoadListener{
        void onCVLoaded();
    }

    private static class CVLoaderCallback extends BaseLoaderCallback{

        CVLoadListener loadListener;

        public CVLoaderCallback(Context AppContext, CVLoadListener loadListener) {
            super(AppContext);
            this.loadListener = loadListener;
        }

        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    loadListener.onCVLoaded();
                    break;
            }
            super.onManagerConnected(status);
        }
    }
    //endregion

    //region Camera Size Calculations

    /** <pre/>
     *  Loads possible camera sizes.
     *  Prerequisites: Camera Permissions granted
     *  Requires: App {@link Context}, {@link CameraManager} instance
     </pre> */
    public static void initCameraSizes(Context appContext, CameraManager cameraManager, SizeListener listener) {
        try {
            //explicit call to check permissions:
            if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Notifier.log(Log.ERROR, CameraUtils.class, "initCameraSizes() -> Camera Permission Not Granted");
                return;
            }

            //get camera characteristics (map = available preview image sizes)
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics("0");
            StreamConfigurationMap streamConfigMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            assert (streamConfigMap != null);

            Size[] sizes = streamConfigMap.getOutputSizes(ImageFormat.JPEG);
            Size cameraPreviewSize = ImageSizeUtils.chooseOptimalSize(streamConfigMap, MAX_PREVIEW_SIZE);
            Size imageReaderSize = ImageSizeUtils.chooseOptimalSize(streamConfigMap, MAX_IMGREADER_SIZE);

            Notifier.log(CameraUtils.class, "Sizes: " + Arrays.toString(sizes));
            Notifier.log(CameraUtils.class, "Preview Size: " + cameraPreviewSize.toString());
            Notifier.log(CameraUtils.class, "Image Reader Size: " + imageReaderSize.toString());

            listener.onCameraSizeCalculated(cameraPreviewSize, imageReaderSize);


        }catch(CameraAccessException e){
            Notifier.log(Log.ERROR, CameraUtils.class, e.getMessage());
        }
    }

    public interface SizeListener{
        void onCameraSizeCalculated(Size preview, Size imgReader);
    }

    //endregion

    //region Camera Initialization

    /** <pre>
     *  Open Camera Device.
     *  Prerequisites: Camera Permissions Granted
     *  Requires: Application {@link Context}, {@link CameraManager} instance
     </pre> */
    public static void openCamera(Context appContext, CameraManager cameraManager, CameraStateListener cameraStateListener){
        try {
            //explicit permissions check
            if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Notifier.log(Log.ERROR, CameraUtils.class, "Permissions missing");
                return;
            }
            cameraManager.openCamera("0", new CameraStatusCallback(cameraStateListener), null);
        }catch(CameraAccessException e){
            Notifier.log(Log.ERROR, CameraUtils.class, e.getMessage());
        }
    }

    private static class CameraStatusCallback extends CameraDevice.StateCallback{

        CameraStateListener listener;

        public CameraStatusCallback(CameraStateListener listener){
            this.listener = listener;
        }

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            listener.onCameraConnected(camera);
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            listener.onCameraDisconnected(camera);
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Notifier.log(Log.ERROR, CameraUtils.class, "ERROR! " + error);
        }
    }

    public interface CameraStateListener{
        void onCameraConnected(CameraDevice cameraDevice);
        void onCameraDisconnected(CameraDevice cameraDevice);
    }

    //endregion

    //region Camera Capture

    /** <pre>
     * Initializes camera capture, given surfaces to send this feed to
     * Prerequisites: OpenCV loaded, (Camera Permissions Granted), (Camera Opened), (Views initialized)
     * Requires: Capture size calculated, {@link CameraDevice} connected, {@link Surface Surface(s)} initialized
     </pre> */
    public static void initCapture(Size captureSize, CameraDevice cameraDevice, CaptureCallbacks callbacks, Surface... surfaces){
        try {
            CaptureRequest.Builder cameraPreviewRequestBuilder =
                    callbacks.editCaptureRequest(cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW));

            ImageReader imgReader = createImageReader(captureSize, callbacks);

            ArrayList<Surface> surfaceList = new ArrayList<>(Arrays.asList(surfaces));
            surfaceList.add(imgReader.getSurface());

            for (Surface surf : surfaceList) {
                cameraPreviewRequestBuilder.addTarget(surf);
            }

            //todo: check if passing the imgReader through callbacks causes any memory issues/leaks
            cameraDevice.createCaptureSession(surfaceList, new PreviewCaptureStateCallback(callbacks, cameraPreviewRequestBuilder, imgReader), null);
        }catch(CameraAccessException e){
            Notifier.log(Log.ERROR, CameraUtils.class, e.getMessage());
        }
    }

    /** <pre>
     * Starts capturing images from the camera, and to the Surfaces provided to {@link #initCapture(Size, CameraDevice, CaptureCallbacks, Surface...)}.
     * Requires: A configured {@link CameraCaptureSession} (and {@link CaptureRequest.Builder}).
     * @see #initCapture(Size, CameraDevice, CaptureCallbacks, Surface...)
    </pre> */
    public static void startCapture(CameraCaptureSession session, CaptureRequest.Builder previewRequestBuilder){
        CaptureRequest cameraPreviewRequest = previewRequestBuilder.build();
        try {
            session.setRepeatingRequest(cameraPreviewRequest, captureCallback, null);
        } catch (CameraAccessException e) { e.printStackTrace(); }
    }

    public interface CaptureCallbacks{
        CaptureRequest.Builder editCaptureRequest(CaptureRequest.Builder builder);
        void onCaptureSessionConfigured(CameraCaptureSession session, CaptureRequest.Builder captureRequestBuilder, ImageReader imgReader);
        void onImageCaptured(byte[] data);
    }

    private static CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureSequenceCompleted(CameraCaptureSession session, int sequenceId, long frameNumber) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
            Notifier.log(getClass(), "Capture Sequence Completed");
        }

        @Override
        public void onCaptureSequenceAborted(CameraCaptureSession session, int sequenceId) {
            super.onCaptureSequenceAborted(session, sequenceId);
            Notifier.log(getClass(), "Capture Sequence Aborted");
        }
    };

    private static class PreviewCaptureStateCallback extends CameraCaptureSession.StateCallback{

        CaptureCallbacks callbacks;
        CaptureRequest.Builder captureRequestBuilder;
        ImageReader imgReader;

        public PreviewCaptureStateCallback(CaptureCallbacks callbacks, CaptureRequest.Builder capRequestBuilder, ImageReader imageReader){
            this.callbacks = callbacks;
            captureRequestBuilder = capRequestBuilder;
            this.imgReader = imageReader;
        }

        @Override
        public void onConfigured(@Nullable CameraCaptureSession session) {
            Notifier.log(getClass(), "Capture Session Configured");
            callbacks.onCaptureSessionConfigured(session, captureRequestBuilder, imgReader);
        }

        @Override
        public void onConfigureFailed(@Nullable CameraCaptureSession session) {
            Notifier.log(Log.ERROR, this, "Capture Session Configuration Failed");
        }
    }

    /** <pre>
     * Opens an image reader, used to grab camera frames and convert them into Mats to prepare for processing.
     * Prerequisite: OpenCV Loaded
     * Requires: Capture size calculated.
     </pre> */
    private static ImageReader createImageReader(Size imageSize, CaptureCallbacks callbacks){
        ImageReader imageReader = ImageReader.newInstance(imageSize.getWidth(), imageSize.getHeight(), ImageFormat.YUV_420_888, 2);
        imageReader.setOnImageAvailableListener(new ImageReaderListener(callbacks), null);
        return imageReader;
    }

    private static class ImageReaderListener implements ImageReader.OnImageAvailableListener{

        CaptureCallbacks callbacks;

        public ImageReaderListener(CaptureCallbacks onImageCaptured){
            callbacks = onImageCaptured;
        }

        byte[] data;

        @Override
        public void onImageAvailable(ImageReader reader) {
            Image img = reader.acquireLatestImage();
            if(img == null)return;

                Image.Plane Y = img.getPlanes()[0];
                Image.Plane U = img.getPlanes()[1];
                Image.Plane V = img.getPlanes()[2];

                int Yb = Y.getBuffer().remaining();
                int Ub = U.getBuffer().remaining();
                int Vb = V.getBuffer().remaining();

                if(data == null || data.length != Yb + Ub + Vb) {
                    data = new byte[Yb + Ub + Vb];
                    Notifier.log(this, "creating data buffer");
                }

                Y.getBuffer().get(data, 0, Yb);
                U.getBuffer().get(data, Yb, Ub);
                V.getBuffer().get(data, Yb + Ub, Vb);

                img.close();

            callbacks.onImageCaptured(data);

        }

    }

    //endregion

}
