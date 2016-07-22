package com.mvrt.bullseye;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import com.mvrt.bullseye.util.AutoFitTextureView;
import com.mvrt.bullseye.util.Notifier;

public class MVRTCameraView extends AutoFitTextureView implements TextureView.SurfaceTextureListener{

    Surface surface;

    public MVRTCameraView(Context context) {
        super(context);
    }

    public MVRTCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MVRTCameraView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    private Size cameraSize = new Size(0, 0);
    private SurfaceReadyListener surfaceReadyListener;

    public void init(SurfaceReadyListener listener, Size cameraCaptureSize){
        this.surfaceReadyListener = listener;
        this.cameraSize = cameraCaptureSize;
        if(isAvailable()){
            Notifier.log(getClass(), "Is Available!");
            onSurfaceTextureAvailable(getSurfaceTexture(), cameraSize.getWidth(), cameraSize.getHeight());
        }
        setSurfaceTextureListener(this);
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to a textureView.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of the TextureView is fixed.
     */
    private void configureTransform() {
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        int rotation = getDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, cameraSize.getHeight(), cameraSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / cameraSize.getHeight(),
                    (float) viewWidth / cameraSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        setTransform(matrix);
    }

    public void releaseSurface(){
        if(surface != null)surface.release();
        surface = null;
    }

    /** TextureView.SurfaceTextureListener */
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        Notifier.log(getClass(), "Surface Texture Available");
        setAspectRatio(cameraSize.getWidth(), cameraSize.getHeight());
        getSurfaceTexture().setDefaultBufferSize(cameraSize.getWidth(), cameraSize.getHeight());
        configureTransform();
        if(surfaceReadyListener != null){
            if (surface == null)surface = new Surface(surfaceTexture);
            if(surfaceReadyListener != null)surfaceReadyListener.onSurfaceReady(surface);
            surfaceReadyListener = null;
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        configureTransform();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
    /** End TextureView.SurfaceTextureListener */

    public interface SurfaceReadyListener{
        void onSurfaceReady(Surface surface);
    }

}
