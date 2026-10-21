import com.android.ide.common.vectordrawable.Svg2Vector;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Converts the bundled piece sets (design/pieces/<set>/wK.svg ...) to Android VectorDrawables
 * (res/drawable/piece_<set>_wk.xml ...) with Android's own SVG converter, the one Android
 * Studio's "Vector Asset" wizard uses. Run it with Android Studio's Java and libraries:
 *
 *   "C:\Program Files\Android\Android Studio\jbr\bin\java" ^
 *     -cp "C:\Program Files\Android\Android Studio\plugins\android\lib\*;C:\Program Files\Android\Android Studio\lib\*" ^
 *     tools\PieceSvgToVector.java design\pieces app\src\main\res\drawable
 */
public class PieceSvgToVector {
    private static final String[] SETS = {"cburnett", "merida", "chessnut", "fantasy"};
    private static final String[] PIECES = {"K", "Q", "R", "B", "N", "P"};

    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]);
        Path target = Path.of(args[1]);
        int failures = 0;
        for (String set : SETS) {
            for (String color : new String[] {"w", "b"}) {
                for (String piece : PIECES) {
                    Path svg = source.resolve(set).resolve(color + piece + ".svg");
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    String error = Svg2Vector.parseSvgToXml(svg, out);
                    String name = "piece_" + set + "_" + color + piece.toLowerCase() + ".xml";
                    if (out.size() == 0) {
                        System.err.println("FAILED " + svg + ": " + error);
                        failures++;
                        continue;
                    }
                    if (error != null && !error.isEmpty()) {
                        System.err.println("Warning for " + svg + ": " + error);
                    }
                    // Some sets declare 800dp or more. Pieces are always drawn at square size, so
                    // give every drawable a small nominal size; the viewport keeps the shapes.
                    String xml = out.toString(StandardCharsets.UTF_8)
                        .replaceFirst("android:width=\"[^\"]+\"", "android:width=\"48dp\"")
                        .replaceFirst("android:height=\"[^\"]+\"", "android:height=\"48dp\"");
                    Files.writeString(target.resolve(name), xml, StandardCharsets.UTF_8);
                }
            }
        }
        if (failures > 0) {
            System.exit(1);
        }
    }
}
