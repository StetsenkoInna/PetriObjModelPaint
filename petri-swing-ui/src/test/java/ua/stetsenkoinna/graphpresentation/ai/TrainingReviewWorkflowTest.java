package ua.stetsenkoinna.graphpresentation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.Component;
import java.awt.Container;
import java.awt.FileDialog;
import java.awt.Window;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.graphpresentation.PetriNetsFrame;
import ua.stetsenkoinna.graphpresentation.io.FileDialogs;
import ua.stetsenkoinna.graphpresentation.training.*;
import ua.stetsenkoinna.pnml.PnmlModelParser;
import static org.junit.Assert.*;

/** Real Swing form/file-chooser flows and real Streamable HTTP calls share one archive contract. */
public class TrainingReviewWorkflowTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private PetriNetsFrame frame;
    private TrainingDataExporter exporter;
    private DesktopMcpServer server;
    private HttpClient client;
    private boolean ukrainianDemo;

    @Before public void setup() throws Exception {
        String pnml;
        try (var input = getClass().getResourceAsStream("/pnml/samples/easy/this-easy.pnml")) { pnml = new String(input.readAllBytes(), StandardCharsets.UTF_8); }
        var canvas = GraphCanvasModel.fromObjModel(new PnmlModelParser().parseXml(pnml));
        SwingUtilities.invokeAndWait(() -> { frame = new PetriNetsFrame(); frame.getPetriNetsPanel().setCanvasModel(canvas); });
        exporter = frame.getTrainingDataExporter();
        server = frame.getMcpServer();
        server.close();
        server.start(0);
        client = HttpClient.newHttpClient();
    }

    @After public void close() throws Exception {
        if (server != null) server.close();
        if (client != null) client.close();
        SwingUtilities.invokeAndWait(() -> { for (Window window : Window.getWindows()) window.dispose(); });
    }

    private JsonNode call(String tool, Map<String, Object> arguments) throws Exception {
        String body = DesktopMcpServer.JSON.writeValueAsString(Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/call", "params", Map.of("name", tool, "arguments", arguments)));
        var response = client.send(HttpRequest.newBuilder(URI.create(server.endpoint())).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        JsonNode result = DesktopMcpServer.JSON.readTree(response.body()).path("result");
        assertFalse(result.toString(), result.path("isError").asBoolean());
        return result;
    }

    private Path httpExport() throws Exception {
        JsonNode result = call("export_training_data", Map.of("title", "Queue review", "task", "Inspect the queue", "output_directory", folder.getRoot().toString(),
                "conversation", List.of(Map.of("role", "user", "content", "Review this queue"))));
        return Path.of(DesktopMcpServer.JSON.readTree(result.at("/content/0/text").asText()).path("path").asText());
    }

    private Path httpReview(String kind) throws Exception {
        JsonNode result = call("export_training_review", Map.of("output_directory", folder.getRoot().toString(), "reviewer", "Review test", "reviewer_kind", kind,
                "decision", "changes_requested", "comment", "Check queue capacity", "findings", List.of(Map.of("severity", "warning", "message", "Capacity must match the task", "view_id", "main"))));
        return Path.of(DesktopMcpServer.JSON.readTree(result.at("/content/0/text").asText()).path("path").asText());
    }

    @Test public void liveCanvasSelectionOpensFindingFormAndPersistsExactSavedImageBinding() throws Exception {
        Path original = httpExport();
        call("open_training_bundle", Map.of("input_path", original.toString(), "replace_current", true));
        TrainingReviewDialog[] form = {null};
        SwingUtilities.invokeLater(() -> { form[0] = new TrainingReviewDialog(frame, exporter); form[0].setVisible(true); });
        SwingUtilities.invokeAndWait(() -> findButton(form[0], "Start review on live network").doClick());
        TrainingRevisionCanvasWindow[] live = {null};
        SwingUtilities.invokeAndWait(() -> {
            for (Window window : Window.getWindows()) if (window instanceof TrainingRevisionCanvasWindow canvas && canvas.isShowing()) live[0] = canvas;
            assertNotNull(live[0]);
            assertTrue(live[0].isResizable());
            assertTrue(find(live[0], JToggleButton.class, "Add finding mode").isSelected());
            assertTrue((live[0].getExtendedState() & java.awt.Frame.MAXIMIZED_BOTH) != 0);
            assertNull(find(form[0], JComboBox.class, "Reviewer kind"));
            var tabs = find(form[0], JTabbedPane.class, null); assertEquals(-1, tabs.indexOfTab("Images"));
            var canvas = live[0].canvas();
            var place = canvas.getGraphNet().getGraphPetriPlaceList().getFirst();
            var position = place.getGraphElementCenter();
            Timer answer = new Timer(40, null);
            answer.addActionListener(event -> {
                for (Window window : Window.getWindows()) if (window.isShowing()) {
                    JTextArea text = find(window, JTextArea.class, "Finding comment");
                    JOptionPane pane = find(window, JOptionPane.class, null);
                    if (text != null && pane != null) { text.setText("Перевірити кількість початкових маркерів"); pane.setValue(JOptionPane.OK_OPTION); answer.stop(); }
                }
            });
            answer.start();
            int x = (int) (position.getX() * canvas.getScale()), y = (int) (position.getY() * canvas.getScale());
            canvas.dispatchEvent(new java.awt.event.MouseEvent(canvas, java.awt.event.MouseEvent.MOUSE_PRESSED, 0, java.awt.event.InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
            canvas.dispatchEvent(new java.awt.event.MouseEvent(canvas, java.awt.event.MouseEvent.MOUSE_RELEASED, 1, 0, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
            assertEquals(position, place.getGraphElementCenter());
            JTable table = find(form[0], JTable.class, null); assertEquals(1, table.getRowCount());
            assertEquals("Перевірити кількість початкових маркерів", table.getModel().getValueAt(0, 1));
            assertFalse(find(form[0], JComboBox.class, "Reviewed revision").isEnabled());
            installFormTimer(form[0]);
        });
        Path saved = awaitExport(original);
        var bundle = TrainingBundleWorkspace.read(saved);
        String reviewId = bundle.history().path("reviews").get(0).path("id").asText();
        var record = DesktopMcpServer.JSON.readTree(bundle.files().get("history/reviews/" + reviewId + ".json"));
        var finding = record.path("findings").get(0);
        assertEquals("main", finding.path("view_id").asText());
        assertFalse(finding.path("element_id").asText().isBlank());
        assertTrue(finding.path("region").path("width").asDouble() > 0);
        String revision = record.path("revision_id").asText();
        assertEquals(TrainingBundleWorkspace.hash(bundle.files().get("history/revisions/" + revision + "/network.png")), finding.path("region").path("image_sha256").asText());
    }

    @Test public void mcpSkipReasonIsRetainedInsteadOfInventingSimulationSuccess() throws Exception {
        httpExport();
        JsonNode result=call("export_training_review",Map.of("output_directory",folder.getRoot().toString(),"reviewer","Test agent","reviewer_kind","agent","decision","changes_requested","comment","Unspecified input needs clarification","skip_simulation_reason","Потрібно уточнити початкові умови"));
        Path path=Path.of(DesktopMcpServer.JSON.readTree(result.at("/content/0/text").asText()).path("path").asText());
        var bundle=TrainingBundleWorkspace.read(path);String id=bundle.history().path("reviews").get(0).path("id").asText();
        var review=DesktopMcpServer.JSON.readTree(bundle.files().get("history/reviews/"+id+".json"));
        assertEquals("Потрібно уточнити початкові умови",review.path("simulation").path("skip_reason").asText());assertTrue(review.path("simulation").path("automatic_runs").isEmpty());assertEquals("unknown",review.path("verification_status").asText());
    }
    @Test public void httpOnlyExportOpenReviewReopenPreservesImagesAndHistory() throws Exception {
        Path original = httpExport();
        call("open_training_bundle", Map.of("input_path", original.toString(), "replace_current", true));
        JsonNode image = call("training_bundle_image", Map.of("image_path", "network.png"));
        assertEquals("image", image.at("/content/0/type").asText());
        assertNotNull(TrainingBundleWorkspace.readImage(java.util.Base64.getDecoder().decode(image.at("/content/0/data").asText())));
        Path reviewed = httpReview("agent");
        call("open_training_bundle", Map.of("input_path", reviewed.toString(), "replace_current", true));
        SwingUtilities.invokeAndWait(() -> {
            var place = frame.getPetriNetsPanel().getGraphNet().getGraphPetriPlaceList().getFirst().getPetriPlace();
            place.setMark(place.getMark() + 2);
        });
        Path reviewedAgain = httpReview("agent");
        checkHistory(reviewedAgain, 2, "agent", "agent");
        call("open_training_bundle", Map.of("input_path", reviewedAgain.toString(), "replace_current", true));
        String first = exporter.workspace().active().history().path("revisions").get(0).path("id").asText();
        JsonNode difference = call("compare_training_revisions", Map.of("left_revision", first, "right_revision", "head", "include_layout", false));
        assertTrue(DesktopMcpServer.JSON.readTree(difference.at("/content/0/text").asText()).path("changes").toString().contains("marking"));
    }

    @Test public void guiOnlyExportsImportsAndAppendsReviewsThroughForms() throws Exception {
        Path original = form(false);
        openFromMenu(original);
        Path reviewed = waitForFormReview();
        openFromMenu(reviewed);
        Path reviewedAgain = waitForFormReview();
        checkHistory(reviewedAgain, 2, "human", "human");
    }

    @Test public void trainingCommandsLiveInAiAndReviewRatherThanFileMenu() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(menuItems("AI").contains("Export training data..."));
            assertTrue(menuItems("Review").containsAll(List.of("Open archive...", "Review network...", "Versions and comparison...")));
            assertFalse(menuItems("File").contains("Export training data..."));
            assertTrue(menuItems("File").containsAll(List.of("Open...", "Export image...")));
        });
        assertTrue(FileDialogs.PROJECT.extensions().contains("zip"));
    }

    @Test public void droppingArchiveOnWindowOpensItsCompleteReviewHistory() throws Exception {
        Path first = httpExport();
        Path reviewed = httpReview("agent");
        var expected = TrainingBundleWorkspace.read(reviewed).history();
        openArchive(reviewed, true);
        assertEquals(expected, exporter.workspace().active().history());
        assertEquals(reviewed.toAbsolutePath(), exporter.workspace().active().path());
        assertNotNull(exporter.workspace().active().files().get("network.png"));
        assertTrue(ua.stetsenkoinna.recentprojects.RecentProjectsStore.shared().all().stream()
                .anyMatch(entry -> Path.of(entry.getPath()).equals(reviewed.toAbsolutePath())));
    }

    @Test public void droppingArchiveOnWelcomeUsesTheNormalProjectOpenRoute() throws Exception {
        Path archive = httpExport();
        var previous = exporter.workspace().active();
        SwingUtilities.invokeAndWait(() -> {
            ua.stetsenkoinna.graphpresentation.welcome.WelcomeFrame.show(ua.stetsenkoinna.config.AppSettings.shared(),
                    ua.stetsenkoinna.recentprojects.RecentProjectsStore.shared(), file -> { frame.openProjectFile(file); return true; }, () -> false, () -> {}, false);
            var welcome = java.util.Arrays.stream(Window.getWindows())
                    .filter(window -> window instanceof ua.stetsenkoinna.graphpresentation.welcome.WelcomeFrame && window.isShowing())
                    .map(window -> (JFrame) window).findFirst().orElseThrow();
            var dropTarget = welcome.getContentPane().getDropTarget();
            var event = new ArchiveDrop(dropTarget.getDropTargetContext(), archive.toFile());
            dropTarget.drop(event);
            assertTrue(event.completed); assertFalse(welcome.isDisplayable());
            ua.stetsenkoinna.utils.MessageHelper.setDefaultParent(frame);
        });
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(12);
        boolean[] showing = {false};
        while (System.nanoTime() < deadline) {
            SwingUtilities.invokeAndWait(() -> showing[0] = java.util.Arrays.stream(Window.getWindows())
                    .anyMatch(window -> window instanceof TrainingReviewDialog && window.getOwner() == frame && window.isShowing()));
            if (exporter.workspace().active() != previous && showing[0]) break;
            Thread.sleep(30);
        }
        assertTrue(showing[0]);
        assertNotSame(previous, exporter.workspace().active());
        assertEquals(archive.toAbsolutePath(), exporter.workspace().active().path());
    }

    @Test public void ordinaryOpenRejectsAnUnrelatedZipWithoutClearingTheCurrentNetwork() throws Exception {
        httpExport();
        var previousBundle = exporter.workspace().active();
        var previousNetwork = frame.getPetriNetsPanel().getGraphNet();
        Path invalid = folder.getRoot().toPath().resolve("unrelated.zip");
        try (var zip = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(invalid))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("notes.txt")); zip.write(new byte[]{1}); zip.closeEntry();
        }
        openArchive(invalid, false, false);
        assertSame(previousBundle, exporter.workspace().active());
        assertSame(previousNetwork, frame.getPetriNetsPanel().getGraphNet());
    }

    private List<String> menuItems(String name) {
        for (int index = 0; index < frame.getJMenuBar().getMenuCount(); index++) {
            JMenu menu = frame.getJMenuBar().getMenu(index);
            if (menu != null && name.equals(menu.getText())) return java.util.Arrays.stream(menu.getMenuComponents())
                    .filter(component -> component instanceof JMenuItem).map(component -> ((JMenuItem) component).getText()).toList();
        }
        throw new AssertionError("Menu is missing: " + name);
    }

    @Test public void mixedGuiAndHttpReviewsUseTheSameRevisionLineage() throws Exception {
        Path original = form(false);
        call("open_training_bundle", Map.of("input_path", original.toString(), "replace_current", true));
        Path agent = httpReview("agent");
        openFromMenu(agent);
        Path human = waitForFormReview();
        call("open_training_bundle", Map.of("input_path", human.toString(), "replace_current", true));
        Path finalZip = httpReview("agent");
        checkHistory(finalZip, 3, "agent", "human", "agent");
    }

    @Test public void ukrainianDemoPreservesAResourceCorrectionAcrossFiveMixedIterations() throws Exception {
        ukrainianDemo = true;
        ua.stetsenkoinna.config.AppSettings.shared().setContributorProfile(new ua.stetsenkoinna.config.ContributorProfile(
                "Демо-учасник", "Демонстраційний навчальний приклад", "ДЕМО-01", ""));
        SwingUtilities.invokeAndWait(() -> {
            var canvas = ukrainianQueue();
            frame.getPetriNetsPanel().setCanvasModel(canvas);
            find(frame, JTextField.class, "Network name").setText("Черга заявок - два виконавці");
        });
        Path first = form(false);
        call("open_training_bundle", Map.of("input_path", first.toString(), "replace_current", true));
        call("training_bundle_image", Map.of("image_path", "networks/object-0002.png"));
        var originalBundle = exporter.workspace().active();
        var objectGeometry = DesktopMcpServer.JSON.readTree(originalBundle.files().get("networks/object-0002-geometry.json"));
        JsonNode resourceBounds = null;
        for (var element : objectGeometry.path("elements")) if (element.path("id").asText().equals("GraphPetriPlace:1004")) resourceBounds = element.path("bounds");
        assertNotNull(resourceBounds);
        Map<String, Object> region = Map.of("coordinate_system", "image-pixels", "image_sha256", TrainingBundleWorkspace.hash(originalBundle.files().get("networks/object-0002.png")),
                "x", resourceBounds.path("x").asDouble() - 6, "y", resourceBounds.path("y").asDouble() - 6, "width", resourceBounds.path("width").asDouble() + 12, "height", resourceBounds.path("height").asDouble() + 12);
        JsonNode response = call("export_training_review", Map.of("output_directory", folder.getRoot().toString(), "reviewer", "Агент перевірки MCP", "reviewer_kind", "agent",
                "decision", "changes_requested", "comment", "У завданні зазначено двох виконавців, але початкова кількість вільних ресурсів дорівнює одному. Потрібно встановити два маркери.",
                "findings", List.of(Map.of("severity", "error", "message", "У позиції «Вільні виконавці» має бути два початкові маркери замість одного.", "view_id", "object-0002", "element_id", "GraphPetriPlace:1004", "region", region))));
        Path second = Path.of(DesktopMcpServer.JSON.readTree(response.at("/content/0/text").asText()).path("path").asText());
        openFromMenu(second);
        SwingUtilities.invokeAndWait(() -> {
            for (Window window : Window.getWindows()) if (window instanceof TrainingReviewDialog) window.dispose();
            frame.getPetriNetsPanel().getGraphNet().getGraphPetriPlaceList().stream()
                    .filter(place -> place.getName().equals("Вільні виконавці")).findFirst().orElseThrow().getPetriPlace().setMark(2);
        });
        Path third = form(false);
        openFromMenu(third);
        Path fourth = waitForFormReview();
        call("open_training_bundle", Map.of("input_path", fourth.toString(), "replace_current", true));
        var bundle = exporter.workspace().active();
        String originalRevision = bundle.history().path("revisions").get(0).path("id").asText();
        JsonNode diff = call("compare_training_revisions", Map.of("left_revision", originalRevision, "right_revision", "head", "include_layout", false));
        assertTrue(DesktopMcpServer.JSON.readTree(diff.at("/content/0/text").asText()).path("changes").toString().contains("marking"));
        response = call("export_training_review", Map.of("output_directory", folder.getRoot().toString(), "reviewer", "Агент завершального перегляду", "reviewer_kind", "agent",
                "decision", "accepted", "comment", "Після повторного відкриття ZIP збережено попередні версії, зауваження та зображення вкладених мереж. Порівняння підтверджує зміну початкового ресурсу з одного до двох. Це статична демонстраційна перевірка; успішний запуск симуляції не засвідчується.",
                "conversation", List.of(Map.of("role", "assistant", "content", "Демонстраційний цикл завершено: експорт у застосунку → рев’ю через MCP → виправлення → рев’ю через форму → завершальне рев’ю MCP. Усі ці записи створив агент, а не незалежний викладач."))));
        Path fifth = Path.of(DesktopMcpServer.JSON.readTree(response.at("/content/0/text").asText()).path("path").asText());
        var finalBundle = TrainingBundleWorkspace.read(fifth);
        assertEquals(5, finalBundle.history().path("revisions").size());
        assertEquals(3, finalBundle.history().path("reviews").size());
        int reviewIndex = 0;
        for (JsonNode reference : finalBundle.history().path("reviews")) {
            JsonNode review = DesktopMcpServer.JSON.readTree(finalBundle.files().get("history/reviews/" + reference.path("id").asText() + ".json"));
            assertEquals(reviewIndex++ == 1 ? "human" : "agent", review.path("reviewer_kind").asText());
        }
        assertTrue(finalBundle.files().containsKey("networks/object-0002-geometry.json"));
        String retained = System.getProperty("training.qa.output");
        if (retained != null) {
            Path output = Path.of(retained); java.nio.file.Files.createDirectories(output);
            java.nio.file.Files.copy(fifth, output.resolve(fifth.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            java.nio.file.Files.writeString(output.resolve("ukrainian-demo-path.txt"), output.resolve(fifth.getFileName()).toString(), StandardCharsets.UTF_8);
            java.nio.file.Files.write(output.resolve("ukrainian-demo-overview.png"), finalBundle.files().get("network.png"));
        }
    }

    @Test public void mcpOpensTwoIndependentRevisionCanvasesWithoutChangingTheWorkingModel() throws Exception {
        Path original = httpExport();
        call("open_training_bundle", Map.of("input_path", original.toString(), "replace_current", true));
        Path reviewed = httpReview("agent");
        var source = frame.getPetriNetsPanel().getGraphNet();
        String first = exporter.workspace().active().history().path("revisions").get(0).path("id").asText();
        JsonNode reply = call("show_training_revision_canvases", Map.of("left_revision", first, "right_revision", "head"));
        assertTrue(DesktopMcpServer.JSON.readTree(reply.at("/content/0/text").asText()).path("read_only").asBoolean());
        SwingUtilities.invokeAndWait(() -> {
            var windows = java.util.Arrays.stream(Window.getWindows()).filter(window -> window instanceof TrainingRevisionCanvasWindow && window.isShowing()).toList();
            assertEquals(2, windows.size());
            for (var window : windows) assertNotSame(source, ((TrainingRevisionCanvasWindow) window).canvas().getGraphNet());
        });
        assertSame(source, frame.getPetriNetsPanel().getGraphNet());
        assertEquals(reviewed.toAbsolutePath(), exporter.workspace().active().path());
    }

    private GraphCanvasModel ukrainianQueue() {
        var canvas = new GraphCanvasModel();
        var source = place("Джерело", 1, 1001, 200, 280);
        var queue = place("Черга", 0, 1002, 620, 280);
        var free = place("Вільні виконавці", 1, 1004, 820, 210);
        var done = place("Виконано", 0, 1005, 1040, 360);
        var arrivals = transition("Надходження", 5, 1006, 360, 280);
        var service = transition("Обслуговування", 3, 1007, 820, 360);
        canvas.getNet().getGraphPetriPlaceList().addAll(List.of(source, queue, free, done));
        canvas.getNet().getGraphPetriTransitionList().addAll(List.of(arrivals, service));
        input(canvas, source, arrivals); output(canvas, arrivals, source); output(canvas, arrivals, queue);
        input(canvas, queue, service); input(canvas, free, service); output(canvas, service, free); output(canvas, service, done);
        var parent = new ua.stetsenkoinna.graphnet.GraphObjectFrame("Система заявок", new java.awt.Rectangle(60, 60, 1120, 460));
        var generator = new ua.stetsenkoinna.graphnet.GraphObjectFrame("Надходження заявок", new java.awt.Rectangle(100, 160, 360, 240));
        var workers = new ua.stetsenkoinna.graphnet.GraphObjectFrame("Черга й виконавці", new java.awt.Rectangle(510, 110, 620, 360));
        canvas.getFrames().addAll(List.of(parent, generator, workers)); canvas.nest(generator, parent); canvas.nest(workers, parent);
        canvas.claim(generator, source); canvas.claim(generator, arrivals);
        for (var element : List.of(queue, free, done, service)) canvas.claim(workers, element);
        parent.setCollapsed(true); workers.setCollapsed(true); generator.setContentVisible(false);
        return canvas;
    }
    private ua.stetsenkoinna.graphnet.GraphPetriPlace place(String name, int marking, int id, int x, int y) {
        var result = new ua.stetsenkoinna.graphnet.GraphPetriPlace(new ua.stetsenkoinna.petriobj.PetriP(name, marking), id);
        result.setNewCoordinates(new java.awt.Point(x, y)); return result;
    }
    private ua.stetsenkoinna.graphnet.GraphPetriTransition transition(String name, double delay, int id, int x, int y) {
        var result = new ua.stetsenkoinna.graphnet.GraphPetriTransition(new ua.stetsenkoinna.petriobj.PetriT(name, delay), id);
        result.setNewCoordinates(new java.awt.Point(x, y)); return result;
    }
    private void input(GraphCanvasModel canvas, ua.stetsenkoinna.graphnet.GraphPetriPlace place, ua.stetsenkoinna.graphnet.GraphPetriTransition transition) {
        var arc = new ua.stetsenkoinna.graphnet.GraphArcIn(); arc.setEndElement(transition); arc.settingNewArc(place); arc.setPetriElements(); arc.changeBorder(); arc.updateCoordinates(); canvas.getNet().getGraphArcInList().add(arc);
    }
    private void output(GraphCanvasModel canvas, ua.stetsenkoinna.graphnet.GraphPetriTransition transition, ua.stetsenkoinna.graphnet.GraphPetriPlace place) {
        var arc = new ua.stetsenkoinna.graphnet.GraphArcOut(); arc.settingNewArc(transition); arc.setEndElement(place); arc.setPetriElements(); arc.changeBorder(); arc.updateCoordinates(); canvas.getNet().getGraphArcOutList().add(arc);
    }

    private void checkHistory(Path path, int count, String... kinds) throws Exception {
        var bundle = TrainingBundleWorkspace.read(path);
        assertEquals(count, bundle.history().path("reviews").size());
        assertEquals(count + 1, bundle.history().path("revisions").size());
        assertNotNull(bundle.files().get("network.png")); assertNotNull(bundle.files().get("model-geometry.json"));
        for (int index = 0; index < kinds.length; index++) {
            String id = bundle.history().path("reviews").get(index).path("id").asText();
            JsonNode review = DesktopMcpServer.JSON.readTree(bundle.files().get("history/reviews/" + id + ".json"));
            assertEquals(kinds[index], review.path("reviewer_kind").asText()); assertEquals("unknown", review.path("verification_status").asText());
            var simulation = review.path("simulation"); assertFalse(simulation.path("automatic_runs").isEmpty());
            assertEquals(TrainingBundleWorkspace.hash(bundle.files().get("history/revisions/" + review.path("revision_id").asText() + "/model.pnml")), simulation.path("source_model_sha256").asText());
            assertEquals(bundle.history().path("revisions").get(index).path("id").asText(), review.path("revision_id").asText());
        }
        assertEquals("unknown", DesktopMcpServer.JSON.readTree(bundle.files().get("verification.json")).path("status").asText());
        assertTrue(path.getFileName().toString().contains("review00" + count));
        String retained = System.getProperty("training.qa.output");
        if (retained != null) {
            Path output = Path.of(retained); java.nio.file.Files.createDirectories(output);
            java.nio.file.Files.copy(path, output.resolve(String.join("-", kinds) + ".zip"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path form(boolean review) throws Exception {
        Path before = exporter.workspace().active() == null ? null : exporter.workspace().active().path();
        SwingUtilities.invokeLater(() -> {
            JDialog dialog = review ? new TrainingReviewDialog(frame, exporter) : new TrainingDataDialog(frame, exporter);
            installFormTimer(dialog);
            dialog.setVisible(true);
        });
        return awaitExport(before);
    }

    private Path waitForFormReview() throws Exception {
        Path before = exporter.workspace().active().path();
        // Opening through the File menu automatically opens the review form.
        SwingUtilities.invokeAndWait(() -> {
            for (Window window : Window.getWindows()) if (window instanceof TrainingReviewDialog dialog && dialog.isShowing()) installFormTimer(dialog);
        });
        return awaitExport(before);
    }

    private void openFromMenu(Path path) throws Exception {
        openArchive(path, false);
    }

    private void openArchive(Path path, boolean drop) throws Exception {
        openArchive(path, drop, true);
    }

    private void openArchive(Path path, boolean drop, boolean valid) throws Exception {
        var previous = exporter.workspace().active();
        java.util.concurrent.atomic.AtomicBoolean rejected = new java.util.concurrent.atomic.AtomicBoolean();
        var timer = new Timer(60, null);
        timer.addActionListener(event -> {
            for (Window window : Window.getWindows()) {
                if (window instanceof FileDialog chooser && chooser.isVisible() && chooser.getTitle().equals("Open")) FileDialogs.selectFile(chooser, path.toFile());
                if (window instanceof JDialog dialog && dialog.isShowing()) {
                    JOptionPane pane = find(dialog, JOptionPane.class, null);
                    if (pane != null && pane.getOptionType() == JOptionPane.YES_NO_OPTION) pane.setValue(JOptionPane.YES_OPTION);
                    else if (!valid && pane != null && pane.getMessageType() == JOptionPane.ERROR_MESSAGE) {
                        rejected.set(true); pane.setValue(JOptionPane.OK_OPTION);
                    }
                }
            }
        });
        SwingUtilities.invokeAndWait(timer::start);
        SwingUtilities.invokeLater(() -> {
            if (drop) {
                var event = new ArchiveDrop(frame.getContentPane().getDropTarget().getDropTargetContext(), path.toFile());
                frame.getContentPane().getDropTarget().drop(event);
                assertTrue(event.completed);
                return;
            }
            for (int menuIndex = 0; menuIndex < frame.getJMenuBar().getMenuCount(); menuIndex++) {
                JMenu menu = frame.getJMenuBar().getMenu(menuIndex);
                if (menu == null) continue;
                for (Component component : menu.getMenuComponents()) if (component instanceof JMenuItem item && "Open...".equals(item.getText())) { item.doClick(); return; }
            }
            throw new AssertionError("Training ZIP menu is missing");
        });
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(12);
        while (System.nanoTime() < deadline) {
            if (!valid && rejected.get()) { SwingUtilities.invokeAndWait(timer::stop); return; }
            if (exporter.workspace().active() != null && exporter.workspace().active() != previous && exporter.workspace().active().path().equals(path.toAbsolutePath())) {
                boolean[] showing = {false};
                SwingUtilities.invokeAndWait(() -> { for (Window window : Window.getWindows()) if (window instanceof TrainingReviewDialog && window.isShowing()) showing[0] = true; });
                if (showing[0]) { SwingUtilities.invokeAndWait(timer::stop); return; }
            }
            Thread.sleep(30);
        }
        SwingUtilities.invokeAndWait(timer::stop);
        fail("Training ZIP did not open through the menu");
    }

    private static final class ArchiveDrop extends java.awt.dnd.DropTargetDropEvent {
        private final java.io.File file;
        private boolean completed;
        ArchiveDrop(java.awt.dnd.DropTargetContext context, java.io.File file) {
            super(context, new java.awt.Point(20, 20), java.awt.dnd.DnDConstants.ACTION_COPY, java.awt.dnd.DnDConstants.ACTION_COPY);
            this.file = file;
        }
        @Override public void acceptDrop(int action) { assertEquals(java.awt.dnd.DnDConstants.ACTION_COPY, action); }
        @Override public void dropComplete(boolean success) { completed = success; }
        @Override public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) { return flavor.equals(java.awt.datatransfer.DataFlavor.javaFileListFlavor); }
        @Override public java.awt.datatransfer.Transferable getTransferable() {
            return new java.awt.datatransfer.Transferable() {
                @Override public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() { return new java.awt.datatransfer.DataFlavor[]{java.awt.datatransfer.DataFlavor.javaFileListFlavor}; }
                @Override public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) { return flavor.equals(java.awt.datatransfer.DataFlavor.javaFileListFlavor); }
                @Override public Object getTransferData(java.awt.datatransfer.DataFlavor flavor) throws java.awt.datatransfer.UnsupportedFlavorException {
                    if (!isDataFlavorSupported(flavor)) throw new java.awt.datatransfer.UnsupportedFlavorException(flavor);
                    return List.of(file);
                }
            };
        }
    }

    private void installFormTimer(JDialog dialog) {
        JTextField reviewer = find(dialog, JTextField.class, "Reviewer name");
        if (reviewer != null) reviewer.setText("GUI reviewer");
        JTextArea comment = find(dialog, JTextArea.class, "Review comment");
        if (comment != null) comment.setText("Inspect capacity and marking");
        JTextArea task = find(dialog, JTextArea.class, "Training task");
        if (task != null) task.setText("Inspect the queue");
        if (ukrainianDemo) {
            JTextField title = find(dialog, JTextField.class, "Training title");
            if (title != null) title.setText("Черга заявок - два виконавці");
            if (task != null) task.setText("Демонстраційне завдання: надходження заявок кожні 5 одиниць часу, черга та два паралельні виконавці. Обслуговування триває 3 одиниці часу. Після завершення обслуговування ресурс повертається. Початкова спроба навмисно містить один ресурс замість двох для демонстрації циклу виправлення. Приклад створений автоматизованим тестом: запис форми демонструє людський канал інтерфейсу, але не є справжнім людським рев’ю.");
            if (reviewer != null) reviewer.setText("Тестовий рецензент інтерфейсу");
            if (comment != null) comment.setText("Через форму застосунку перевірено виправлене початкове маркування: два вільні виконавці. Попередню помилку та зауваження MCP збережено в історії. Це синтетичний тестовий запис каналу інтерфейсу, а не справжнє людське схвалення; симуляцію не засвідчено.");
            var decision = find(dialog, JComboBox.class, "Review decision"); if (decision != null) decision.setSelectedItem("accepted");
        }
        boolean[] clicked = {false};
        Timer timer = new Timer(80, null);
        timer.addActionListener(event -> {
            for (Window window : Window.getWindows()) if (window.isShowing()) {
                JFileChooser chooser = find(window, JFileChooser.class, null);
                if (chooser != null) { chooser.setSelectedFile(folder.getRoot()); chooser.approveSelection(); }
                JOptionPane message = find(window, JOptionPane.class, null);
                if (message != null && message.getMessage() instanceof String text && text.startsWith("Saved")) message.setValue(JOptionPane.OK_OPTION);
            }
            if (!clicked[0] && dialog.isShowing()) {
                JButton button = findButton(dialog, dialog instanceof TrainingReviewDialog ? "Save reviewed ZIP..." : "Export ZIP...");
                clicked[0] = true; SwingUtilities.invokeLater(button::doClick);
            }
            if (clicked[0] && !dialog.isDisplayable()) timer.stop();
        });
        timer.start();
    }

    private Path awaitExport(Path previous) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            var bundle = exporter.workspace().active();
            if (bundle != null && !bundle.path().equals(previous)) return bundle.path();
            Thread.sleep(30);
        }
        fail("No ZIP was saved through the form"); return null;
    }

    private static JButton findButton(Container parent, String text) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JButton button && text.equals(button.getText())) return button;
            if (component instanceof Container nested) { JButton found = findButton(nested, text); if (found != null) return found; }
        }
        return null;
    }

    private static <T> T find(Container parent, Class<T> type, String name) {
        for (Component component : parent.getComponents()) {
            if (type.isInstance(component) && (name == null || component.getAccessibleContext() != null && name.equals(component.getAccessibleContext().getAccessibleName()))) return type.cast(component);
            if (component instanceof Container nested) { T found = find(nested, type, name); if (found != null) return found; }
        }
        return null;
    }
}
