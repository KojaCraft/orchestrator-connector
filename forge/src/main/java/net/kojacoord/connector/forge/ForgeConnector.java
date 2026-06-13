package net.kojacoord.connector.forge;

import net.kojacoord.connector.core.OrchestratorConnector;
import net.kojacoord.connector.core.ReflectiveServerHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.File;

/**
 * Forge server adapter. Hooks the server lifecycle and delegates to the
 * version-agnostic core, which reads player data reflectively off the
 * MinecraftServer so the same code works across Forge/MC versions.
 */
@Mod("kojaorchestrator")
public final class ForgeConnector {
    private OrchestratorConnector connector;

    public ForgeConnector() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onStarted(ServerStartedEvent event) {
        File dataDir = new File(System.getProperty("user.dir", "."));
        connector = new OrchestratorConnector(new ReflectiveServerHooks(event.getServer(), dataDir));
        connector.onServerStarted();
    }

    @SubscribeEvent
    public void onStopping(ServerStoppingEvent event) {
        if (connector != null) connector.onServerStopping();
    }
}
