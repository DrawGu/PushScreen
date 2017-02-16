package com.codyy.pushscreen;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import java.io.IOException;

import static android.content.ContentValues.TAG;

/**
 * 截屏服务
 * Created by gujiajia on 2017/2/10.
 */

public class CaptureService extends Service {

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    private MediaProjectionManager mMediaProjectionManager;

    private MediaProjection mMediaProjection;

    private MediaRecorder mMediaRecorder;

    private VirtualDisplay mVirtualDisplay;

    private int mScreenWidth;

    private int mScreenHeight;

    private int mDensity;

    private boolean mRecording;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private Binder mBinder = new LocalBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mMediaProjectionManager = (MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        obtainScreenSize();
        mMediaRecorder = new MediaRecorder();
    }

    /**
     * 获取屏幕尺寸
     */
    private void obtainScreenSize() {
        DisplayMetrics metrics = getApplicationContext().getResources().getDisplayMetrics();
        mScreenWidth = metrics.widthPixels / 2;
        mScreenHeight = metrics.heightPixels / 2;
        mDensity = metrics.densityDpi;
    }

    public MediaProjection getMediaProjection() {
        return mMediaProjection;
    }

    public MediaProjectionManager getMediaProjectionManager() {
        return mMediaProjectionManager;
    }

    public Intent createScreenCaptureIntent() {
        if (mMediaProjectionManager != null) {
            return mMediaProjectionManager.createScreenCaptureIntent();
        }
        return null;
    }

    private void createVirtualDisplay() {
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("gg",
                mScreenWidth, mScreenHeight, mDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mMediaRecorder.getSurface(),
                null, null);
    }

    public void setUpMediaProjection(int resultCode, Intent resultData) {
        mMediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, resultData);
        mMediaProjection.registerCallback(mMediaProjectionCallback, null);
    }

    public boolean startRecord() {
        initRecorder();
        createVirtualDisplay();
        if (mMediaProjection != null) {
            mRecording = true;
            createVirtualDisplay();
            mMediaRecorder.start();
        }
        return true;
    }

    public void stopRecording() {
        mRecording = false;
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        destroyMediaProjection();
    }

    public boolean isRecording() {
        return mRecording;
    }

    public class LocalBinder extends Binder{
        CaptureService getService() {
            return CaptureService.this;
        }
    }

    private MediaProjection.Callback mMediaProjectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
            mMediaProjection = null;
            mVirtualDisplay.release();
            destroyMediaProjection();
        }
    };

    public void initRecorder() {
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mMediaRecorder.setOutputFile(Environment
                .getExternalStoragePublicDirectory(Environment
                        .DIRECTORY_DOWNLOADS) + "/video.mp4");
        mMediaRecorder.setVideoSize(mScreenWidth, mScreenHeight);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mMediaRecorder.setVideoEncodingBitRate(1000 * 1000);
        mMediaRecorder.setVideoFrameRate(30);
        WindowManager window = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = window.getDefaultDisplay();
        int rotation = display.getRotation();
        int orientation = ORIENTATIONS.get(rotation + 90);
        mMediaRecorder.setOrientationHint(orientation);
        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void destroyMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection.unregisterCallback(mMediaProjectionCallback);
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        Log.i(TAG, "MediaProjection Stopped");
    }
}
