package ua.stetsenkoinna.graphpresentation.training;

import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class TrainingRevisionDiffTest {
    @Test public void comparisonReportsMarkingAndCoordinatesButIgnoresFormatting() throws Exception {
        String original = "<pnml><net id='n'><page id='p'><place id='queue'><initialMarking><text>1</text></initialMarking><graphics><position x='10' y='20'/></graphics></place></page></net></pnml>";
        String reordered = original.replace("x='10' y='20'", "y='20' x='10'").replace("<page", "\n  <page");
        assertTrue(TrainingRevisionDiff.compare(bytes(original), bytes(reordered), true).isEmpty());
        String corrected = original.replace(">1<", ">2<").replace("x='10'", "x='30'");
        var all = TrainingRevisionDiff.compare(bytes(original), bytes(corrected), true);
        assertEquals(2, all.size());
        assertTrue(all.stream().anyMatch(change -> change.category().equals("marking") && change.before().equals("1") && change.after().equals("2")));
        assertEquals(1, TrainingRevisionDiff.compare(bytes(original), bytes(corrected), false).size());
    }
    private byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
