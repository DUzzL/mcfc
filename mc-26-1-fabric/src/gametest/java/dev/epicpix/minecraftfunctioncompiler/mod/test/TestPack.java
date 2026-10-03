package dev.epicpix.minecraftfunctioncompiler.mod.test;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

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
        public PackMetadataResources openMetadata(PackLocationInfo location) {
            return new TestPackResources(Objects.requireNonNull(pack));
        }

        @Override
        public Stream<PackResources> openResources(PackLocationInfo location, Metadata metadata) {
            return Stream.of(new TestPackResources(Objects.requireNonNull(pack)));
        }

        public void setPack(TestPack pack) {
            this.pack = pack;
        }
    }

    static class TestPackResources implements PackResources {

        private final TestPack pack;

        TestPackResources(TestPack pack) {
            this.pack = pack;
        }

        @Override
        public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
            return null;
        }

        @Override
        public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier location) {
            if(type == PackType.SERVER_DATA && McfcTestHelper.currentTest != null) {
                for(var file : McfcTestHelper.currentTest.files()) {
                    if(file.location().namespace().equals(location.getNamespace()) && file.location().path().equals(location.getPath())) {
                        return () -> new ByteArrayInputStream(file.content().getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
            return null;
        }

        @Override
        public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
            if(type == PackType.SERVER_DATA && McfcTestHelper.currentTest != null) {
                var startCheck = directory + "/";
                for(var file : McfcTestHelper.currentTest.files()) {
                    if(file.location().namespace().equals(namespace) && file.location().path().startsWith(startCheck)) {
                        output.accept(Identifier.fromNamespaceAndPath(namespace, file.location().path()), () -> new ByteArrayInputStream(file.content().getBytes(StandardCharsets.UTF_8)));
                    }
                }
            }
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            if(type == PackType.SERVER_DATA && McfcTestHelper.currentTest != null) {
                var namespaces = new HashSet<String>();
                for(var file : McfcTestHelper.currentTest.files()) {
                    namespaces.add(file.location().namespace());
                }
                return namespaces;
            }else {
                return Set.of();
            }
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
