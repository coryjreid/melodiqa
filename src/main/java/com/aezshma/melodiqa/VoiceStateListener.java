package com.aezshma.melodiqa;

import java.lang.invoke.MethodHandles;

import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VoiceStateListener extends ListenerAdapter {
    private static final Logger sLogger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final BotController mController;

    public VoiceStateListener(final BotController controller) {
        mController = controller;
    }

    @Override
    public void onGuildVoiceUpdate(final GuildVoiceUpdateEvent event) {
        if (event.getMember().equals(event.getGuild().getSelfMember())) {
            if (event.getChannelLeft() != null) {
                sLogger.info("Bot was removed from voice channel by admin, resetting state");
                mController.leave();
            }
            return;
        }

        final AudioChannel botChannel = mController.getConnectedChannel();
        if (botChannel == null) return;

        final Long connectedGuildId = mController.getConnectedGuildId();
        if (connectedGuildId == null || connectedGuildId.longValue() != event.getGuild().getIdLong()) return;

        final AudioChannel joined = event.getChannelJoined();
        if (botChannel.equals(joined)) {
            sLogger.info("User joined bot channel, cancelling idle timer");
            mController.cancelIdleTimer();
            return;
        }

        final AudioChannel left = event.getChannelLeft();
        if (botChannel.equals(left)) {
            final long nonBotCount = left.getMembers().stream()
                .filter(m -> !m.getUser().isBot())
                .count();
            if (nonBotCount == 0) {
                sLogger.info("Bot channel is empty, starting idle timer");
                mController.startIdleTimer();
            }
        }
    }
}
