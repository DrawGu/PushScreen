package com.codyy.pushscreen;

import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.support.annotation.NonNull;
import android.util.Log;

import com.codyy.pushscreen.media.Packager;
import com.codyy.pushscreen.media.RESCoreParameters;
import com.codyy.pushscreen.media.RESFlvData;
import com.codyy.pushscreen.media.RESFlvDataCollecter;
import com.codyy.pushscreen.rtmp.RESRtmpSender;

import java.nio.ByteBuffer;

/**
 * 音频录制器
 * Created by gujiajia on 2017/2/18.
 */

public class AudioRecordWorker implements Runnable {

    private final static String TAG = "AudioRecordWorker";

    private static final String MIME_TYPE = "audio/mp4a-latm";

    public static final int SAMPLES_PER_FRAME = 1024;	// AAC, bytes/frame/channel
    public static final int FRAMES_PER_BUFFER = 25; 	// AAC, frame/buffer/sec

    private MediaCodec mAudioEncoder;

    protected static final int TIMEOUT_USEC = 10000;

    private volatile boolean mQuit = false;

    private long mStartTime;

    private long mStartInputTime;

    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();

    private RESFlvDataCollecter mDataCollecter;

    private RESCoreParameters mCoreParameters;

    public AudioRecordWorker(RESCoreParameters rtmpParams, RESFlvDataCollecter dataCollecter) {
        this.mCoreParameters = rtmpParams;
        this.mDataCollecter = dataCollecter;
    }

    private void initCodec() {
        MediaFormat audioFormat = new MediaFormat();
        audioFormat.setString(MediaFormat.KEY_MIME, MIME_TYPE);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, mCoreParameters.mediacodecAACProfile);
        audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, mCoreParameters.mediacodecAACSampleRate);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, mCoreParameters.mediacodecAACChannelCount);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, mCoreParameters.mediacodecAACBitRate);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mCoreParameters.mediacodecAACMaxInputSize);
        Log.d(TAG, "creatingAudioEncoder,format=" + audioFormat.toString());
        try {
            mAudioEncoder = MediaCodec.createEncoderByType(audioFormat.getString(MediaFormat.KEY_MIME));
        } catch (Exception e) {
            Log.e(TAG, "can`t create audioEncoder!", e);
        }
        mAudioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mAudioEncoder.start();
