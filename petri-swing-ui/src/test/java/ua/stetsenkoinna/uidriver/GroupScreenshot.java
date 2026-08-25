package ua.stetsenkoinna.uidriver;

import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphObjectGroup;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphnet.GraphPlaceFusion;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import javax.imageio.ImageIO;
import javax.swing.JViewport;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Builds a Petri-object group on a real canvas and paints it to a PNG, so the feature can be
 * looked at without clicking it together by hand.
 *
 * <p>A throwaway tool in the test tree, named without a {@code Test} suffix on purpose so
 * surefire never picks it up — the same arrangement {@link ScreenshotHarness} uses. It drives the
 * editor's own operations rather than drawing a mock-up: the group is stamped by the panel's
 * replication command and the links are made by its connector-to-a-group command, so what comes
 * out is what the user would see, not an artist's impression of it.
 *
 * <p>Run: {@code java -cp <classes;test-classes;deps> ua.stetsenkoinna.uidriver.GroupScreenshot
 * <output.png>}
 */
public final class GroupScreenshot {

    private static int idCounter = 1;

    private final PetriNetsPanel panel = new PetriNetsPanel(null, true);

    private GroupScreenshot() {
    }

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "group.png";
        GroupScreenshot shot = new GroupScreenshot();
        shot.build();
        shot.paintTo(new File(out));
        System.out.println("wrote " + new File(out).getAbsolutePath());
    }

    private void build() throws Exception {
        PetriP.initNext();
        PetriT.initNext();

        GraphObjectFrame server = object("Server", 60, 250);
        replicate(server, 4);
        GraphObjectGroup group = panel.getCanvasModel().getGroups().getFirst();

        GraphObjectFrame hub = object("Dispatcher", 60, 40);

        // Share the dispatcher's place with one member, then spread it across the group - the
        // two commands this whole feature is about.
        GraphPetriPlace hubPlace = panel.getCanvasModel().placesOf(hub).getFirst();
        GraphPetriPlace memberPlace =
                panel.getCanvasModel().placesOf(group.getMembers().getFirst()).getFirst();
        GraphPlaceFusion link = panel.getCanvasModel().joinPlaces(hubPlace, memberPlace);
        invoke("replicateLinkAcrossGroup",
                new Class<?>[]{GraphPlaceFusion.class, GraphObjectGroup.class}, link, group);

        panel.getCanvasModel().syncFusions();
        System.out.println("group '" + group.getName() + "' holds " + group.size() + " objects");
        System.out.println("links made: " + panel.getCanvasModel().getFusions().size());
        System.out.println("connectors: " + panel.getCanvasModel().connectors().size());
    }

    /** One object with a place, a transition and a second place, framed. */
    private GraphObjectFrame object(String name, int x, int y) {
        GraphObjectFrame frame = new GraphObjectFrame(name, new Rectangle(x, y, 250, 130));
        panel.getCanvasModel().getFrames().add(frame);
        panel.getCanvasModel().claim(frame, place(name + ".in", x + 45, y + 70));
        panel.getCanvasModel().claim(frame, transition(name + ".run", x + 125, y + 70));
        panel.getCanvasModel().claim(frame, place(name + ".out", x + 205, y + 70));
        return frame;
    }

    private GraphPetriPlace place(String name, int x, int y) {
        GraphPetriPlace place = new GraphPetriPlace(new PetriP(name, 0), idCounter++);
        place.setNewCoordinates(new Point2D.Double(x, y));
        panel.getGraphNet().getGraphPetriPlaceList().add(place);
        return place;
    }

    private GraphPetriTransition transition(String name, int x, int y) {
        GraphPetriTransition transition =
                new GraphPetriTransition(new PetriT(name, 1.0), idCounter++);
        transition.setNewCoordinates(new Point2D.Double(x, y));
        panel.getGraphNet().getGraphPetriTransitionList().add(transition);
        return transition;
    }

    private void replicate(GraphObjectFrame frame, int count) throws Exception {
        invoke("replicateObjectInto", new Class<?>[]{GraphObjectFrame.class, int.class},
                frame, count);
    }

    private void invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = PetriNetsPanel.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(panel, args);
    }

    /**
     * Paints the canvas into a PNG.
     *
     * <p>Through a viewport, because parts of the drawing ask the panel for one — the reference
     * links resolve their ends through it. Painting the bare panel would exercise a path the
     * running editor never takes.
     */
    private void paintTo(File file) throws Exception {
        Rectangle content = contentBounds();
        int width = content.x + content.width + 60;
        int height = content.y + content.height + 60;

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
        ImageIO.write(image, "png", file);
    }

    /** The rectangle every drawn frame fits inside. */
    private Rectangle contentBounds() {
        Rectangle bounds = null;
        List<GraphObjectFrame> frames = panel.getCanvasModel().getFrames();
        for (GraphObjectFrame frame : frames) {
            bounds = bounds == null
                    ? new Rectangle(frame.getBounds())
                    : bounds.union(frame.getBounds());
        }
        return bounds == null ? new Rectangle(0, 0, 800, 600) : bounds;
    }
}
