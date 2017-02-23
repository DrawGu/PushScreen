/*
 * Copyright (c) 2014 Yrom Wang <http://www.yrom.net>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.codyy.pushscreen;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.util.Log;
import android.view.Surface;

import com.codyy.pushscreen.media.Packager;
import com.codyy.pushscreen.media.RESCoreParameters;
import com.codyy.pushscreen.media.RESFlvData;
import com.codyy.pushscreen.media.RESFlvDataCollecter;
import com.codyy.pushscreen.rtmp.RESRtmpSender;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Yrom
 */
public class ScreenRecordWorker implements Runnable {
    private static final String TAG = "ScreenRecordWorker";

    private int mWidth;
    private int mHeight;
    private int mBitRate;
    private int mDpi;
    private MediaProjection mMediaProjection;
    // parameters for the encoder
    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video Coding
    private static final int IFRAME_INTERVAL = 10; // 10 seconds between I-frames
    private static final int TIMEOUT_US = 10000;

    private MediaCodec mEncoder;

    private volatile boolean mQuit = false;

    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();

    private VirtualDisplay mVirtualDisplay;

    private RESFlvDataCollecter mDataCollecter;

    private int mFrameRate;

    private long startTime = 0;

    public ScreenRecordWorker() { }

    public void init(RESCoreParameters params, int dpi, MediaProjection mp, RESFlvDataCollecter dataCollecter) {
        this.mWidth = params.videoWidth;
        this.mHeight = params.videoHeight;
        this.mBitRate = params.mediacodecAVCBitRate;
        this.mFrameRate = params.mediacodecAVCFrameRate;
        this.mDpi = dpi;
        this.mMediaProjection = mp;
        this.mDataCollecter = dataCollecter;
    }

    /**
     * stop task
     */
    public final void quit() {
        mQuit = true;
    }

    @Override
    public void run() {
        try {
            Surface inputSurface;
            try {
                inputSurface = prepareEncoder();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            mVirtualDisplay = mMediaProjection.createVirtualDisplay(TAG + "-display",
                    mWidth, mHeight, mDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    inputSurface, null, null);
            Log.d(TAG, "created virtual display: " + mVirtualDisplay);
            recordVirtualDisplay();
        } finally {
            release();
        }
    }

    private void recordVirtualDisplay() {
        MediaFormat outputFormat;
        while (!mQuit) {
            int outputBufferId = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_US);
            Log.i(TAG, "dequeue output buffer index=" + outputBufferId);
            if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                outputFormat = mEncoder.getOutputFormat();
                Log.i(TAG, "output format changed.\n new format: " + outputFormat.toString());
                if (mDataCollecter != null) {
                    sendAVCDecoderConfigurationRecord(0, mEncoder.getOutputFormat());
                }
            } else if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.d(TAG, "retrieving buffers time out!");
                try {
                    // wait 10ms
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else if (outputBufferId >= 0) {
                if (startTime == 0) {
                    startTime = mBufferInfo.presentationTimeUs / 1000;
                }

                ByteBuffer outputBuffer = mEncoder.getOutputBuffer(outputBufferId);

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.
                    // Ignore it.
                    Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mBufferInfo.size = 0;
                }
                if (mBufferInfo.size != 0) {
                    Log.d(TAG, "got buffer, info: size=" + mBufferInfo.size
                            + ", presentationTimeUs=" + mBufferInfo.presentationTimeUs
                            + ", offset=" + mBufferInfo.offset);
//                    outputBuffer.position(mBufferInfo.offset);
//                    outputBuffer.limit(mBufferInfo.offset + mBufferInfo.size);
                    if (mDataCollecter != null) {
                        outputBuffer.position(mBufferInfo.offset + 4);
                        outputBuffer.limit(mBufferInfo.offset + mBufferInfo.size);
                        sendRealData(mBufferInfo.presentationTimeUs / 1000 - startTime, outputBuffer);
                    }

                    Log.i(TAG, "sent " + mBufferInfo.size + " bytes to muxer...");
                } else {
                    Log.d(TAG, "info.size == 0, drop it.");
                }
                mEncoder.releaseOutputBuffer(outputBufferId, false);
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    // when EOS come.
                    mQuit = true;
                    break;      // out of while
                }
            }
        }
    }

    private void sendAVCDecoderConfigurationRecord(long tms, MediaFormat format) {
        byte[] AVCDecoderConfigurationRecord = Packager.H264Packager.generateAVCDecoderConfigurationRecord(format);
        int packetLen = Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH +
                AVCDecoderConfigurationRecord.length;
        byte[] finalBuff = new byte[packetLen];
        Packager.FLVPackager.fillFlvVideoTag(finalBuff,
                0,
                true,
                true,
                AVCDecoderConfigurationRecord.length);
        System.arraycopy(AVCDecoderConfigurationRecord, 0,
                finalBuff, Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH, AVCDecoderConfigurationRecord.length);
        RESFlvData resFlvData = new RESFlvData();
        resFlvData.droppable = false;
        resFlvData.byteBuffer = finalBuff;
        resFlvData.size = finalBuff.length;
        resFlvData.dts = (int) tms;
        resFlvData.flvTagType = RESFlvData.FLV_RTMP_PACKET_TYPE_VIDEO;
        resFlvData.videoFrameType = RESFlvData.NALU_TYPE_IDR;
        mDataCollecter.collect(resFlvData, RESRtmpSender.FROM_VIDEO);
    }

    private void sendRealData(long tms, ByteBuffer realData) {
        int realDataLength = realData.remaining();
        int packetLen = Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH +
                Packager.FLVPackager.NALU_HEADER_LENGTH +
                realDataLength;
        byte[] finalBuff = new byte[packetLen];
        realData.get(finalBuff, Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH +
                        Packager.FLVPackager.NALU_HEADER_LENGTH,
                realDataLength);
        int frameType = finalBuff[Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH +
                Packager.FLVPackager.NALU_HEADER_LENGTH] & 0x1F;
        Packager.FLVPackager.fillFlvVideoTag(finalBuff,
                0,
                false,
                frameType == 5,
                realDataLength);
        RESFlvData resFlvData = new RESFlvData();
        resFlvData.droppable = true;
        resFlvData.byteBuffer = finalBuff;
        resFlvData.size = finalBuff.length;
        resFlvData.dts = (int) tms;
        resFlvData.flvTagType = RESFlvData.FLV_RTMP_PACKET_TYPE_VIDEO;
        resFlvData.videoFrameType = frameType;
        mDataCollecter.collect(resFlvData, RESRtmpSender.FROM_VIDEO);
    }

    private Surface prepareEncoder() throws IOException {
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

        Log.d(TAG, "created video format: " + format);
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        Surface inputSurface = mEncoder.createInputSurface();
        Log.d(TAG, "created input surface: " + inputSurface);
        mEncoder.start();
        return inputSurface;
    }

    private void release() {
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
        }
    }
}
