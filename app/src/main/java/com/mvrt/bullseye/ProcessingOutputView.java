/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mvrt.bullseye;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

/**
 * A {@link SurfaceView} that can be adjusted to a specified aspect ratio.
 */
public class ProcessingOutputView extends SurfaceView implements SurfaceHolder.Callback{

    private int mRatioWidth = 0;
    private int mRatioHeight = 0;

    private SurfaceHolder surfaceHolder;

    Bitmap outputCacheBitmap;

    public ProcessingOutputView(Context context) {
        this(context, null);
    }

    public ProcessingOutputView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ProcessingOutputView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void close(){
        if(outputCacheBitmap != null) outputCacheBitmap.recycle();
    }

    public void init(Size cameraSize){
        surfaceHolder = getHolder();
        surfaceHolder.addCallback(this);
        initOutputBitmap(cameraSize.getWidth(), cameraSize.getHeight());
    }

    private void initOutputBitmap(int width, int height){
        outputCacheBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
    }

    public void setMat(Mat m){
        if(m == null)return;
        if(outputCacheBitmap == null || outputCacheBitmap.isRecycled())return;
        Utils.matToBitmap(m, outputCacheBitmap);
        if(outputCacheBitmap != null)setBitmap(outputCacheBitmap);
    }

    public void setBitmap(Bitmap bitmap){
        if(surfaceHolder == null)return;
        Canvas canvas = surfaceHolder.lockCanvas();
        if(canvas == null)return;
        canvas.drawBitmap(bitmap, new Rect(0,0,bitmap.getWidth(),bitmap.getHeight()),
                new Rect(0,0,canvas.getWidth(),canvas.getHeight()), null);
        surfaceHolder.unlockCanvasAndPost(canvas);
    }

    /**d
     * Sets the aspect ratio for this view. The size of the view will be measured based on the ratio
     * calculated from the parameters. Note that the actual sizes of parameters don't matter, that
     * is, calling setAspectRatio(2, 3) and setAspectRatio(4, 6) make the same result.
     *
     * @param width  Relative horizontal size
     * @param height Relative vertical size
     */
    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        mRatioWidth = width;
        mRatioHeight = height;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(width, height);
        } else {
            if (width < height * mRatioWidth / mRatioHeight) {
                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
            } else {
                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
            }
        }
    }


    /** SurfaceHolder.Callback */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceHolder = holder;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        surfaceHolder = holder;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceHolder = null;
    }
    /** END SurfaceHolder.Callback */
}
