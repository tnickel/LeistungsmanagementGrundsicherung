package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BrowserProzesseTest {
    @TempDir Path temp;

    @Test void fremdeBrowserUndTreiberWerdenNichtAusgewaehlt() {
        Path eigenes = temp.resolve("eigenes profil");
        assertFalse(BrowserProzesse.gehoertZuProfil("C:/Chrome/chrome.exe",
                List.of("--test-type", "--user-data-dir=" + temp.resolve("anderes")), eigenes));
        assertFalse(BrowserProzesse.gehoertZuProfil("C:/Chrome/chromedriver.exe",
                List.of("--user-data-dir=" + eigenes), eigenes));
        assertFalse(BrowserProzesse.gehoertZuProfil("C:/Chrome/chrome.exe",
                List.of("--user-data-dir=" + eigenes + "-fremd"), eigenes));
        assertFalse(BrowserProzesse.gehoertZuProfil("C:/Chrome/chrome.exe", List.of("--test-type"), eigenes));
    }

    @Test void nurVollstaendigesEigenesProfilMitLeerzeichenWirdErkannt() {
        Path eigenes = temp.resolve("eigenes profil");
        assertTrue(BrowserProzesse.gehoertZuProfil("C:/Chrome/chrome.exe",
                List.of("--user-data-dir=" + eigenes), eigenes));
        assertTrue(BrowserProzesse.gehoertZuProfil("C:/Chrome/chrome.exe",
                List.of("--user-data-dir", eigenes.toString()), eigenes));
    }
}
