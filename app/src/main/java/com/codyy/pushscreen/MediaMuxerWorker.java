package com.codyy.pushscreen;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.support.annotation.IntDef;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;

/**
 * Created by gujiajia on 2017/2/18.
 */

public class MediaMuxerWorker {

    public final static int TYPE_AUDIO = 0;

    public final static int TYPE_VIDEO = 1;

    private MediaMuxer mMediaMuxer;

    private int mVideoTrackIndex = -1;

    private int mAudioTrackIndex = -1;

    private boolean mMuxing;

    private final Object mLock = new Object();

    public MediaMuxerWorker(String path) throws IOException {
        mMediaMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    public void addTrack(@TrackType int trackType, MediaFormat mediaFormat) {
        synchronized (mLock) {
            if (mMuxing) return;
            if (trackType == TYPE_AUDIO) {
                mAudioTrackIndex = mMediaMuxer.addTrack(mediaFormat);
            } else if (trackType == TYPE_VIDEO) {
                mVideoTrackIndex = mMediaMuxer.addTrack(mediaFormat);
            }
            if (mVideoTrackIndex != -1 && mAudioTrackIndex != -1) {//音轨视轨都已添加
                mMediaMuxer.start();
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
            mMediaMuxer.writeSampleData(trackId, byteBuffer, bufferInfo);
        }
    }

    @IntDef({TYPE_AUDIO, TYPE_VIDEO})
    @Retention(RetentionPolicy.SOURCE)
    public @interface TrackType{}
}
