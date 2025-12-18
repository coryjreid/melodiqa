package com.aezshma.melodiqa;

import java.io.File;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;

public class MelodiqaConfig {
    private static final String CONFIG_KEY = "melodiqa";
    private static final String AUDIO_DEVICE_NAME_KEY = "audioDeviceName";
    private static final String DISCORD_BOT_TOKEN_KEY = "discordBotToken";
    private static final String GUILD_ID_KEY = "guildId";
    private static final String VOICE_CHANNEL_ID_KEY = "voiceChannelId";
    private static final Config REFERENCE_CONFIG =
        ConfigFactory.parseMap(ImmutableMap.<String, ImmutableMap<String, Object>>builder().put(
            CONFIG_KEY,
            ImmutableMap.<String, Object>builder()
                .put(AUDIO_DEVICE_NAME_KEY, "Built-in Microphone")
                .put(DISCORD_BOT_TOKEN_KEY, "DISCORD_BOT_TOKEN")
                .put(GUILD_ID_KEY, 1234567890123456789L)
                .put(VOICE_CHANNEL_ID_KEY, 1234567890123456789L)
                .build()).build());

    private final Config mConfig;

    private MelodiqaConfig(final Config config) {
        Preconditions.checkNotNull(config, "config cannot be null");
        config.checkValid(REFERENCE_CONFIG, CONFIG_KEY);
        mConfig = config.getConfig(CONFIG_KEY);
    }

    public String getAudioDeviceName() {
        return mConfig.getString(AUDIO_DEVICE_NAME_KEY);
    }

    public String getDiscordBotToken() {
        return mConfig.getString(DISCORD_BOT_TOKEN_KEY);
    }

    public long getGuildId() {
        return mConfig.getLong(GUILD_ID_KEY);
    }

    public long getVoiceChannelId() {
        return mConfig.getLong(VOICE_CHANNEL_ID_KEY);
    }

    @Override
    public String toString() {
        return mConfig.root().render(ConfigRenderOptions.concise().setFormatted(true));
    }

    public static MelodiqaConfig fromFilePath(final String filePath) {
        return new MelodiqaConfig(ConfigFactory.parseFile(new File(filePath)));
    }

    public static MelodiqaConfig fromResource(final String resourceBaseName) {
        return new MelodiqaConfig(ConfigFactory.parseResources(resourceBaseName));
    }
}
