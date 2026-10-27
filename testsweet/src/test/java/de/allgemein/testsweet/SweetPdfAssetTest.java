package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SweetPdfAssetTest {
    @TempDir Path temp;

    @Test void allCurrentReportAssetsRemainLoadableFromClasspath() {
        for (String name : List.of("transaktionsarchitektur.jpg", "bedarfsberechnung_sgb12.jpg",
                "vier_augen_workflow.jpg", "simulator_zyklus.jpg", "stresstest_zwei_wellen.png")) {
            SweetPdf pdf = new SweetPdf();
            pdf.bildZentriert("/sweet/assets/" + name, 100);
            assertTrue(pdf.eintraegeKopie().stream().anyMatch(SweetPdf.BildEintrag.class::isInstance), name);
        }
    }

    @Test void absoluteTraversalAndUnrelatedResourcePathsCannotBeEmbedded() throws Exception {
        Path outside = image(temp.resolve("private.png"));
        for (String path : List.of(outside.toString(), "../private.png", "sweet/assets/../../private.png",
                "doc/manual-bilder/../../../private.png", "/unrelated/private.png", "file:///private.png", "bad\0.png")) {
            assertNull(SweetPdf.imageAssetName(path), path);
            SweetPdf pdf = new SweetPdf();
            pdf.bildZentriert(path, 100);
            assertFalse(pdf.eintraegeKopie().stream().anyMatch(SweetPdf.BildEintrag.class::isInstance), path);
        }
    }

    @Test void filesystemFallbackUsesOnlyApprovedProjectAssetDirectories() throws Exception {
        Path root = Files.createDirectory(temp.resolve("repo"));
        image(root.resolve("ordinary.png"));
        assertNull(SweetPdf.loadAssetFile(root, "ordinary.png"));
        assertNull(SweetPdf.loadAssetFile(root, "../private.png"));
        Path approved = root.resolve("doc/manual-bilder/approved.png");
        image(approved);
        var loaded = SweetPdf.loadAssetFile(root, "approved.png");
        assertNotNull(loaded);assertEquals(5, loaded.pixelBreite());assertEquals(4, loaded.pixelHoehe());
    }

    private static Path image(Path file) throws Exception {
        Files.createDirectories(file.getParent());
        assertTrue(ImageIO.write(new BufferedImage(5, 4, BufferedImage.TYPE_INT_RGB), "png", file.toFile()));
        return file;
    }
}
