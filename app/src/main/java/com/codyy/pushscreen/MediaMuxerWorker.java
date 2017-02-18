package com.codyy.pushscreen;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.support.annotation.IntDef;
import android.util.Log;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;

/**
 * Created by gujiajia on 2017/2/18.
 */

public class MediaMuxerWorker {

    private final static String TAG = "MediaMuxerWorker";

    public final static int TYPE_AUDIO = 0;

    public final static int TYPE_VIDEO = 1;

    private MediaMuxer mMediaMuxer;

    private int mVideoTrackIndex = -1;

    private int mAudioTrackIndex = -1;

    private volatile boolean mMuxing;

    private final Object mLock = new Object();

    private String mPath;

    public MediaMuxerWorker(String path) {
        this.mPath = path;
    }

    public void stop() {
        mMediaMuxer.stop();
        mMuxing = false;
    }

    public void addTrack(@TrackType int trackType, MediaFormat mediaFormat) {
        synchronized (mLock) {
            if (mMuxing) return;
            createMuxer();
            Log.d(TAG, "addTrack trackType=" + trackType + ",mediaFormat=" + mediaFormat.getString(MediaFormat.KEY_MIME));
            if (trackType == TYPE_AUDIO) {
                mAudioTrackIndex = mMediaMuxer.addTrack(mediaFormat);
            } else if (trackType == TYPE_VIDEO) {
                mVideoTrackIndex = mMediaMuxer.addTrack(mediaFormat);
            }
            if (mVideoTrackIndex != -1 && mAudioTrackIndex != -1) {//音轨视轨都已添加
                mMediaMuxer.start();
                mMuxing = true;
            }
        }
    }

    private void createMuxer() {
        if (mMediaMuxer == null) {
            try {
                mMediaMuxer = new MediaMuxer(mPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void writeSampleData(@TrackType int trackType, ByteBuffer byteBuffer,
                                MediaCodec.BufferInfo bufferInfo) {
        synchronized (mLock) {
            if (!mMuxing) return;
            int trackId;
            if (trackType == TYPE_VIDEO) {
                trackId = mVideoTrackIndex;
            } else {
                trackId = mAudioTrackIndex;
            }
            Log.d(TAG, "writeSampleData trackType=" + trackType + ",bufferInfo=" + bufferInfo);
            mMediaMuxer.writeSampleData(trackId, byteBuffer, bufferInfo);
        }
    }

    @IntDef({TYPE_AUDIO, TYPE_VIDEO})
    @Retention(RetentionPolicy.SOURCE)
    public @interface TrackType{}
}
