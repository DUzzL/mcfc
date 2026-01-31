package dev.epicpix.minecraftfunctioncompiler.mod.test;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.flag.FeatureFlagSet;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class TestPack extends Pack {
    public TestPack() {
        var location = new PackLocationInfo("mcfc-test", Component.literal("Minecraft Function Compiler Test Pack"), PackSource.BUILT_IN, Optional.empty());
        var resources = new TestPackResourcesSupplier();
        var metadata = new Metadata(Component.empty(), PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of());
        var selectionConfig = new PackSelectionConfig(true, Position.TOP, true);
        super(location, resources, metadata, selectionConfig);
        resources.setPack(this);
    }

    static class TestPackResourcesSupplier implements ResourcesSupplier {

        @Nullable private TestPack pack;

        @Override
        public PackResources openPrimary(PackLocationInfo location) {
            return new TestPackResources(Objects.requireNonNull(pack));
        }

        @Override
        public PackResources openFull(PackLocationInfo location, Metadata metadata) {
            return openPrimary(location);
        }

        public void setPack(TestPack pack) {
            this.pack = pack;
        }
    }

    static class TestPackResources implements PackResources {

        private static final Logger LOGGER = LoggerFactory.getLogger(TestPackResources.class);

        private final TestPack pack;

        TestPackResources(TestPack pack) {
            this.pack = pack;
        }

        @Override
        public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
            LOGGER.info("getRootResource {}", Arrays.toString(path));
            return null;
        }

        @Override
        public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier location) {
            LOGGER.info("getResource {} {}", type, location);
            return null;
        }

        @Override
        public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
            LOGGER.info("listResources {} {} {}", type, namespace, directory);
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            LOGGER.info("getNamespaces {}", type);
            return Set.of();
        }

        @Override
        public @Nullable <T> T getMetadataSection(MetadataSectionType<T> metadataSectionType) {
            return null;
        }

        @Override
        public PackLocationInfo location() {
            return pack.location();
        }

        @Override
        public void close() {}
    }
}
