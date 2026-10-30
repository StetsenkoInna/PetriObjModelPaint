package ua.stetsenkoinna.graphpresentation.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class TrainingReviewPresentationTest {
    @Test public void taskReviewsAndImageChoicesHideMachineIdentifiersWithoutDiscardingTheirBindings() throws Exception {
        String revision = "a".repeat(64), reviewId = "b".repeat(64);
        var files = new LinkedHashMap<String, byte[]>();
        var json = new ObjectMapper();
        files.put("manifest.json", json.writeValueAsBytes(java.util.Map.of("title", "Queue study")));
        files.put("dataset-history.json", json.writeValueAsBytes(java.util.Map.of("head_revision_id", revision,
                "revisions", List.of(java.util.Map.of("id", revision)), "reviews", List.of(java.util.Map.of("id", reviewId)))));
        files.put("task.json", json.writeValueAsBytes(java.util.Map.of("task", "Use two workers", "clarifications", List.of("FIFO"))));
        files.put("history/reviews/" + reviewId + ".json", json.writeValueAsBytes(java.util.Map.of("revision_id", revision,
                "reviewer", "Reviewer A", "reviewer_kind", "human", "decision", "changes_requested", "comment", "Add the second worker", "findings", List.of())));
        files.put("network.png", new byte[]{1});
        files.put("network-views.json", json.writeValueAsBytes(java.util.Map.of("views", List.of(java.util.Map.of("id", "main", "image", "network.png")))));
        var bundle = new TrainingBundleWorkspace.Bundle(Path.of("sample.zip"), files);
        String text = TrainingReviewPresentation.context(bundle);
        assertTrue(text.contains("Use two workers")); assertTrue(text.contains("Reviewer A")); assertTrue(text.contains("Needs changes"));
        assertFalse(text.contains(revision)); assertFalse(text.contains(reviewId)); assertFalse(text.contains("reviewer_kind"));
        var images = TrainingReviewPresentation.images(bundle);
        assertEquals("network.png", images.getFirst().path());
        assertEquals("Version 1 - Complete network", images.getFirst().toString());
    }

    @Test public void comparisonUsesElementNamesAndDomainTermsRatherThanXmlPaths() {
        String internal = "/pnml/net[@id=n]/page[@id=object2]/place[@id=p123]/initialMarking/text";
        String text = TrainingReviewPresentation.differences(List.of(new TrainingRevisionDiff.Change("marking", internal, "1", "2", "Вільні виконавці")));
        assertTrue(text.contains("Вільні виконавці")); assertTrue(text.contains("1 → 2")); assertTrue(text.contains("Initial tokens"));
        assertFalse(text.contains("pnml")); assertFalse(text.contains("p123")); assertFalse(text.contains("[@id"));
    }
}