//        new AudioThread().start();
    }

    @Override
    public void run() {
        initCodec();
        sampleAndEncode();
//        while (!mQuit) {
//            int outputBufferId = mAudioEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
//            if (outputBufferId >=0) {
//                if (mStartTime == 0) {
//                    mStartTime = mBufferInfo.presentationTimeUs / 1000;
//                }
//                ByteBuffer outputBuffer = mAudioEncoder.getOutputBuffer(outputBufferId);
//                Log.d(TAG, "got buffer, info: size=" + mBufferInfo.size
//                        + ", presentationTimeUs=" + mBufferInfo.presentationTimeUs
//                        + ", offset=" + mBufferInfo.offset);
//                if (mBufferInfo.flags != MediaCodec.BUFFER_FLAG_CODEC_CONFIG
//                        && mBufferInfo.size != 0) {
//                    sendRealData((mBufferInfo.presentationTimeUs / 1000) - mStartTime, outputBuffer);
//                }
//                mAudioEncoder.releaseOutputBuffer(outputBufferId, false);
//            } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
//                Log.d(TAG, "AudioSenderThread,MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:" +
//                        mAudioEncoder.getOutputFormat().toString());
////                MediaFormat outputFormat = mAudioEncoder.getOutputFormat();
//                ByteBuffer csd0 = mAudioEncoder.getOutputFormat().getByteBuffer("csd-0");
//                sendAudioSpecificConfig(0, csd0);
//            } else if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
//                try {
//                    TimeUnit.MICROSECONDS.sleep(10);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
        release();
    }

    private void sampleAndEncode() {
        AudioRecord audioRecord = createAudioRecord();
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED)
            audioRecord = null;
        if (audioRecord != null) {
            Log.v(TAG, "AudioThread:readyStart audio recording");
            int bufferSize = mCoreParameters.audioRecoderBufferSize;
            int readBytes;
            audioRecord.startRecording();
            byte[] pcmBuffer = new byte[bufferSize];
            mStartInputTime = System.nanoTime() / 1000;
            for (; !mQuit ;) {
                readBytes = audioRecord.read(pcmBuffer, 0, bufferSize);
                if (readBytes > 0) {
                    // set audio data to encoder
                    long pts = System.nanoTime()/1000 - mStartInputTime;
                    encodeAudio(pcmBuffer, readBytes, pts);
                }
            }
            audioRecord.stop();
            audioRecord.release();
        } else {
            Log.e(TAG, "failed to initialize AudioRecord");
        }
        Log.v(TAG, "AudioThread:finished");
    }

    private void encodeAudio(byte[] buffer, int length, long pts) {
        int inputBufferId = mAudioEncoder.dequeueInputBuffer(-1);
        if (inputBufferId >= 0) {
            Log.d(TAG, "butBuffer length=" + length + ",pts=" + pts);
            ByteBuffer inputBuffer = mAudioEncoder.getInputBuffer(inputBufferId);
            inputBuffer.put(buffer, 0, length);
            mAudioEncoder.queueInputBuffer(inputBufferId, 0, length, pts, 0);
        }
        for (;;) {
            int outBufferIndex = mAudioEncoder.dequeueOutputBuffer(mBufferInfo, 0);
            if (outBufferIndex >=0) {
                if (mStartTime == 0) {
                    mStartTime = mBufferInfo.presentationTimeUs / 1000;
                }
                ByteBuffer outputBuffer = mAudioEncoder.getOutputBuffer(outBufferIndex);
                Log.d(TAG, "got buffer, info: size=" + mBufferInfo.size
                        + ", presentationTimeUs=" + mBufferInfo.presentationTimeUs
                        + ", offset=" + mBufferInfo.offset);
                if (mBufferInfo.flags != MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                        && mBufferInfo.size != 0) {
                    sendRealData((mBufferInfo.presentationTimeUs / 1000) - mStartTime, outputBuffer);
                }
                mAudioEncoder.releaseOutputBuffer(outBufferIndex, false);
            } else if (outBufferIndex == MediaCodec.BUFFER_FLAG_CODEC_CONFIG){
                Log.d(TAG, "AudioSenderThread,MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:" +
                        mAudioEncoder.getOutputFormat().toString());
                ByteBuffer csd0 = mAudioEncoder.getOutputFormat().getByteBuffer("csd-0");
                sendAudioSpecificConfig(0, csd0);
            } else {
                break;
            }
        }
    }

    private void release() {
        if (mAudioEncoder != null) {
            mAudioEncoder.stop();
            mAudioEncoder.release();
        }
    }

    private void sendAudioSpecificConfig(long tms, ByteBuffer realData) {
        int packetLen = Packager.FLVPackager.FLV_AUDIO_TAG_LENGTH +
                realData.remaining();
        byte[] finalBuff = new byte[packetLen];
        realData.get(finalBuff, Packager.FLVPackager.FLV_AUDIO_TAG_LENGTH,
                realData.remaining());
        Packager.FLVPackager.fillFlvAudioTag(finalBuff,
                0,
                true);
        RESFlvData resFlvData = new RESFlvData();
        resFlvData.droppable = false;
        resFlvData.byteBuffer = finalBuff;
        resFlvData.size = finalBuff.length;
        resFlvData.dts = (int) tms;
        resFlvData.flvTagType = RESFlvData.FLV_RTMP_PACKET_TYPE_AUDIO;
        mDataCollecter.collect(resFlvData, RESRtmpSender.FROM_AUDIO);
    }

    private void sendRealData(long tms, ByteBuffer realData) {
        int packetLen = Packager.FLVPackager.FLV_AUDIO_TAG_LENGTH +
                realData.remaining();
        byte[] finalBuff = new byte[packetLen];
        realData.get(finalBuff, Packager.FLVPackager.FLV_AUDIO_TAG_LENGTH,
                realData.remaining());
        Packager.FLVPackager.fillFlvAudioTag(finalBuff,
                0,
                false);
        RESFlvData resFlvData = new RESFlvData();
        resFlvData.droppable = true;
        resFlvData.byteBuffer = finalBuff;
        resFlvData.size = finalBuff.length;
        resFlvData.dts = (int) tms;
        resFlvData.flvTagType = RESFlvData.FLV_RTMP_PACKET_TYPE_AUDIO;
        mDataCollecter.collect(resFlvData, RESRtmpSender.FROM_AUDIO);
    }

    public void quit() {
        mQuit = true;
    }

    /**
     * Thread to capture audio data from internal mic as uncompressed 16bit PCM data
     * and write them to the MediaCodec encoder
     */
    private class AudioThread extends Thread {
        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
            AudioRecord audioRecord = createAudioRecord();
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED)
                audioRecord = null;
            if (audioRecord != null) {
                Log.v(TAG, "AudioThread:readyStart audio recording");
                int bufferSize = mCoreParameters.audioRecoderBufferSize;
                ByteBuffer buf = ByteBuffer.allocateDirect(bufferSize);
                int readBytes;
                audioRecord.startRecording();
                mStartInputTime = System.nanoTime() / 1000;
                for (; !mQuit ;) {
                    // read audio data from internal mic
                    buf.clear();
                    readBytes = audioRecord.read(buf, bufferSize);
                    if (readBytes > 0) {
                        // set audio data to encoder
                        buf.position(readBytes);
                        buf.flip();
                        long pts = System.nanoTime()/1000 - mStartInputTime;
                        encode(buf, readBytes, pts);
                    }
                }
                audioRecord.stop();
                audioRecord.release();
            } else {
                Log.e(TAG, "failed to initialize AudioRecord");
            }
            Log.v(TAG, "AudioThread:finished");
        }
    }

    @NonNull
    private AudioRecord createAudioRecord() {
        int minBufferSize = AudioRecord.getMinBufferSize(
                mCoreParameters.audioRecoderSampleRate,
                mCoreParameters.audioRecoderChannelConfig,
                mCoreParameters.audioRecoderFormat);

        return new AudioRecord(
                mCoreParameters.audioRecoderSource,
                mCoreParameters.audioRecoderSampleRate,
                mCoreParameters.audioRecoderChannelConfig,
                mCoreParameters.audioRecoderFormat,
                minBufferSize * 5);
    }

    /**
     * Method to set byte array to the MediaCodec encoder
     * @param buffer
     * @param length　length of byte array, zero means EOS.
     * @param presentationTimeUs
     */
    protected void encode(ByteBuffer buffer, final int length, final long presentationTimeUs) {
        if (mQuit) return;
        while (!mQuit) {
            int inputBufferIndex = mAudioEncoder.dequeueInputBuffer(0);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = mAudioEncoder.getInputBuffer(inputBufferIndex);
                inputBuffer.clear();
                if (buffer != null) {
                    inputBuffer.put(buffer);
                }
                if (length <= 0) {
                    // send EOS
                    Log.i(TAG, "send BUFFER_FLAG_END_OF_STREAM");
                    mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, 0,
                            presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    break;
                } else {
                    mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, length,
                            presentationTimeUs, 0);
                }
                break;
            } else if (inputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // wait for MediaCodec encoder is ready to encode
                // nothing to do here because MediaCodec#dequeueInputBuffer(TIMEOUT_USEC)
                // will wait for maximum TIMEOUT_USEC(10msec) on each call
            }
        }
    }
}
