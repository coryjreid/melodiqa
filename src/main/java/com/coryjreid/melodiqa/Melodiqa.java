package com.coryjreid.melodiqa;

import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "melodiqa", mixinStandardHelpOptions = true, description = "Streams audio from an audio input device to a voice channel in a Discord server")
public class Melodiqa implements Runnable, CommandLine.IExitCodeGenerator {
    private static final Logger sLogger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String ENV_VAR_DISCORD_TOKEN = "MELODIQA_DISCORD_TOKEN";

    // COMMAND LINE ARGUMENTS
    @Parameters(index = "0", description = "Discord server id", arity = "0..1")
    private long mGuildId;
    @Parameters(index = "1", description = "Discord voice channel id", arity = "0..1")
    private long mChannelId;
    @Option(names = {"-d", "--print-devices"}, description = "Print available audio devices")
    private boolean mPrintDevices;
    @ArgGroup
    private DeviceKey mDeviceKey;
    @ArgGroup
    private DiscordToken mDiscordToken;

    // IMMUTABLE STATE
    private final Queue<byte[]> mAudioSendQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean mShutdown = new AtomicBoolean(false);

    // MUTABLE STATE
    private int mExitCode;
    private Thread mAudioReceiveThread;
    private JDA mJda;
    private AudioManager mAudioManager;

    @Override
    public void run() {
        final Map<String, Mixer> mixersMap = Arrays.stream(AudioSystem.getMixerInfo())
            .filter(info -> info.getDescription().contains("DirectSound Capture"))
            .collect(Collectors.toMap(Mixer.Info::getName, AudioSystem::getMixer));
        final List<String> mixerNames = mixersMap.keySet().stream().sorted().toList();

        if (mPrintDevices) {
            sLogger.info("Available devices ({}):", mixerNames.size());
            for (int i = 0; i < mixerNames.size(); i++) {
                sLogger.info("[{}] {}", i, mixerNames.get(i));
            }
            return;
        }

        final int audioDeviceIndex = mDeviceKey.mIndex;
        final Mixer targetMixer;
        switch (mDeviceKey.getKeyType()) {
            case NAME:
                if (!mixerNames.contains(mDeviceKey.mName)) {
                    sLogger.error("Audio device name not found: {}", mDeviceKey.mName);
                    mExitCode = 1;
                    return;
                } else {
                    targetMixer = mixersMap.get(mixerNames.get(mixerNames.indexOf(mDeviceKey.mName)));
                }
                break;
            case INDEX:
                if (audioDeviceIndex < 0 || audioDeviceIndex >= mixerNames.size()) {
                    sLogger.error("Audio device index out of range: {}", audioDeviceIndex);
                    mExitCode = 1;
                    return;
                } else {
                    targetMixer = mixersMap.get(mixerNames.get(audioDeviceIndex));
                }
                break;
            default:
                sLogger.error("No audio device key specified");
                mExitCode = 1;
                return;
        }

        mAudioReceiveThread = new Thread(() -> {
            try (
                final TargetDataLine dataLine = (TargetDataLine) targetMixer.getLine(new DataLine.Info(
                    TargetDataLine.class,
                    AudioSendHandler.INPUT_FORMAT))) {

                dataLine.open();
                dataLine.start();

                while (!mShutdown.get()) {
                    final byte[] data = new byte[1920 * 2];
                    dataLine.read(data, 0, data.length);
                    mAudioSendQueue.add(data);
                }

                dataLine.stop();
            } catch (final LineUnavailableException e) {
                sLogger.error("Failed to open audio device", e);
                mExitCode = 1;
                mShutdown.set(true);
            }
        });

        final EnumSet<GatewayIntent> intents = EnumSet.of(
            // Need messages in guilds to accept commands from users
            GatewayIntent.GUILD_MESSAGES,
            // Need voice states to connect to the voice channel
            GatewayIntent.GUILD_VOICE_STATES,
            // Enable access to message.getContentRaw()
            GatewayIntent.MESSAGE_CONTENT);

        // Start the JDA session with the default mode (voice member cache)
        mJda = JDABuilder.createDefault(mDiscordToken.getToken(), intents)
            .setActivity(Activity.listening("to jams")) // Inform users that we are jammin' it out
            .setStatus(OnlineStatus.DO_NOT_DISTURB)           // Please don't disturb us while we're jammin'
            .enableCache(CacheFlag.VOICE_STATE)               // Enable the VOICE_STATE cache to find a user's connected voice channel
            .build();                                         // Login with these options

        try {
            mJda.awaitReady();
        } catch (final InterruptedException e) {
            sLogger.error("JDA failed to be ready", e);
            mExitCode = 1;
            return;
        }

        final Guild guild = mJda.getGuildById(mGuildId);
        if (guild == null) {
            sLogger.error("Guild not found: {}", mGuildId);
            mExitCode = 1;
            return;
        }

        mAudioManager = guild.getAudioManager();
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

        sLogger.info("Starting audio receive thread");
        mAudioReceiveThread.start();
        sLogger.info("Connecting to voice channel");
        mAudioManager.openAudioConnection(guild.getChannelById(AudioChannel.class, mChannelId));

        try {
            sLogger.info("Waiting for audio receive thread to finish");
            mAudioReceiveThread.join();
        } catch (final InterruptedException e) {
            sLogger.warn("Audio receive thread interrupted", e);
            mShutdown.set(true);
        }
    }

    @Override
    public int getExitCode() {
        return mExitCode;
    }

    private void shutdown() {
        sLogger.info("Shutdown hook triggered");
        if (mAudioManager != null) {
            mAudioManager.closeAudioConnection();
        }
        if (mAudioReceiveThread != null) {
            mAudioReceiveThread.interrupt();
        }
        if (mJda != null) {
            mJda.shutdown();
        }
    }

    public static void main(final String[] args) {
        final Melodiqa melodiqa = new Melodiqa();
        Runtime.getRuntime().addShutdownHook(new Thread(melodiqa::shutdown));

        final int exitCode = new CommandLine(melodiqa).execute(args);
        System.exit(exitCode);
    }

    static class DeviceKey {
        @Option(names = {"-n", "--device-name"}, paramLabel = "DEVICE_NAME", required = true)
        String mName;

        @Option(names = {"-i", "--device-index"}, paramLabel = "DEVICE_INDEX", required = true)
        int mIndex;

        KeyType getKeyType() {

            if (mName != null) {
                return KeyType.NAME;
            } else if (mIndex >= 0) {
                return KeyType.INDEX;
            } else {
                throw new RuntimeException("No device key specified");
            }
        }

        enum KeyType {
            NAME,
            INDEX
        }
    }

    static class DiscordToken {
        @Option(names = {"-t", "--token"}, paramLabel = "TOKEN", required = true)
        String mToken;

        @Option(names = {"-e", "--use-environment-variable"}, required = true)
        boolean mUseEnvironmentVariable;

        String getToken() {
            if (mUseEnvironmentVariable) {
                return System.getenv(ENV_VAR_DISCORD_TOKEN);
            } else {
                return mToken;
            }
        }
    }
}

