package com.codyy.pushscreen;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;

import org.joda.time.format.DateTimeFormat;

import static android.content.ContentValues.TAG;

/**
 * 截屏服务
 * Created by gujiajia on 2017/2/10.
 */

public class CaptureService extends Service {

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    private MediaProjectionManager mMediaProjectionManager;

    private MediaProjection mMediaProjection;

    private VirtualDisplay mVirtualDisplay;

    private int mScreenWidth;

    private int mScreenHeight;

    private int mDensity;

    private boolean mRecording;

    private ScreenRecordWorker mScreenRecordWorker;

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

//    private void createVirtualDisplay() {
//        mVirtualDisplay = mMediaProjection.createVirtualDisplay("gg",
//                mScreenWidth, mScreenHeight, mDensity,
//                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mMediaRecorder.getSurface(),
//                null, null);
//    }

    public void setUpMediaProjection(int resultCode, Intent resultData) {
        mMediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, resultData);
        mMediaProjection.registerCallback(mMediaProjectionCallback, null);
    }

    public void startRecord() {
        if (mMediaProjection != null) {
            mScreenRecordWorker = new ScreenRecordWorker();
            mScreenRecordWorker.init(mScreenWidth, mScreenHeight, 600000,mDensity, mMediaProjection,
                    Environment.getExternalStorageDirectory() + "/Download/" + "record-"
                            + DateTimeFormat.forPattern("MM-dd-HH:mm:ss").print(System.currentTimeMillis()) + ".mp4");
            mScreenRecordWorker.start();
            mRecording = true;
        }
    }

    public void stopRecording() {
        mRecording = false;
        mScreenRecordWorker.quit();
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
            mScreenRecordWorker.quit();
            mMediaProjection = null;
            mVirtualDisplay.release();
            destroyMediaProjection();
        }
    };

    private void destroyMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection.unregisterCallback(mMediaProjectionCallback);
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        Log.i(TAG, "MediaProjection Stopped");
    }
}
