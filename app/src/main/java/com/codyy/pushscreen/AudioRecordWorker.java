package com.codyy.pushscreen;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * 音频录制器
 * Created by gujiajia on 2017/2/18.
 */

public class AudioRecordWorker implements Runnable {

    private final static String TAG = "AudioRecordWorker";

    private static final String MIME_TYPE = "audio/mp4a-latm";
    private static final int SAMPLE_RATE = 44100;	// 44.1[KHz] is only setting guaranteed to be available on all devices.
    private static final int BIT_RATE = 64000;
    public static final int SAMPLES_PER_FRAME = 1024;	// AAC, bytes/frame/channel
    public static final int FRAMES_PER_BUFFER = 25; 	// AAC, frame/buffer/sec

    private MediaCodec mAudioCodec;

    protected static final int TIMEOUT_USEC = 10000;

    private volatile boolean mQuit = false;

    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();

    private AudioRecordWorker() {
        initCodec();
        initAudioRecord();
    }

    private void initCodec() {
        MediaFormat audioFormat = new MediaFormat();
        audioFormat.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 32 * 1024);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8820);
        Log.d(TAG, "creatingAudioEncoder,format=" + audioFormat.toString());
        try {
            mAudioCodec = MediaCodec.createEncoderByType(audioFormat.getString(MediaFormat.KEY_MIME));
        } catch (Exception e) {
            Log.e(TAG, "can`t create audioEncoder!", e);
        }
        mAudioCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }

    private void initAudioRecord() {
    }

    public void collectAndEncode() {
        mAudioCodec.start();
    }

    @Override
    public void run() {
        MediaFormat outputFormat = mAudioCodec.getOutputFormat();
        while (!mQuit) {
            int outputBufferId = mAudioCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (outputBufferId >=0) {
                ByteBuffer outputBuffer = mAudioCodec.getOutputBuffer(outputBufferId);
                // TODO: 2017/2/18
                mAudioCodec.releaseOutputBuffer(outputBufferId, false);
            } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                outputFormat = mAudioCodec.getOutputFormat();
            } else {
                try {
                    TimeUnit.MICROSECONDS.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
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
            try {
                final int min_buffer_size = AudioRecord.getMinBufferSize(
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);
                int buffer_size = SAMPLES_PER_FRAME * FRAMES_PER_BUFFER;
                if (buffer_size < min_buffer_size)
                    buffer_size = ((min_buffer_size / SAMPLES_PER_FRAME) + 1) * SAMPLES_PER_FRAME * 2;

                AudioRecord audioRecord = null;
                    try {
                        audioRecord = new AudioRecord(
                                MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buffer_size);
                        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED)
                            audioRecord = null;
                    } catch (final Exception e) {
                        audioRecord = null;
                    }
                if (audioRecord != null) {
                    try {
                        if (!mQuit) {
                            Log.v(TAG, "AudioThread:readyStart audio recording");
                            ByteBuffer buf = ByteBuffer.allocateDirect(SAMPLES_PER_FRAME);
                            int readBytes;
                            audioRecord.startRecording();
                            try {
                                for (; !mQuit ;) {
                                    // read audio data from internal mic
                                    buf.clear();
                                    readBytes = audioRecord.read(buf, SAMPLES_PER_FRAME);
                                    if (readBytes > 0) {
                                        // set audio data to encoder
                                        buf.position(readBytes);
                                        buf.flip();
                                        encode(buf, readBytes, getPTSUs());
                                    }
                                }
                            } finally {
                                audioRecord.stop();
                            }
                        }
                    } finally {
                        audioRecord.release();
                    }
                } else {
                    Log.e(TAG, "failed to initialize AudioRecord");
                }
            } catch (final Exception e) {
                Log.e(TAG, "AudioThread#run", e);
            }
            Log.v(TAG, "AudioThread:finished");
        }
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
            int inputBufferIndex = mAudioCodec.dequeueInputBuffer(TIMEOUT_USEC);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = mAudioCodec.getInputBuffer(inputBufferIndex);
                inputBuffer.clear();
                if (buffer != null) {
                    inputBuffer.put(buffer);
                }
                if (length <= 0) {
                    // send EOS
                    Log.i(TAG, "send BUFFER_FLAG_END_OF_STREAM");
                    mAudioCodec.queueInputBuffer(inputBufferIndex, 0, 0,
                            presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    break;
                } else {
                    mAudioCodec.queueInputBuffer(inputBufferIndex, 0, length,
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

    /**
     * previous presentationTimeUs for writing
     */
    private long prevOutputPTSUs = 0;
    /**
     * get next encoding presentationTimeUs
     * @return
     */
    protected long getPTSUs() {
        long result = System.nanoTime() / 1000L;
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < prevOutputPTSUs)
            result = (prevOutputPTSUs - result) + result;
        return result;
    }
}
