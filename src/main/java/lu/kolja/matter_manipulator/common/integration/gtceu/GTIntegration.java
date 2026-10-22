package lu.kolja.matter_manipulator.common.integration.gtceu;

import lu.kolja.matter_manipulator.MatterManipulator;
import lu.kolja.matter_manipulator.api.IntegrationRegistrar;
import lu.kolja.matter_manipulator.common.integration.gtceu.common.GTAccess;
import lu.kolja.matter_manipulator.common.integration.gtceu.common.GTBlockIntegrations;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.jetbrains.annotations.Nullable;

public final class GTIntegration {

    public static final ResourceLocation PIPE_ID = new ResourceLocation("gtceu", "pipe");
    public static final ResourceLocation MACHINE_ID = new ResourceLocation("gtceu", "machine");
    public static final ResourceLocation MUFFLER_ID = new ResourceLocation("gtceu", "muffler");
    public static final ResourceLocation CIRCUIT_ID = new ResourceLocation("gtceu", "circuit");
    public static final ResourceLocation PAINT_ID = new ResourceLocation("gtceu", "paint");
    public static final ResourceLocation COVER_ID = new ResourceLocation("gtceu", "covers");
    public static final ResourceLocation MAINTENANCE_ID = new ResourceLocation("gtceu", "maintenance");

    public static final String GTM7_VERSIONS = "[1.7.0,2.0.0-alpha),[7.5.0-alpha,8.0.0-alpha)";
    public static final String GTM8_VERSIONS = "[8.0.0-alpha,9.0.0-alpha)";

    private static @Nullable GTAccess access;

    private GTIntegration() {}

    public static void register(IntegrationRegistrar registrar) {
        if (access == null) {
            MatterManipulator.LOGGER.warn("GTM {} is not supported (supported versions: V7: {}, V8: {}); ",
                installedVersion(), GTM7_VERSIONS, GTM8_VERSIONS);
            return;
        }
        GTBlockIntegrations.register(registrar, access);
    }

    public static @Nullable GTAccess access() {
        return access;
    }

    public static boolean installedIn(String versions) {
        return ModList.get().isLoaded("gtceu") && covers(versions, installedVersion());
    }

    public static void install(GTAccess adapter) {
        access = adapter;
        MatterManipulator.LOGGER.info("GTM {} detected, using the {} adapter", installedVersion(), adapter.name());
    }

    static boolean covers(String versions, ArtifactVersion version) {
        try {
            return VersionRange.createFromVersionSpec(versions).containsVersion(version);
        } catch (InvalidVersionSpecificationException exception) {
            throw new IllegalArgumentException(versions, exception);
        }
    }

    private static ArtifactVersion installedVersion() {
        return ModList.get().getModContainerById("gtceu").orElseThrow().getModInfo().getVersion();
    }
}
