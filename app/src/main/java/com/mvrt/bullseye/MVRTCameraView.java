package com.mvrt.bullseye;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.support.v4.app.ActivityCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import com.mvrt.bullseye.util.Notifier;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MVRTCameraView extends AutoFitTextureView implements TextureView.SurfaceTextureListener, ImageReader.OnImageAvailableListener {

    private CameraDevice cameraDevice;
    private CameraManager cameraManager;

    private Size previewSize;

    private ImageReader imageReader;

    CaptureRequest.Builder cameraPreviewRequestBuilder;
    CaptureRequest cameraPreviewRequest;

    private CameraCaptureSession cameraPreviewSession;

    public MVRTCameraView(Context context) {
        super(context);
    }

    public MVRTCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MVRTCameraView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void init(){
        if(isAvailable())openCamera(getWidth(), getHeight());
        else setSurfaceTextureListener(this);
    }

    private void openCamera(int width, int height) {
        //explicit call to check permissions:
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)return;

        //initialize manager, then open camera
        cameraManager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics("0");
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if(map == null)return;

            Point displaySize = new Point();
            getDisplay().getSize(displaySize);

            int maxPreviewWidth = displaySize.x;
            int maxPreviewHeight = displaySize.y;

            Size largest = Collections.max(
                    Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888)),
                    new CompareSizesByArea());

            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    width, height, maxPreviewWidth,
                    maxPreviewHeight, largest);

            imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(this, null);

            setAspectRatio(previewSize.getWidth(), previewSize.getHeight());

            configureTransform(width, height);

            cameraManager.openCamera("0", cameraStateCallback, null);
        } catch (CameraAccessException e) { e.printStackTrace(); }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        int rotation = getDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / previewSize.getHeight(),
                    (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        setTransform(matrix);
    }

    public void closeCamera(){
        Log.d("MVRT", "Closing Camera");
        cameraPreviewSession.close();
        cameraDevice.close();
        imageReader.close();
    }

    long exposureTime = 14000000; /** 7/500 seconds -> tested OPTIMAL (6/12/16) by @akhil99 */
    //long exposureTime = 10000000; //1/100
    //long exposureTime = 3333333; //1/300
    //long exposureTime = 5000000; //1/200
    //long exposureTime = 6666666; //1/150
    long frameDuration = 1000;
    int iso = 100;

    private void onCameraOpened(CameraDevice camera){
        cameraDevice = camera;
        try {
            cameraPreviewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            cameraPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            cameraPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            cameraPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);
            cameraPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime);
            cameraPreviewRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, frameDuration);
            cameraPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, iso);

            Surface surface = new Surface(getSurfaceTexture());
            Surface surfTwo = imageReader.getSurface();

            cameraPreviewRequestBuilder.addTarget(surface);
            cameraPreviewRequestBuilder.addTarget(surfTwo);

            camera.createCaptureSession(Arrays.asList(surface, surfTwo), cameraCaptureStateCallback, null);

        }catch(CameraAccessException cae){ cae.printStackTrace(); }

    }

    CameraCaptureSession.StateCallback cameraCaptureStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
            if(cameraDevice == null)return;

            cameraPreviewSession = session;
            cameraPreviewRequest = cameraPreviewRequestBuilder.build();

            try {
                cameraPreviewSession.setRepeatingRequest(cameraPreviewRequest, null, null);
            } catch (CameraAccessException e) { e.printStackTrace(); }

        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            Notifier.toast(getContext(), "Capture Session Configuration Failed", Toast.LENGTH_LONG);
        }
    };

    CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            onCameraOpened(camera);
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            closeCamera();
        }

        @Override
        public void onError(CameraDevice camera, int error) {

        }
    };

    /**
        Chooses the optimal size for preview capture
        @author Google
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth, int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e("MVRT", "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        openCamera(width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        configureTransform(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        Image image = reader.acquireLatestImage();
        if(image == null)return;

        Image.Plane Y = image.getPlanes()[0];
        Image.Plane U = image.getPlanes()[1];
        Image.Plane V = image.getPlanes()[2];

        int Yb = Y.getBuffer().remaining();
        int Ub = U.getBuffer().remaining();
        int Vb = V.getBuffer().remaining();

        byte[] data = new byte[Yb + Ub + Vb];

        Y.getBuffer().get(data, 0, Yb);
        U.getBuffer().get(data, Yb, Ub);
        V.getBuffer().get(data, Yb+ Ub, Vb);

        Mat mat = new Mat(image.getHeight() + image.getHeight()/2, image.getWidth(), CvType.CV_8UC1);
        mat.put(0, 0, data);

        Mat newMat = new Mat(mat.size(), CvType.CV_8UC3);
        Imgproc.cvtColor(mat, newMat, Imgproc.COLOR_YUV2RGB_I420);

        Log.d("MVRT", Arrays.toString(newMat.get(0, 0)));

        image.close();
    }
 
    /**
     * Compares two {@code Size}s based on their areas.
     * @author Google
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

}
