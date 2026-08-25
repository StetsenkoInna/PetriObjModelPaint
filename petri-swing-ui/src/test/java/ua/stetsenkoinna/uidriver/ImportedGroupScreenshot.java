package ua.stetsenkoinna.uidriver;

import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPlaceFusion;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import ua.stetsenkoinna.pnml.PnmlModelParser;

import javax.imageio.ImageIO;
import javax.swing.JViewport;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;

/**
 * Opens a PNML document exactly the way the editor does and paints the result to a PNG, so what
 * a user would see after File → Open can be checked without opening it by hand.
 *
 * <p>A throwaway tool in the test tree, named without a {@code Test} suffix so surefire skips
 * it — the arrangement {@link ScreenshotHarness} uses. It deliberately travels the whole path:
 * parser, then {@code fromObjModel}, then {@code setCanvasModel} on a real panel. That last step
 * is the one that used to drop a document's object groups, and a driver that skipped it would
 * have reported the feature working while the editor lost it.
 *
 * <p>Run: {@code java -cp <classes;test-classes;deps>
 * ua.stetsenkoinna.uidriver.ImportedGroupScreenshot <in.pnml> <out.png>}
 */
public final class ImportedGroupScreenshot {

    private ImportedGroupScreenshot() {
    }

    public static void main(String[] args) throws Exception {
        File source = new File(args[0]);
        File target = new File(args[1]);

        GraphPetriObjModel model = new PnmlModelParser().parse(source);
        GraphCanvasModel canvas = GraphCanvasModel.fromObjModel(model);

        PetriNetsPanel panel = new PetriNetsPanel(null, true);
        panel.setCanvasModel(canvas);

        System.out.println("objects:    " + panel.getCanvasModel().getFrames().size());
        System.out.println("groups:     " + panel.getCanvasModel().getGroups().size());
        panel.getCanvasModel().getGroups()
                .forEach(g -> System.out.println("  group '" + g.getName() + "' x" + g.size()));
        System.out.println("links:      " + panel.getCanvasModel().getFusions().size());
        System.out.println("connectors: " + panel.getCanvasModel().connectors().size());

        if (args.length > 2 && "select-group".equals(args[2])) {
            // The group picked out as a whole, so the band's selected colour is in the picture.
            panel.getCanvasModel().getGroups().forEach(group ->
                    group.getMembers().forEach(panel.getSelection()::add));
        } else if (!panel.getCanvasModel().getFusions().isEmpty()) {
            // Pick one strand, so the two-colour connector highlight is in the picture: the
            // strand itself in the accent, the rest of its connector held back.
            select(panel, panel.getCanvasModel().getFusions().getFirst());
        }

        paint(panel, target);
        System.out.println("wrote " + target.getAbsolutePath());
    }

    private static void select(PetriNetsPanel panel, GraphPlaceFusion fusion) throws Exception {
        Field field = PetriNetsPanel.class.getDeclaredField("choosenFusion");
        field.setAccessible(true);
        field.set(panel, fusion);
    }

    private static void paint(PetriNetsPanel panel, File target) throws Exception {
        Rectangle content = null;
        for (GraphObjectFrame frame : panel.getCanvasModel().getFrames()) {
            content = content == null
                    ? new Rectangle(frame.getBounds())
                    : content.union(frame.getBounds());
        }
        if (content == null) {
            content = new Rectangle(0, 0, 800, 600);
        }
        int width = content.x + content.width + 80;
        int height = content.y + content.height + 80;

        // Through a viewport: the reference links resolve their ends through one, so painting
        // the bare panel would take a path the running editor never does.
        JViewport viewport = new JViewport();
        viewport.setView(panel);
        viewport.setSize(width, height);
        viewport.doLayout();
        panel.setSize(Math.max(width, 20000), Math.max(height, 20000));
        panel.doLayout();

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        panel.paint(g2);
        g2.dispose();
        ImageIO.write(image, "png", target);
    }
}
