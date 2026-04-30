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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
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

    @Option(names = {"-d", "--print-devices"}, description = "Print available audio devices")
    private boolean mPrintDevices;
    @Option(names = {"-f", "--config"}, description = "Path to configuration file")
    private String mConfigFilePath;

    private int mExitCode;
    private JDA mJda;
    private BotController mController;
    private ScheduledExecutorService mScheduler;

    @Override
    public void run() {
        final Map<String, Mixer> mixersMap = Arrays.stream(AudioSystem.getMixerInfo())
            .filter(info -> AudioSystem.getMixer(info).isLineSupported(new Line.Info(TargetDataLine.class)))
            .collect(Collectors.toMap(Mixer.Info::getName, AudioSystem::getMixer));
        final List<String> mixerNames = mixersMap.keySet().stream().sorted().toList();

        if (mPrintDevices) {
            sLogger.info("Available devices ({}):", mixerNames.size());
            for (int i = 0; i < mixerNames.size(); i++) {
                sLogger.info("[{}] {}", i, mixerNames.get(i));
            }
            return;
        }

        final MelodiqaConfig config = MelodiqaConfig.fromFilePath(mConfigFilePath);

        if (!mixerNames.contains(config.getAudioDeviceName())) {
            sLogger.error("Audio device name not found: {}", config.getAudioDeviceName());
            mExitCode = 1;
            return;
        }

        final Mixer targetMixer = mixersMap.get(config.getAudioDeviceName());
        mScheduler = Executors.newSingleThreadScheduledExecutor();
        mController = new BotController(targetMixer, mScheduler);

        mJda = JDABuilder.createDefault(
                config.getDiscordBotToken(),
                EnumSet.of(GatewayIntent.GUILD_VOICE_STATES))
            .setAudioModuleConfig(new AudioModuleConfig().withDaveSessionFactory(new JDaveSessionFactory()))
            .setActivity(Activity.listening("to jams"))
            .setStatus(OnlineStatus.DO_NOT_DISTURB)
            .enableCache(CacheFlag.VOICE_STATE)
            .addEventListeners(
                new SlashCommandListener(mController),
                new VoiceStateListener(mController))
            .build();

        try {
            mJda.awaitReady();
        } catch (final InterruptedException e) {
            sLogger.error("JDA failed to be ready", e);
            mExitCode = 1;
            return;
        }

        mJda.updateCommands().addCommands(
            Commands.slash("join", "Joins your current voice channel"),
            Commands.slash("leave", "Leaves the current voice channel")
        ).queue();

        sLogger.info("Bot ready. Use /join in Discord to invite the bot to your voice channel.");

        while (!STOP_FILE_PATH.toFile().exists()) {
            try {
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        sLogger.info("Stop file detected, shutting down");
        mController.leave();
        mJda.shutdown();
        mScheduler.shutdown();

        try {
            Files.deleteIfExists(STOP_FILE_PATH);
        } catch (final IOException e) {
            sLogger.error("Failed to delete stop file", e);
        }
    }

    @Override
    public int getExitCode() {
        return mExitCode;
    }

    private void shutdown() {
        sLogger.info("Shutdown hook triggered");
        if (mController != null) {
            mController.leave();
        }
        if (mJda != null) {
            mJda.shutdown();
        }
        if (mScheduler != null) {
            mScheduler.shutdown();
        }
        try {
            Files.deleteIfExists(STOP_FILE_PATH);
        } catch (final IOException e) {
            sLogger.error("Failed to delete stop file during shutdown", e);
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
