package com.mvrt.bullseye;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.util.Size;

import org.opencv.core.Mat;

public interface CameraManagerListener{
    void onCaptureSizeCalculated(Size cameraSize);
    void onCameraOpened(CameraDevice cameraDevice);
    void onCaptureSessionReady(CameraCaptureSession session);
    void onYUVFrameCaptured(Mat yuvData);
}