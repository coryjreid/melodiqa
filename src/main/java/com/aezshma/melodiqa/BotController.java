package com.aezshma.melodiqa;

import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.managers.AudioManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BotController {
    private static final Logger sLogger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Mixer mTargetMixer;
    private final ScheduledExecutorService mScheduler;
    private final Queue<byte[]> mAudioSendQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean mCapturing = new AtomicBoolean(false);

    private volatile TargetDataLine mDataLine;
    private AudioManager mAudioManager;
    private AudioChannel mConnectedChannel;
    private Long mConnectedGuildId;
    private Thread mCaptureThread;
    private ScheduledFuture<?> mIdleTimer;

    public BotController(final Mixer targetMixer, final ScheduledExecutorService scheduler) {
        mTargetMixer = targetMixer;
        mScheduler = scheduler;
    }

    public synchronized void join(final AudioChannel channel) {
        if (isConnected()) return;
        mConnectedChannel = channel;
        mConnectedGuildId = channel.getGuild().getIdLong();
        mAudioManager = channel.getGuild().getAudioManager();
        mAudioManager.setSendingHandler(new AudioSendHandler() {
            @Override
            public boolean canProvide() {
                return !mAudioSendQueue.isEmpty();
            }

            @Override
            public ByteBuffer provide20MsAudio() {
                final byte[] data = mAudioSendQueue.poll();
                return data == null ? null : ByteBuffer.wrap(data);
            }
        });
        mAudioManager.openAudioConnection(channel);

        mCapturing.set(true);
        mCaptureThread = new Thread(() -> {
            try (TargetDataLine dataLine = (TargetDataLine) mTargetMixer.getLine(
                    new DataLine.Info(TargetDataLine.class, AudioSendHandler.INPUT_FORMAT))) {
                mDataLine = dataLine;
                dataLine.open();
                dataLine.start();
                while (mCapturing.get()) {
                    final byte[] data = new byte[1920 * 2];
                    dataLine.read(data, 0, data.length);
                    mAudioSendQueue.add(data);
                }
                dataLine.stop();
            } catch (final LineUnavailableException e) {
                sLogger.error("Failed to open audio device", e);
                leave();
            } finally {
                synchronized (BotController.this) {
                    mDataLine = null;
                }
            }
        });
        mCaptureThread.setDaemon(true);
        mCaptureThread.start();
        sLogger.info("Joined voice channel: {}", channel.getName());
    }

    public void leave() {
        final Thread captureThreadToJoin;
        synchronized (this) {
            if (!isConnected()) return;

            cancelIdleTimer();

            mCapturing.set(false);
            final TargetDataLine dataLine = mDataLine;
            if (dataLine != null) {
                dataLine.stop();
            }
            captureThreadToJoin = (mCaptureThread != Thread.currentThread()) ? mCaptureThread : null;
            mCaptureThread = null;
            mAudioSendQueue.clear();

            if (mAudioManager != null) {
                mAudioManager.closeAudioConnection();
                mAudioManager = null;
            }

            mConnectedChannel = null;
            mConnectedGuildId = null;
            sLogger.info("Left voice channel");
        }
        if (captureThreadToJoin != null) {
            try {
                captureThreadToJoin.join(2000);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public synchronized void startIdleTimer() {
        cancelIdleTimer();
        mIdleTimer = mScheduler.schedule(this::leave, 5, TimeUnit.MINUTES);
        sLogger.info("Idle timer started — disconnecting in 5 minutes if no users join");
    }

    public synchronized void cancelIdleTimer() {
        if (mIdleTimer != null) {
            mIdleTimer.cancel(false);
            mIdleTimer = null;
            sLogger.info("Idle timer cancelled");
        }
    }

    public synchronized boolean isConnected() {
        return mConnectedGuildId != null;
    }

    public synchronized Long getConnectedGuildId() {
        return mConnectedGuildId;
    }

    public synchronized AudioChannel getConnectedChannel() {
        return mConnectedChannel;
    }
}
