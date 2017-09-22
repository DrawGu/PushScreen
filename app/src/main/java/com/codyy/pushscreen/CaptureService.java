package com.codyy.pushscreen;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.MediaCodecInfo;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;

import com.codyy.pushscreen.media.RESCoreParameters;
import com.codyy.pushscreen.media.RESFlvData;
import com.codyy.pushscreen.media.RESFlvDataCollecter;
import com.codyy.pushscreen.rtmp.RESRtmpSender;

import static android.content.ContentValues.TAG;

/**
 * 截屏服务
 * Created by gujiajia on 2017/2/10.
 */

public class CaptureService extends Service {

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    private MediaProjectionManager mMediaProjectionManager;

    private MediaProjection mMediaProjection;

    private int mDensity;

    private boolean mRecording;

    private RESCoreParameters mRtmpParams;

    private RESRtmpSender mRtmpSender;

    private ScreenRecordWorker mScreenRecordWorker;

    private AudioRecordWorker mAudioRecordWorker;

    private RESFlvDataCollecter mDataCollecter;

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
        mRtmpParams = new RESCoreParameters();
        mRtmpParams.rtmpAddr = "rtmp://10.5.51.11/dms/yonglic";
        mRtmpParams.printDetailMsg = true;
        mRtmpParams.senderQueueLength = 150;
        mRtmpParams.mediacodecAVCBitRate = 750 * 1024;
        mRtmpParams.mediacodecAVCFrameRate = 20;

        mRtmpParams.mediacodecAACBitRate = 32 * 1024;
        mRtmpParams.mediacodecAACChannelCount = 1;
        mRtmpParams.mediacodecAACProfile = MediaCodecInfo.CodecProfileLevel.AACObjectLC;
        mRtmpParams.mediacodecAACSampleRate = 44100;
        mRtmpParams.mediacodecAACMaxInputSize = 8820;

        mRtmpParams.audioRecoderSource = MediaRecorder.AudioSource.MIC;
        mRtmpParams.audioRecoderChannelConfig = AudioFormat.CHANNEL_IN_MONO;
        mRtmpParams.audioRecoderSampleRate = mRtmpParams.mediacodecAACSampleRate;
        mRtmpParams.audioRecoderFormat = AudioFormat.ENCODING_PCM_16BIT;
        mRtmpParams.audioRecoderSliceSize = mRtmpParams.mediacodecAACSampleRate / 10;
        mRtmpParams.audioRecoderBufferSize = mRtmpParams.audioRecoderSliceSize;

        mMediaProjectionManager = (MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        obtainScreenSize();
    }

    /**
     * 获取屏幕尺寸
     */
    private void obtainScreenSize() {
        DisplayMetrics metrics = getApplicationContext().getResources().getDisplayMetrics();
        mRtmpParams.videoWidth = metrics.widthPixels / 2;
        mRtmpParams.videoHeight = metrics.heightPixels / 2;
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

    public void setUpMediaProjection(int resultCode, Intent resultData) {
        mMediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, resultData);
        mMediaProjection.registerCallback(mMediaProjectionCallback, null);
    }

    public void startRecord() {
        if (mMediaProjection != null) {
//            String path = Environment.getExternalStorageDirectory() + "/Download/" + "record-"
//                    + DateTimeFormat.forPattern("MM-dd-HH:mm:ss").print(System.currentTimeMillis()) + ".mp4";
//            mMediaMuxerWorker = new MediaMuxerWorker(path);

            mRtmpSender = new RESRtmpSender();
            mRtmpSender.prepare( mRtmpParams);
            mDataCollecter = new RESFlvDataCollecter() {
                @Override
                public void collect(RESFlvData flvData, int type) {
                    mRtmpSender.feed(flvData, type);
                }
            };
            mRtmpSender.start(mRtmpParams.rtmpAddr);

            mAudioRecordWorker = new AudioRecordWorker(mRtmpParams, mDataCollecter);
            mScreenRecordWorker = new ScreenRecordWorker();
            mScreenRecordWorker.init(mRtmpParams, mDensity,
                    mMediaProjection, mDataCollecter);

            new Thread(mAudioRecordWorker).start();
            new Thread(mScreenRecordWorker).start();
            mRecording = true;
        }
    }

    public void stopRecording() {
        mRecording = false;
        mScreenRecordWorker.quit();
        mAudioRecordWorker.quit();
        destroyMediaProjection();
        if (mRtmpSender != null) {
            mRtmpSender.stop();
            mRtmpSender.destroy();
        }
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
            mAudioRecordWorker.quit();
            mMediaProjection = null;
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
