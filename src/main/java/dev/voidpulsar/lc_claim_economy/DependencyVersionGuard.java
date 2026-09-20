package dev.voidpulsar.lc_claim_economy;

import net.neoforged.fml.ModList;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Validates that companion mods are at least the versions this mod was
 * built/tested against. FTB Chunks and OP&C are checked as a <em>minimum</em>
 * version (matching this mod's own {@code versionRange="[X,)"} dependency
 * declaration in {@code neoforge.mods.toml}) rather than an exact match -
 * this mod's mixins target their internal implementation classes (not
 * public API), which is inherently more fragile than a normal API
 * dependency, but pinning to one exact point release meant every routine
 * FTB Chunks/OP&C bugfix release broke this mod's loading entirely until
 * republished. Lightman's Currency stays an exact match: it's a required
 * (not optional) dependency this mod is compiled directly against a much
 * larger API surface of, where a version bump is far more likely to carry
 * an actual breaking change.
 */
public final class DependencyVersionGuard {
    /** Minimum version required; must match {@code ftb_chunks_version} in gradle.properties. */
    public static final String REQUIRED_FTB_CHUNKS_VERSION = "2101.1.20";
    /** Minimum version required; must match {@code opc_version} in gradle.properties. */
    public static final String REQUIRED_OPC_VERSION = "0.27.5";
    /** Must match {@code lightmanscurrency_version} in gradle.properties. */
    public static final String REQUIRED_LIGHTMANS_CURRENCY_VERSION = "1.21-2.3.0.5";

    private DependencyVersionGuard() {
    }

    public static void validateOrThrow() {
        List<String> errors = new ArrayList<>();
        requireExactVersion("lightmanscurrency", "Lightman's Currency", REQUIRED_LIGHTMANS_CURRENCY_VERSION, errors);

        boolean ftbPresent = ModList.get().isLoaded("ftbchunks");
        boolean opcPresent = ModList.get().isLoaded("openpartiesandclaims");

        if (!ftbPresent && !opcPresent) {
            errors.add(
                    "No supported claim backend found. Install either FTB Chunks + FTB Teams + FTB Library, "
                            + "or Open Parties and Claims."
            );
        } else if (ftbPresent) {
            requireMinimumVersion("ftbchunks", "FTB Chunks", REQUIRED_FTB_CHUNKS_VERSION, errors);
        } else {
            requireMinimumVersion("openpartiesandclaims", "Open Parties and Claims", REQUIRED_OPC_VERSION, errors);
        }

        if (errors.isEmpty()) {
            return;
        }

        for (String error : errors) {
            LcClaimEconomy.LOGGER.error(error);
        }

        throw new IllegalStateException(
                "Lightman's Currency: FTB Claim Economy cannot load with incompatible mod versions. "
                        + "Details: "
                        + String.join(" | ", errors)
        );
    }

    private static void requireExactVersion(
            String modId,
            String displayName,
            String requiredVersion,
            List<String> errorsOut
    ) {
        Optional<String> installed = ModList.get()
                .getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString());
        if (installed.isEmpty()) {
            errorsOut.add(displayName + " (" + modId + ") is missing.");
            return;
        }

        if (!requiredVersion.equals(installed.get())) {
            errorsOut.add(displayName + " version mismatch: required " + requiredVersion + ", found " + installed.get() + ".");
        }
    }

    private static void requireMinimumVersion(
            String modId,
            String displayName,
            String requiredVersion,
            List<String> errorsOut
    ) {
        Optional<String> installed = ModList.get()
                .getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString());
        if (installed.isEmpty()) {
            errorsOut.add(displayName + " (" + modId + ") is missing.");
            return;
        }

        ArtifactVersion installedVersion = new DefaultArtifactVersion(installed.get());
        ArtifactVersion minimumVersion = new DefaultArtifactVersion(requiredVersion);
        if (installedVersion.compareTo(minimumVersion) < 0) {
            errorsOut.add(displayName + " version too old: need at least " + requiredVersion + ", found " + installed.get() + ".");
        }
    }
}
