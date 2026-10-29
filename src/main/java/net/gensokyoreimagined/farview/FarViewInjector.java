package net.gensokyoreimagined.farview;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import net.gensokyoreimagined.farview.nms.FarViewNms;
import org.bukkit.entity.Player;

import java.util.NoSuchElementException;

final class FarViewInjector {
    private FarViewInjector() {}

    static FarViewSession inject(FarViewPlugin plugin, Player player) {
        FarViewNms nms = plugin.nms();
        Channel channel = nms.channel(player);
        uninject(plugin, player);

        FarViewSession session = new FarViewSession(
            player, channel, plugin,
            player.getWorld().getKey(), player.getSendViewDistance(),
            plugin.settings().rate());

        channel.pipeline().addFirst(FarViewWireMeter.NAME, new FarViewWireMeter(session));
        nms.inject(channel, session);
        return session;
    }

    static void uninject(FarViewPlugin plugin, Player player) {
        ChannelPipeline pipeline = plugin.nms().channel(player).pipeline();
        for (String name : new String[] { FarViewNms.OUTBOUND_HANDLER, FarViewNms.INBOUND_HANDLER, FarViewWireMeter.NAME }) {
            // A closing channel tears its pipeline down on the event loop, so the handler can vanish at any moment.
            try {
                pipeline.remove(name);
            } catch (NoSuchElementException ignored) {
            }
        }
    }
}
