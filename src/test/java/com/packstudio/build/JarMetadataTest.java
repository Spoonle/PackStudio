package com.packstudio.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Audits the packaged {@code fabric.mod.json} — the exact resource written into the release jar — for
 * every target, so a jar can never claim the wrong Minecraft version, ship as a server mod, or publish
 * without a real Fabric API lower bound.
 */
class JarMetadataTest {

    private static JsonObject packagedMetadata() throws Exception {
        try (InputStream in = JarMetadataTest.class.getClassLoader().getResourceAsStream("fabric.mod.json")) {
            assertNotNull(in, "fabric.mod.json missing from the packaged resources");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void metadataTargetsTheDeclaredMinecraftVersion() throws Exception {
        JsonObject metadata = packagedMetadata();
        String minecraftVersion = buildIdentityVersion();
        String modVersion = metadata.get("version").getAsString();

        assertTrue(modVersion.endsWith("+mc" + minecraftVersion),
                "mod version '" + modVersion + "' must name Minecraft " + minecraftVersion);

        String minecraftRange = metadata.getAsJsonObject("depends").get("minecraft").getAsString();
        assertTrue(minecraftRange.contains(minecraftVersion),
                "minecraft dependency '" + minecraftRange + "' does not target " + minecraftVersion);
        // Exact-version targeting: never a single jar spanning several Minecraft versions.
        assertFalse(minecraftRange.matches("^\\s*>=?[^<]*$") && !minecraftRange.contains(" ") && !minecraftRange.startsWith("~"),
                "minecraft dependency '" + minecraftRange + "' is not bounded to " + minecraftVersion);
    }

    @Test
    void metadataIsClientOnlyJava25WithFabricApiDependency() throws Exception {
        JsonObject metadata = packagedMetadata();
        assertEquals("client", metadata.get("environment").getAsString(), "mod must stay client-side only");

        JsonObject depends = metadata.getAsJsonObject("depends");
        assertEquals(">=25", depends.get("java").getAsString(), "Java 25 is the documented minimum");
        assertTrue(depends.has("fabric-api"), "fabric-api dependency missing");
        String fabricApi = depends.get("fabric-api").getAsString();
        // The 26.3 baseline ships the untouched `fabric-api: "*"` requirement; every target added after it
        // must pin the official Fabric API build of its own Minecraft version instead of a wildcard.
        if ("26.3".equals(buildIdentity("minecraft_family"))) {
            assertEquals("*", fabricApi, "the 26.3 baseline keeps its original fabric-api requirement");
        } else {
            assertFalse(fabricApi.equals("*"),
                    "fabric-api dependency must have a real lower bound for family "
                            + buildIdentity("minecraft_family"));
        }
        assertTrue(depends.has("fabricloader"), "fabricloader dependency missing");
        assertEquals("com.packstudio.client.PackStudioClient",
                metadata.getAsJsonObject("entrypoints").getAsJsonArray("client").get(0).getAsString());
    }

    private static String buildIdentityVersion() throws Exception {
        return buildIdentity("minecraft_version");
    }

    private static String buildIdentity(String key) throws Exception {
        try (InputStream in = JarMetadataTest.class.getClassLoader()
                .getResourceAsStream("packstudio-build.properties")) {
            assertNotNull(in, "packstudio-build.properties missing");
            java.util.Properties properties = new java.util.Properties();
            properties.load(in);
            return properties.getProperty(key);
        }
    }
}
