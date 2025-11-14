package ua.stetsenkoinna.javamethod;

import ua.stetsenkoinna.PetriObj.*;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parser that reads Java-like Petri net construction code and converts it into a {@link PetriNet} object.
 * <p>
 * This class is designed to parse code fragments generated from Petri net Recognition API and extract:
 * <ul>
 *     <li>Places (<code>PetriP</code>)</li>
 *     <li>Transitions (<code>PetriT</code>)</li>
 *     <li>Input arcs (<code>ArcIn</code>)</li>
 *     <li>Output arcs (<code>ArcOut</code>)</li>
 * </ul>
 *
 * <h2>Supported Patterns</h2>
 * <pre>
 * new PetriP("P1", 5)
 * new PetriT("T1", 3.0)
 * new ArcIn(d_P.get(0), d_T.get(1), 2)
 * new ArcOut(d_T.get(1), d_P.get(0), 1)
 * </pre>
 *
 * <h2>Parsing Rules</h2>
 * <ul>
 *     <li>All place and transition definitions are scanned first.</li>
 *     <li>Only after reading all nodes, arc definitions are processed.</li>
 *     <li>Patterns support multiple matches per line.</li>
 *     <li>Indexes in <code>d_P.get()</code> and <code>d_T.get()</code> must be valid; otherwise an exception is thrown.</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>
 * JavaMethodParser parser = new JavaMethodParser();
 * PetriNet net = parser.parse(new File("PetriSketch.petriobj"));
 * </pre>
 *
 * @author  Bohdan Hrontkovskyi
 * @since   14.11.2025
 */
public class JavaMethodParser {

    private final Pattern placePattern = Pattern.compile("new\\s+PetriP\\s*\\(\\s*\"([^\"]+)\"\\s*,\\s*(\\d+)\\s*\\)");
    private final Pattern transitionPattern = Pattern.compile("new\\s+PetriT\\s*\\(\\s*\"([^\"]+)\"\\s*,\\s*([0-9.]+)\\s*\\)");
    private final Pattern arcOutPattern = Pattern.compile("new\\s+ArcOut\\s*\\(\\s*d_T\\.get\\((\\d+)\\)\\s*,\\s*d_P\\.get\\((\\d+)\\)\\s*,\\s*(\\d+)\\s*\\)");
    private final Pattern arcInPattern = Pattern.compile("new\\s+ArcIn\\s*\\(\\s*d_P\\.get\\((\\d+)\\)\\s*,\\s*d_T\\.get\\((\\d+)\\)\\s*,\\s*(\\d+)\\s*\\)");

    private final ArrayList<PetriP> places = new ArrayList<>();
    private final ArrayList<PetriT> transitions = new ArrayList<>();
    private final ArrayList<ArcIn> arcIns = new ArrayList<>();
    private final ArrayList<ArcOut> arcOuts = new ArrayList<>();

    /**
     * Parses a file containing Java-like Petri net construction statements.
     *
     * @param file the file containing model construction code
     * @return a fully built {@link PetriNet} instance
     * @throws Exception if I/O or pattern parsing errors occur
     */
    public PetriNet parse(File file) throws Exception {
        List<String> lines = Files.readAllLines(file.toPath());
        ArrayList<String> arcLines = new ArrayList<>();

        for (String line : lines) {
            Matcher mP = placePattern.matcher(line);
            while (mP.find()) {
                places.add(new PetriP(mP.group(1), Integer.parseInt(mP.group(2))));
            }

            Matcher mT = transitionPattern.matcher(line);
            while (mT.find()) {
                transitions.add(new PetriT(mT.group(1), Double.parseDouble(mT.group(2))));
            }

            if (line.contains("ArcIn") || line.contains("ArcOut")) {
                arcLines.add(line);
            }
        }

        for (String line : arcLines) {
            Matcher mOut = arcOutPattern.matcher(line);
            while (mOut.find()) {
                int t = Integer.parseInt(mOut.group(1));
                int p = Integer.parseInt(mOut.group(2));
                int w = Integer.parseInt(mOut.group(3));

                arcOuts.add(new ArcOut(transitions.get(t), places.get(p), w));
            }

            Matcher mIn = arcInPattern.matcher(line);
            while (mIn.find()) {
                int p = Integer.parseInt(mIn.group(1));
                int t = Integer.parseInt(mIn.group(2));
                int w = Integer.parseInt(mIn.group(3));

                arcIns.add(new ArcIn(places.get(p), transitions.get(t), w));
            }
        }

        return new PetriNet("FromJavaSketch", places, transitions, arcIns, arcOuts);
    }
}