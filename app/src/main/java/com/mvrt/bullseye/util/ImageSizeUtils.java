package com.mvrt.bullseye.util;

import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Log;
import android.util.Size;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Code taken from Google's Camera2 Android Sample
 */
public class ImageSizeUtils{

    /**
     * Compares two {@code Size}s based on their areas.
     */
    public static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    /**
     * Chooses the largest size within the max size limitations;
     */
    public static Size chooseOptimalSize(StreamConfigurationMap map, Size maxSize){
        Size largest = ImageSizeUtils.getLargestSize(map);
        Size[] choices = map.getOutputSizes(SurfaceTexture.class);

        List<Size> smallEnough = new ArrayList<>();

        for(Size option: choices){
            if(option.getWidth() <= maxSize.getWidth() && option.getHeight() <= maxSize.getHeight()){
                smallEnough.add(option);
            }
        }

        if(smallEnough.size() > 0){
            return Collections.max(smallEnough, new CompareSizesByArea());
        }else{
            Log.e("MVRT", "No suitable image size found");
            return choices[choices.length - 1];
        }

    }

    /**
     Chooses the optimal size for preview capture
     */
    public static Size chooseOptimalSize(StreamConfigurationMap map, int textureViewWidth, int textureViewHeight, Point maxSize) {

        Size aspectRatio = ImageSizeUtils.getLargestSize(map);
        Size[] choices = map.getOutputSizes(SurfaceTexture.class);

        int maxWidth = maxSize.x;
        int maxHeight = maxSize.y;

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();

        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth && option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                    Log.d("MVRT", "added option: " + option.toString() + " to bigEnough");
                } else {
                    Log.d("MVRT", "added option: " + option.toString() + " to notBigEnough");
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

    public static Size getLargestSize(StreamConfigurationMap map){
        return Collections.max(
                Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888)),
                new ImageSizeUtils.CompareSizesByArea());
    }

}