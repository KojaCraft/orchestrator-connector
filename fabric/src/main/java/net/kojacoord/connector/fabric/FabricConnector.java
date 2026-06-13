package net.kojacoord.connector.fabric;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.kojacoord.connector.core.OrchestratorConnector;
import net.kojacoord.connector.core.ReflectiveServerHooks;

import java.io.File;

/**
 * Fabric (and Quilt, via its Fabric compatibility) server adapter. Uses the
 * stable ServerLifecycleEvents and hands the MinecraftServer to the
 * version-agnostic core, which reads player data reflectively.
 */
public final class FabricConnector implements DedicatedServerModInitializer {
    private OrchestratorConnector connector;

    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            File dataDir = new File(System.getProperty("user.dir", "."));
            connector = new OrchestratorConnector(new ReflectiveServerHooks(server, dataDir));
            connector.onServerStarted();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (connector != null) connector.onServerStopping();
        });
    }
}
