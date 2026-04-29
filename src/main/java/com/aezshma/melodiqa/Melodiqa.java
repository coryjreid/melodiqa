package com.aezshma.melodiqa;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import club.minnced.discord.jdave.interop.JDaveSessionFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "melodiqa", mixinStandardHelpOptions = true, description = "Streams audio from an audio input device to a voice channel in a Discord server")
public class Melodiqa implements Runnable, CommandLine.IExitCodeGenerator {
    private static final Logger sLogger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final Path STOP_FILE_PATH = resolveStopFilePath();
    // COMMAND LINE ARGUMENTS
    @Option(names = {"-d", "--print-devices"}, description = "Print available audio devices")
    private boolean mPrintDevices;
    @Option(names = {"-f", "--config"}, description = "Path to configuration file")
    private String mConfigFilePath;
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
            .filter(info -> AudioSystem.getMixer(info).isLineSupported(new Line.Info(TargetDataLine.class)))
            .collect(Collectors.toMap(Mixer.Info::getName, AudioSystem::getMixer));
        final List<String> mixerNames = mixersMap.keySet().stream().sorted().toList();

        // Prints available audio devices and returns early
        if (mPrintDevices) {
            sLogger.info("Available devices ({}):", mixerNames.size());
            for (int i = 0; i < mixerNames.size(); i++) {
                sLogger.info("[{}] {}", i, mixerNames.get(i));
            }
            return;
        }

        final MelodiqaConfig config = MelodiqaConfig.fromFilePath(mConfigFilePath);

        final Mixer targetMixer;
        // Selects target audio device or exits on failure
        if (!mixerNames.contains(config.getAudioDeviceName())) {
            sLogger.error("Audio device name not found: {}", config.getAudioDeviceName());
            mExitCode = 1;
            return;
        } else {
            targetMixer = mixersMap.get(mixerNames.get(mixerNames.indexOf(config.getAudioDeviceName())));
        }

        mAudioReceiveThread = new Thread(() -> {
            try (
                final TargetDataLine dataLine = (TargetDataLine) targetMixer.getLine(new DataLine.Info(
                    TargetDataLine.class,
                    AudioSendHandler.INPUT_FORMAT))) {

                dataLine.open();
                dataLine.start();

                while (!mShutdown.get()) {
                    if (STOP_FILE_PATH.toFile().exists()) {
                        sLogger.info("Stopping audio receive thread due to stop file");
                        mShutdown.set(true);
                        break;
                    }

                    final byte[] data = new byte[1920 * 2];
                    dataLine.read(data, 0, data.length);
                    mAudioSendQueue.add(data);
                }

                dataLine.stop();
            } catch (final LineUnavailableException exception) {
                sLogger.error("Failed to open audio device", exception);
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
        mJda = JDABuilder.createDefault(config.getDiscordBotToken(), intents)
            .setAudioModuleConfig(new AudioModuleConfig().withDaveSessionFactory(new JDaveSessionFactory()))
            .setActivity(Activity.listening("to jams")) // Inform users that we are jammin' it out
            .setStatus(OnlineStatus.DO_NOT_DISTURB)           // Please don't disturb us while we're jammin'
            .enableCache(CacheFlag.VOICE_STATE)               // Enable the VOICE_STATE cache to find a user's connected voice channel
            .build();                                         // Login with these options

        try {
            mJda.awaitReady();
        } catch (final InterruptedException exception) {
            sLogger.error("JDA failed to be ready", exception);
            mExitCode = 1;
            return;
        }

        sLogger.info("Starting audio receive thread");
        mAudioReceiveThread.start();

        try {
            sLogger.info("Waiting for audio receive thread to finish");
            mAudioReceiveThread.join();
        } catch (final InterruptedException exception) {
            sLogger.warn("Audio receive thread interrupted", exception);
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
        try {
            final boolean stopFileDeleted = Files.deleteIfExists(STOP_FILE_PATH);
            if (stopFileDeleted) {
                sLogger.info("Stop file deleted");
            } else {
                sLogger.warn("Stop file not found");
            }
        } catch (final IOException exception) {
            sLogger.error("Failed to delete stop file due to IO error", exception);
        }
    }

    static Path resolveStopFilePath(final String osName, final String appData, final String userHome) {
        if (osName == null) {
            throw new IllegalStateException("os.name system property is not set");
        }
        if (osName.toLowerCase().contains("win")) {
            if (appData == null) {
                throw new IllegalStateException("APPDATA environment variable is not set");
            }
            return Paths.get(appData, "Aezshma", "Melodiqa", ".stop");
        }
        return Paths.get(userHome, ".local", "share", "Aezshma", "Melodiqa", ".stop");
    }

    static void main(final String[] args) {
        final Melodiqa melodiqa = new Melodiqa();
        Runtime.getRuntime().addShutdownHook(new Thread(melodiqa::shutdown));

        System.exit(new CommandLine(melodiqa).execute(args));
    }

    private static Path resolveStopFilePath() {
        return resolveStopFilePath(
            System.getProperty("os.name"),
            System.getenv("APPDATA"),
            System.getProperty("user.home"));
    }
}
