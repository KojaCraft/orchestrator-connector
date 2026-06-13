package net.kojacoord.connector.neoforge;

import net.kojacoord.connector.core.OrchestratorConnector;
import net.kojacoord.connector.core.ReflectiveServerHooks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.io.File;

/**
 * NeoForge server adapter. Same shape as the Forge one against NeoForge's event
 * packages; the heavy lifting is in the version-agnostic core.
 */
@Mod("kojaorchestrator")
public final class NeoForgeConnector {
    private OrchestratorConnector connector;

    public NeoForgeConnector() {
        NeoForge.EVENT_BUS.register(this);
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
