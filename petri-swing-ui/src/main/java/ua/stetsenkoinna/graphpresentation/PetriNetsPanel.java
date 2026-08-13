package ua.stetsenkoinna.graphpresentation;

import ua.stetsenkoinna.petriobj.ExceptionInvalidNetStructure;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Point2D;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

import ua.stetsenkoinna.config.ResourcePathConfig;
import ua.stetsenkoinna.graphnet.FramePort;
import ua.stetsenkoinna.graphnet.GraphArcFactory;
import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.graphnet.GraphElement;
import ua.stetsenkoinna.graphnet.GraphElementIdGenerator;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPlaceFusion;
import ua.stetsenkoinna.graphnet.NetTemplateRef;
import ua.stetsenkoinna.graphnet.PortAnchor;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphnet.GraphArc;
import ua.stetsenkoinna.graphnet.GraphArcIn;
import ua.stetsenkoinna.graphnet.GraphArcOut;
import ua.stetsenkoinna.graphpresentation.dragndrop.PnsDropHandler;
import ua.stetsenkoinna.graphpresentation.dragndrop.UnifiedDropHandler;
import ua.stetsenkoinna.graphpresentation.objmodel.CanvasStack;
import ua.stetsenkoinna.graphpresentation.objmodel.NetTemplateDialog;
import ua.stetsenkoinna.graphpresentation.objmodel.PetriObjectPalette;
import ua.stetsenkoinna.graphpresentation.objmodel.PetriObjectTemplate;
import ua.stetsenkoinna.graphnet.GraphNetBuilder;
import ua.stetsenkoinna.libnet.NetTemplateCatalog;
import ua.stetsenkoinna.pnml.PnmlParser;
import ua.stetsenkoinna.graphpresentation.undoable_edits.AddArcEdit;
import ua.stetsenkoinna.graphpresentation.undoable_edits.AddGraphElementEdit;
import ua.stetsenkoinna.graphpresentation.undoable_edits.AddObjectFrameEdit;
import ua.stetsenkoinna.graphpresentation.undoable_edits.DeleteArcEdit;
import ua.stetsenkoinna.graphpresentation.undoable_edits.DeleteGraphElementsEdit;
import ua.stetsenkoinna.graphpresentation.undoable_edits.ObjectFrameSnapshot;
import ua.stetsenkoinna.graphpresentation.undoable_edits.PasteElementsEdit;
import ua.stetsenkoinna.graphpresentation.undoable_edits.RemoveObjectFrameEdit;
import ua.stetsenkoinna.graphpresentation.dragndrop.PnmlDropHandler;
import ua.stetsenkoinna.utils.MessageHelper;

import java.awt.dnd.DropTarget;

/**
 * Creates new form PetriNetsPanel
 *
 * @author Ольга
 */
public class PetriNetsPanel extends javax.swing.JPanel {

    private static final Logger LOGGER = LoggerFactory.getLogger(PetriNetsPanel.class);

    /**
     * Creates new form PetriNetsPanel
     */
    private static int id; // нумерація графічних елементів
    private GraphPetriNet graphNet;  //added 4.12.2012
    private boolean isSettingArc;
    private GraphElement current;
    private GraphElement choosen;
    private GraphArc currentArc;
    private GraphArc choosenArc;
    private int savedId;
    public SetArc setArcFrame = new SetArc(this);
    public SetPosition setPositionFrame = new SetPosition(this);
    public SetTransition setTransitionFrame = new SetTransition(this);
    private JTextField nameTextField;
    private final String DEFAULT_NAME = "Untitled";
    private Point prevMouseLocation;
    private Point startDragMouseLocation = null;
    private Point currentDragMouseLocation = null;

    /**
     * Everything the canvas has selected, of either kind - see {@link CanvasSelection} for why
     * one store replaced the three the canvas used to keep.
     */
    private final CanvasSelection selection = new CanvasSelection();

    private double scale = 1.0;
    private boolean leftMouseButtonPressed = false;

    private List<GraphElement> copiedElements;

    /**
     * The Petri-objects Ctrl+C picked up, so Ctrl+V pastes objects rather than the loose
     * elements they happened to hold.
     */
    private List<GraphObjectFrame> copiedFrames = new ArrayList<>();

    /** False for a panel that only displays a net, e.g. while animating a whole model. */
    private final boolean editable;

    /**
     * The canvas seen as a Petri-object model: the drawing above, plus the frames that mark
     * out the objects in it and the places shared between them.
     */
    private final GraphCanvasModel canvasModel = new GraphCanvasModel();

    /** Frame the user is currently moving or resizing. */
    private GraphObjectFrame draggedFrame;
    private GraphObjectFrame resizedFrame;
    /** Offset between the pointer and the dragged frame's corner, so it does not jump. */
    private Point frameDragOffset;

    /**
     * The Petri-object whose own canvas is being edited, or {@code null} for the net's canvas.
     *
     * <p>This is a view, not a second document: there is still one {@link GraphCanvasModel}, one
     * net and one undo history. What it changes is what is painted, what is hit-tested, what a
     * selection may hold and which object a newly drawn element is claimed for. Everything else
     * in the application - File Save, PNML export, {@code toObjModel}, statistics, animation -
     * therefore keeps working on the whole model with no notion of focus at all, which is exactly
     * what stashing per-canvas documents would have taken away.
     */
    private GraphObjectFrame focusedFrame;

    /** Which canvases are open along the bottom of the window, and which is active. */
    private final CanvasStack canvasStack = new CanvasStack(canvasModel);

    /** Frames a running animation is currently highlighting — see {@link #clearAnimationHighlight()}. */
    private final Set<GraphObjectFrame> activeAnimationFrames =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Color ANIMATION_ACTIVE_COLOR = new Color(255, 77, 77);
    private static final Color ANIMATION_CROSSING_COLOR = new Color(60, 120, 220);

    /** The element being dragged, with where it started, so a move between objects can be undone. */
    private GraphElement draggedElement;
    private GraphObjectFrame ownerBeforeDrag;
    private Point2D positionBeforeDrag;

    /** The port a cross-object link is being dragged from, if any, and where the pointer is now. */
    private FramePort draggedFromPort;
    private Point draggedPortCurrentPoint;
    /** The port under the pointer, highlighted as the drag's likely target. */
    private FramePort hoveredPort;

    /** Which gesture a left-click-drag currently performs; see {@link CanvasTool}. */
    private CanvasTool tool = CanvasTool.SELECT;

    /** The Petri-object stamped by each click while {@link CanvasTool#ADD_PETRI_OBJECT} is
     *  active; {@code null} under every other tool. */
    private PetriObjectTemplate armedTemplate;

    /** A loaded net waiting to be placed, and where the pointer currently says it goes; both
     *  set only while {@link CanvasTool#PLACE_LOADED_NET} is active. */
    private GraphPetriNet pendingNet;
    private Point placementPoint;

    /** The scroll pane viewport being panned, and where the drag started, while tool == PAN
     *  — or, during a double-click-to-pan gesture on the Select tool, while
     *  {@link #selectToolPanning} is true instead. */
    private JViewport panViewport;
    private Point panDragOrigin;
    private Point panViewportOrigin;
    /** True mid-gesture after a double-click on empty canvas with the Select tool active,
     *  panning the view without actually switching tools. */
    private boolean selectToolPanning;

    /**
     * Set the moment a drag is seen, cleared on the next press and by the click that follows the
     * drag. It stops {@code mouseClicked} from reading the tail of a drag gesture as a click on
     * nothing and clearing the selection the drag just made - a selection that outlives its own
     * release only because AWT happens to suppress {@code MOUSE_CLICKED} after a drag, which is a
     * platform behaviour rather than a promise.
     */
    private boolean dragCompleted;

    /** Set while a multi-selection is being dragged, so its release can reparent what moved. */
    private boolean selectionDragged;

    private static final Cursor ERASER_CURSOR = buildEraserCursor();

    /**
     * @return the selected places and transitions, live and mutable - the undoable edits have
     *         mutated the canvas's selection through this accessor since long before
     *         Petri-objects existed, so it still hands out the very same list instance
     *         {@link CanvasSelection} holds
     */
    public List<GraphElement> getChoosenElements() {
        return selection.elements();
    }

    /**
     * @return everything the canvas has selected, of either kind
     */
    public CanvasSelection getSelection() {
        return selection;
    }

    /**
     * @return which canvases are open along the bottom of the window
     */
    public CanvasStack getCanvasStack() {
        return canvasStack;
    }

    public PetriNetsPanel(JTextField textField) {
        this(textField, true);
    }

    /**
     * @param textField field showing the name of the net on display, may be null
     * @param editable false to build a view-only panel: the net can be zoomed and animated
     *        but not drawn on, which is what the per-object views of a Petri-object model
     *        animation need
     */
    public PetriNetsPanel(JTextField textField, boolean editable) {
        this.editable = editable;
        initComponents();
        this.setBackground(Color.WHITE);

        // These are plain JFrames with no owner window, so Swing has no way to tell they
        // belong with this panel. Left as APPLICATION_MODAL_EXCLUDE by default, opening one
        // while an application-modal dialog is showing elsewhere — the per-object editor,
        // most notably — gets it blocked the instant it appears: it flashes and vanishes,
        // since a blocked window cannot take focus or paint. Exempting them from that
        // blocking is what lets a place or transition's own properties still be edited from
        // inside the object editor.
        setArcFrame.setModalExclusionType(java.awt.Dialog.ModalExclusionType.APPLICATION_EXCLUDE);
        setPositionFrame.setModalExclusionType(java.awt.Dialog.ModalExclusionType.APPLICATION_EXCLUDE);
        setTransitionFrame.setModalExclusionType(java.awt.Dialog.ModalExclusionType.APPLICATION_EXCLUDE);

        nameTextField = textField;
        this.resetOwnState(); // починаємо заново створювати усі списки графічних елементів  //додано 3.12.2012
        setFocusable(editable);

        addMouseWheelListener(new MouseWheelHendler());
        if (!editable) {
            return;
        }

        addMouseListener(new MouseHandler());
        addMouseMotionListener(new MouseMotionHandler());

        this.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE && isPlacingNet()) {
                    // The way out of placement mode without dropping the net somewhere the
                    // user then has to undo.
                    setTool(CanvasTool.SELECT);
                    return;
                }
                if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    deleteSelection();
                }

                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_A) {

                    selectAll();
                    repaint();
                }

                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_C) {
                    copySelection();
                }

                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_V) {
                    pasteAction();
                }

                // Duplicating a Petri-object is a distinct gesture from copy/paste of plain
                // elements, since it also carries the object's name, priority and template —
                // Ctrl+D matches what the frame's own context menu offers.
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_D) {
                    duplicateSelection();
                }
            }
        });

    }

    /**
     * A handler for ctrl+V. Clones elements and arcs associated with them and
     * pastes them onto the canvas.
     *
     * <p>A Petri-object that was copied comes back as a Petri-object: a frame of its own, at the
     * paste offset, claiming the copies of what the original held. Before, copying an object
     * pasted its net as loose elements, because the clipboard only ever held elements and nothing
     * recreated the frame.
     */
    public void pasteAction() {
        pasteClipboard();
    }

    /**
     * One of the canvas's selection operations: pastes whatever Ctrl+C picked up, of either kind.
     *
     * @see CanvasSelection for why the operations are written once each rather than per store
     */
    public void pasteClipboard() {
        // Everything to clone in one pass, so a copied object's net and the loose elements copied
        // alongside it come out of a single bulk copy and any arc between them survives.
        List<GraphElement> toClone = new ArrayList<>();
        if (copiedElements != null) {
            toClone.addAll(copiedElements);
        }
        for (GraphObjectFrame frame : copiedFrames) {
            for (GraphElement member : canvasModel.membersOfSubtree(frame)) {
                if (!toClone.contains(member)) {
                    toClone.add(member);
                }
            }
        }
        if (toClone.isEmpty()) {
            return;
        }

        GraphPetriNet.GraphNetFragment clonedFragment = graphNet.bulkCopyNoPasteElements(toClone);
        addNetFragment(clonedFragment);

        for (GraphObjectFrame original : copiedFrames) {
            recreateCopiedObject(original, clonedFragment.oldToNew);
        }

        copiedElements = new ArrayList<>(clonedFragment.elements);
        // The pasted objects, not the originals, are what another Ctrl+V should paste next -
        // otherwise every paste after the first stacks another copy on top of the first one.
        List<GraphObjectFrame> pasted = new ArrayList<>();
        for (GraphObjectFrame frame : canvasModel.getFrames()) {
            if (recentlyPasted.contains(frame)) {
                pasted.add(frame);
            }
        }
        copiedFrames = pasted;
        recentlyPasted.clear();

        PetriNetsFrame.getUndoSupport().postEdit(
                new PasteElementsEdit(this, clonedFragment)
        );
    }

    /** Frames created by the paste currently in progress; see {@link #pasteClipboard}. */
    private final List<GraphObjectFrame> recentlyPasted = new ArrayList<>();

    /**
     * Rebuilds one copied Petri-object around the copies of its own elements: same name with a
     * "copy" suffix, same priority, template and collapsed state, nested wherever the original
     * was, and fitted to what it actually holds.
     *
     * @param original the frame that was copied
     * @param oldToNew every copied element mapped to its copy
     */
    private void recreateCopiedObject(GraphObjectFrame original,
            Map<GraphElement, GraphElement> oldToNew) {
        List<GraphElement> members = new ArrayList<>();
        for (GraphElement member : canvasModel.membersOfSubtree(original)) {
            GraphElement copy = oldToNew.get(member);
            if (copy != null) {
                members.add(copy);
            }
        }
        if (members.isEmpty()) {
            return;
        }
        GraphObjectFrame duplicate = new GraphObjectFrame(
                original.getName() + " copy", boundsAround(members));
        duplicate.setPriority(original.getPriority());
        duplicate.setTemplate(original.getTemplate());
        duplicate.setContentVisible(original.isContentVisible());
        canvasModel.nest(duplicate, canvasModel.enclosingOf(original));
        addObjectFrame(duplicate);
        for (GraphElement member : members) {
            canvasModel.claim(duplicate, member);
            member.setColor(Color.BLACK);
            selection.remove(member);
        }
        if (original.isCollapsed()) {
            duplicate.setCollapsed(true);
        }
        recentlyPasted.add(duplicate);
    }

    /**
     * Adds a fragment of a net onto the canvas. Fragments' coordinates are
     * updated in the process.
     *
     * @param fragment fragment to add
     */
    public void addNetFragment(GraphPetriNet.GraphNetFragment fragment) {
        List<GraphElement> elementsToSpawn = fragment.elements;

        // de-selecting any selected elements
        for (GraphElement prevElement : selection.elements()) {
            prevElement.setColor(Color.BLACK);
        }
        selection.clear();

        for (GraphElement element : elementsToSpawn) {
            Point2D spawnPoint = element.getGraphElementCenter();
            spawnPoint.setLocation(spawnPoint.getX() + 15, spawnPoint.getY() + 15);

            element.setNewCoordinates(spawnPoint);

            if (element instanceof GraphPetriPlace) {
                this.getGraphNet().getGraphPetriPlaceList().add((GraphPetriPlace) element);
            } else {
                this.getGraphNet().getGraphPetriTransitionList().add((GraphPetriTransition) element);
            }

            selection.add(element);
        }

        for (GraphArcIn arcIn : fragment.inArcs) {
            getGraphNet().getGraphArcInList().add(arcIn);
        }

        for (GraphArcOut arcOut : fragment.outArcs) {
            getGraphNet().getGraphArcOutList().add(arcOut);
        }

        // wtf is this
        for (GraphArcOut arcOut : fragment.outArcs) {
            for (GraphArcIn arcIn : fragment.inArcs) {
                int inBeginId = ((GraphPetriPlace) arcIn.getBeginElement()).getId();
                int inEndId = ((GraphPetriTransition) arcIn.getEndElement()).getId();
                int outBeginId = ((GraphPetriTransition) arcOut.getBeginElement()).getId();
                int outEndId = ((GraphPetriPlace) arcOut.getEndElement()).getId();
                if (inBeginId == outEndId && inEndId == outBeginId) {
                    arcIn.twoArcs(arcOut); // two arcs
                }
                arcIn.updateCoordinates();
                arcOut.updateCoordinates();
            }
        }

        repaint();
    }

    public void removeArc(GraphArc s) {
        if (s == null) {
            return;
        }
        if (s == currentArc) {
            currentArc = null;
        }

        if (s.getClass().equals(GraphArcOut.class)) {
            graphNet.getGraphArcOutList().remove((GraphArcOut) s); //added by Inna 4.12.2012

        } else {
            graphNet.getGraphArcInList().remove((GraphArcIn) s); //added by Inna 4.12.2012
        }

        repaint();
    }

    @Override
    public void paintComponent(Graphics g) {

        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.scale(scale, scale);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        this.requestFocusInWindow(); //added 1.06.2013
        //додано 3.12.2012
        if (graphNet == null) {
            setCanvasNet(new GraphPetriNet());
        }

        // Expanded frames go under the drawing, collapsed ones over it: covering the net is
        // exactly what collapsing an object means on a shared canvas.
        paintObjectFrames(g2, false);
        graphNet.paintGraphPetriNet(g2, g, hiddenElements());
        for (GraphPlaceFusion fusion : canvasModel.getFusions()) {
            fusion.draw(g2, false);
        }
        paintObjectFrames(g2, true);
        paintPorts(g2);
        for (GraphPlaceFusion fusion : canvasModel.getFusions()) {
            if (fusion.isAnchoredToAFrame()) {
                // Asks the canvas who owns each half rather than the fusion's own remembered
                // owners: those were recorded when the fusion was made and a removed frame went
                // on answering with itself forever, so a shared place could resolve to an object
                // that is no longer on the canvas at all.
                Point masterPoint = connectionEndpoint(
                        canvasModel.ownerOf(fusion.getMaster()), fusion.getMaster());
                Point joinedPoint = connectionEndpoint(
                        canvasModel.ownerOf(fusion.getJoined()), fusion.getJoined());
                if (masterPoint != null && joinedPoint != null) {
                    fusion.drawBetweenPorts(g2, masterPoint, joinedPoint, false);
                }
            }
        }
        paintCrossingArcSubstitutes(g2);
        if (draggedFromPort != null && draggedPortCurrentPoint != null) {
            Color previous = g2.getColor();
            g2.setColor(Color.GRAY);
            // The same anchor a finished connection would use: the real element while it is on
            // screen, its port only while its object is hidden — not always the port's own
            // position, which would make the preview appear to start from the frame's border
            // even when dragging from the element's own, now directly clickable, body.
            GraphElement sourceElement = draggedFromPort.getElement();
            Point from = connectionEndpoint(canvasModel.ownerOf(sourceElement), sourceElement);
            if (from != null) {
                g2.drawLine(from.x, from.y, draggedPortCurrentPoint.x, draggedPortCurrentPoint.y);
            }
            g2.setColor(previous);
        }

        if (currentArc != null) {
            currentArc.drawGraphElement(g2);
        }
        if (choosenArc != null) {
            choosenArc.drawGraphElement(g2);
        }
        if (current != null) {
            current.drawGraphElement(g2);
        }
        if (choosen != null) {
            choosen.drawGraphElement(g2);
        }
        for (GraphElement graphElement : selection.elements()) {

            graphElement.drawGraphElement(g2);
        }

        //  printPointLocation(currentDragMouseLocation,"current");
        //  printPointLocation(startDragMouseLocation,"start");
        //   printArraySize(choosenElements, "");
        if (currentDragMouseLocation != null && startDragMouseLocation != null && leftMouseButtonPressed) {
            g2.setStroke((Stroke) new BasicStroke(1.0f,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_BEVEL,
                    20.0f,
                    new float[]{15.0f, 15.0f}, 0.0f));
            g2.drawRect(startDragMouseLocation.x,
                    startDragMouseLocation.y,
                    currentDragMouseLocation.x - startDragMouseLocation.x,
                    currentDragMouseLocation.y - startDragMouseLocation.y);
        }

        paintPendingNetOutline(g2);
    }

    /**
     * Shows where a net waiting to be placed would land — the same dashed rubber band the
     * marquee uses, so an in-progress gesture looks like an in-progress gesture. Drawn as an
     * outline of the net's extent rather than the net itself: at the moment of choosing a spot
     * what matters is how much room it needs, and a full ghost redrawn every mouse move over a
     * large net would be needlessly heavy.
     */
    private void paintPendingNetOutline(Graphics2D g2) {
        if (pendingNet == null || placementPoint == null) {
            return;
        }
        List<GraphElement> members = new ArrayList<>();
        members.addAll(pendingNet.getGraphPetriPlaceList());
        members.addAll(pendingNet.getGraphPetriTransitionList());
        if (members.isEmpty()) {
            return;
        }

        Rectangle extent = boundsAround(members);
        Point centre = pendingNet.getCurrentLocation();
        // changeLocation moves the net's centroid to the click point, so the outline has to be
        // offset by the same centroid-to-bounds relationship for the preview to be truthful.
        int x = extent.x + placementPoint.x - centre.x;
        int y = extent.y + placementPoint.y - centre.y;

        Stroke previousStroke = g2.getStroke();
        Color previousColor = g2.getColor();
        g2.setColor(new Color(0x33, 0x5A, 0x8A));
        g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL,
                20.0f, new float[]{15.0f, 15.0f}, 0.0f));
        g2.drawRect(x, y, extent.width, extent.height);
        g2.setStroke(previousStroke);
        g2.setColor(previousColor);
    }

    /**
     * Draws the Petri-object frames that belong on the active canvas: on the net's own canvas,
     * every top-level object and, inside each one, whatever it has nested that nothing above
     * hides; on an object's canvas, whatever is nested inside that object.
     *
     * <p>The object being edited is not drawn at all. On its own canvas it is not a box the user
     * is inside, it is simply the net they are editing, and a border around the whole drawing
     * would only be a wall around everything on screen. Which canvas this is comes from the
     * canvas strip, where the object's pill is the active one, so the frame has nothing left to
     * say. Its ports are still drawn, by paintPorts, because those stand for real connections to
     * other objects rather than for the boundary.
     *
     * <p>Frames are drawn parent-first, so a nested object is painted over the parent it sits
     * inside rather than underneath it.
     *
     * @param collapsedOnes true to draw the collapsed frames, which hide their net and are
     *        therefore painted over it, false for the expanded ones painted under it
     */
    private void paintObjectFrames(Graphics2D g2, boolean collapsedOnes) {
        List<GraphObjectFrame> flat = canvasModel.getFrames();
        for (GraphObjectFrame frame : canvasModel.framesParentFirst()) {
            if (frame == focusedFrame || !isFrameDrawnOnThisCanvas(frame)) {
                continue;
            }
            if (frame.isCollapsed() != collapsedOnes) {
                continue;
            }
            if (collapsedOnes) {
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(frame.getBounds().x, frame.getBounds().y,
                        frame.getBounds().width, frame.getBounds().height, 14, 14);
            }
            frame.draw(g2, flat.indexOf(frame), selection.contains(frame), countElementsIn(frame));
        }
    }

    /**
     * Draws the ports of every frame the active canvas shows only as a boundary: the collapsed and
     * the eye-hidden objects on it. While an object's own net is on screen there is nothing a port
     * needs to stand in for, so drawing its circle over the real element right next to it would
     * only be clutter.
     *
     * <p>The focused object has none drawn either. On its own canvas it is a plain net, and a
     * circle on a border that is not painted would be the last trace of a box the user was told
     * they are not in. A link to another object is made from the canvas that shows both, which is
     * the level above.
     */
    private void paintPorts(Graphics2D g2) {
        for (GraphObjectFrame frame : canvasModel.getFrames()) {
            if (frame == focusedFrame || !isFrameDrawnOnThisCanvas(frame) || !isContentHidden(frame)) {
                continue;
            }
            for (FramePort port : canvasModel.portsOf(frame)) {
                port.draw(g2, port == draggedFromPort || port == hoveredPort);
            }
        }
    }

    /**
     * @param frame a Petri-object frame, or {@code null} for the free elements
     * @return the outermost object at or above {@code frame} whose content the active canvas does
     *         not paint, or {@code null} when nothing hides it. Walking the whole chain rather
     *         than checking one frame is what makes a collapsed object hide the objects nested
     *         inside it too, and what lets a connection out of a deeply nested object resolve to
     *         a port on the outermost thing the user can actually see.
     */
    private GraphObjectFrame outermostHidden(GraphObjectFrame frame) {
        GraphObjectFrame hidden = null;
        int guard = 0;
        for (GraphObjectFrame above = frame;
                above != null && above != focusedFrame && guard <= canvasModel.getFrames().size();
                above = canvasModel.enclosingOf(above), guard++) {
            if (!above.isContentShown()) {
                hidden = above;
            }
        }
        return hidden;
    }

    /**
     * @param frame the owner of an element whose content might currently be hidden
     * @return true if that frame's net is not painted right now, whether because it is itself
     *         collapsed or eye-hidden or because something enclosing it is
     */
    private boolean isContentHidden(GraphObjectFrame frame) {
        return outermostHidden(frame) != null;
    }

    /**
     * @param frame a Petri-object frame
     * @return true if the active canvas draws this frame at all: it has to sit inside the focused
     *         object (or at the top level when the net's own canvas is active) with nothing
     *         between it and that focus collapsed or eye-hidden. The one predicate that decides
     *         what a canvas shows of the nesting hierarchy.
     */
    private boolean isFrameDrawnOnThisCanvas(GraphObjectFrame frame) {
        if (frame == null || frame == focusedFrame) {
            // Neither is a box this canvas paints. The net has no frame of its own, and the
            // object being edited is drawn as the net it holds rather than as a room around it,
            // which is also why nothing of its chrome, its header or its eye icon, is clickable.
            return false;
        }
        int guard = 0;
        for (GraphObjectFrame above = canvasModel.enclosingOf(frame);
                above != focusedFrame;
                above = canvasModel.enclosingOf(above)) {
            if (above == null || !above.isContentShown() || guard++ > canvasModel.getFrames().size()) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param element a place or transition of the drawing
     * @return true if the active canvas paints it: it belongs to the focused object, or to
     *         something nested inside it that nothing above has hidden. Everything else - another
     *         object's net, a sibling object's, the free elements while an object is being edited
     *         - is simply not on this canvas.
     */
    private boolean isDrawnOnThisCanvas(GraphElement element) {
        GraphObjectFrame owner = canvasModel.ownerOf(element);
        if (owner == focusedFrame) {
            return true;
        }
        return isFrameDrawnOnThisCanvas(owner) && owner.isContentShown();
    }

    /**
     * @param element a place or transition of the drawing
     * @return true if the active canvas is the one this element is edited on - it is claimed by
     *         exactly the focused object. On the net's canvas that is the free elements, which is
     *         the rule that has always locked a framed element; on an object's canvas it is that
     *         object's own members, which is what makes them directly draggable, deletable and
     *         arc-able there.
     */
    private boolean isOnThisCanvas(GraphElement element) {
        return canvasModel.ownerOf(element) == focusedFrame;
    }

    /**
     * @return every place and transition {@code paintGraphPetriNet} must leave out: those on
     *         another canvas entirely, and those whose own object, or an object enclosing it, is
     *         collapsed or eye-hidden
     */
    private java.util.Set<GraphElement> hiddenElements() {
        java.util.Set<GraphElement> hidden = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (GraphPetriPlace place : graphNet.getGraphPetriPlaceList()) {
            if (!isDrawnOnThisCanvas(place)) {
                hidden.add(place);
            }
        }
        for (GraphPetriTransition transition : graphNet.getGraphPetriTransitionList()) {
            if (!isDrawnOnThisCanvas(transition)) {
                hidden.add(transition);
            }
        }
        return hidden;
    }

    /**
     * Where one half of a connection — a shared place, or a crossing arc's end — is anchored:
     * the real place or transition itself while it is on screen (whether that is because it is
     * free, or because its object is fully shown), or a {@link PortAnchor} standing in for its
     * port while its owning frame's content is hidden. Either way the result trims a line the
     * same way {@link GraphArc#changeBorder()} always has — that trimming is the entire reason
     * this returns something {@code changeBorder()} can consume, rather than a bare point.
     *
     * @param frame the half's owning frame, or {@code null} if it is free
     * @param element the place or transition this is the anchor for
     * @return what to give {@code GraphArc.setBeginElement}/{@code setEndElement}, or
     *         {@code null} if a hidden half's port cannot currently be found
     */
    private GraphElement connectionAnchor(GraphObjectFrame frame, GraphElement element) {
        GraphObjectFrame hiding = outermostHidden(frame);
        if (hiding == null) {
            return element;
        }
        // The OUTERMOST hidden object, not the element's own: a place inside an object nested in a
        // collapsed object is drawn nowhere, and the only border on screen for a link to reach is
        // the collapsed ancestor's. Its ports cover its whole subtree, so the element has one.
        return portAnchorFor(hiding, element);
    }

    /**
     * @param frame the object whose border the connection should reach
     * @param element the place or transition the port stands in for
     * @return an anchor on that frame's border, or {@code null} if it has no port for the element
     */
    private GraphElement portAnchorFor(GraphObjectFrame frame, GraphElement element) {
        for (FramePort port : canvasModel.portsOf(frame)) {
            if (port.getElement() == element) {
                return new PortAnchor(port.getPosition(), FramePort.RADIUS);
            }
        }
        return null;
    }

    /**
     * The point-only view of {@link #connectionAnchor}, for callers — the fusion line, the
     * drag-in-progress preview — that only need where to draw to or from, not a full
     * {@code GraphArc}-compatible endpoint.
     *
     * @return the point to draw a connection's line to or from, or {@code null} if it cannot
     *         currently be found
     */
    private Point connectionEndpoint(GraphObjectFrame frame, GraphElement element) {
        GraphElement anchor = connectionAnchor(frame, element);
        if (anchor == null) {
            return null;
        }
        Point2D centre = anchor.getGraphElementCenter();
        return centre == null ? null : new Point((int) centre.getX(), (int) centre.getY());
    }

    /**
     * Substitutes a line for every crossing arc that a hidden object's own drawing no longer
     * shows — {@link #hiddenElements()} already kept {@code graphNet.paintGraphPetriNet} from
     * drawing the arc itself, along with everything else belonging to that object, so only arcs
     * whose two ends belong to different objects need a substitute; an arc entirely inside one
     * hidden object is meant to simply vanish with the rest of it. The substitute is drawn by
     * an ordinary, throwaway {@code GraphArcIn}/{@code GraphArcOut} — the same "temporary, for
     * drawing only" arc their own no-argument constructors already exist for — so it gets the
     * exact same border-trimmed line, arrowhead and quantity label a real one would, anchored
     * to {@link #connectionAnchor} instead of two elements guaranteed to both be on screen.
     */
    private void paintCrossingArcSubstitutes(Graphics2D g2) {
        for (GraphArcIn arc : graphNet.getGraphArcInList()) {
            paintCrossingArcSubstitute(g2, arc, new GraphArcIn());
        }
        for (GraphArcOut arc : graphNet.getGraphArcOutList()) {
            paintCrossingArcSubstitute(g2, arc, new GraphArcOut());
        }
    }

    private void paintCrossingArcSubstitute(Graphics2D g2, GraphArc arc, GraphArc temp) {
        GraphElement begin = arc.getBeginElement();
        GraphElement end = arc.getEndElement();
        GraphObjectFrame beginOwner = canvasModel.ownerOf(begin);
        GraphObjectFrame endOwner = canvasModel.ownerOf(end);
        if (beginOwner == endOwner) {
            return; // internal to one object, or both free — paintGraphPetriNet already drew it
        }
        if (isDrawnOnThisCanvas(begin) && isDrawnOnThisCanvas(end)) {
            return; // both ends are on screen already, drawn directly by paintGraphPetriNet
        }
        GraphElement beginAnchor = crossingAnchor(begin, end);
        GraphElement endAnchor = crossingAnchor(end, begin);
        if (beginAnchor == null || endAnchor == null) {
            return;
        }
        // settingNewArc is also what initializes the arc's own Line2D — setBeginElement alone
        // leaves it null, which changeBorder() (called below) unconditionally writes through.
        temp.settingNewArc(beginAnchor);
        temp.setEndElement(endAnchor);
        temp.setQuantity(arc.getQuantity());
        temp.setInf(arc.getIsInf());
        temp.setColor(arc.getColor());
        temp.changeBorder();
        temp.drawGraphElement(g2);
    }

    /**
     * Where one end of a crossing connection is drawn on the active canvas.
     *
     * @param element the end being anchored
     * @param otherEnd the connection's other end, needed only for the last case below
     * @return the element itself while it is on screen; a port on the outermost collapsed or
     *         eye-hidden object above it while that object is on screen; and, for an end that is
     *         not on this canvas at all, a port on the focused object's own border - the boundary
     *         the connection leaves through, so from inside an object a link out reads as "out
     *         through here" rather than as a line to nowhere. {@code null} when none of those can
     *         be resolved, which is when nothing should be drawn.
     */
    private GraphElement crossingAnchor(GraphElement element, GraphElement otherEnd) {
        GraphObjectFrame hiding = outermostHidden(canvasModel.ownerOf(element));
        if (hiding != null && isFrameDrawnOnThisCanvas(hiding)) {
            return portAnchorFor(hiding, element);
        }
        if (isDrawnOnThisCanvas(element)) {
            return element;
        }
        if (focusedFrame != null) {
            return portAnchorFor(focusedFrame, otherEnd);
        }
        return null;
    }

    private void printPointLocation(Point point, String s) {
        if (point != null) {
            LOGGER.debug("{}  {}", s, point.getX());
        } else {
            LOGGER.debug("NULL");
        }
    }

    private void printArraySize(List<GraphElement> list, String s) {
        if (list != null) {
            LOGGER.debug("{}  {}", s, list.size());
        } else {
            LOGGER.debug("NULL");
        }
    }

    /**
     * @param p a point on the canvas
     * @return the place or transition drawn there, or {@code null}. Only ever something the
     *         active canvas actually paints: an element belonging to another object, or to the
     *         net while an object is being edited, is not merely invisible but unreachable, which
     *         is what keeps a click on empty canvas from picking up something off screen.
     */
    public GraphElement find(Point2D p) {
        for (GraphPetriPlace pp : graphNet.getGraphPetriPlaceList()) {
            if (pp.isGraphElement(p) && isDrawnOnThisCanvas(pp)) {
                return pp;
            }
        }
        for (GraphPetriTransition pt : graphNet.getGraphPetriTransitionList()) {
            if (pt.isGraphElement(p) && isDrawnOnThisCanvas(pt)) {
                return pt;
            }
        }
        return null;
    }

    public GraphArc findArc(Point2D p) {
        for (GraphArcOut to : graphNet.getGraphArcOutList()) {
            if (to.isEnoughDistance(p) && isDrawnOnThisCanvas(to.getBeginElement())
                    && isDrawnOnThisCanvas(to.getEndElement())) {
                return to;
            }
        }
        for (GraphArcIn ti : graphNet.getGraphArcInList()) {
            if (ti.isEnoughDistance(p) && isDrawnOnThisCanvas(ti.getBeginElement())
                    && isDrawnOnThisCanvas(ti.getEndElement())) {
                return ti;
            }
        }
        return null;
    }

    /**
     * Takes one place or transition off the canvas, releasing it from whatever Petri-object
     * claimed it and dropping any shared place that referred to it.
     *
     * <p>Releasing it here is what keeps a frame from claiming an element the canvas no longer
     * draws. That used to happen on every route into this method, so an object's own member set
     * and the canvas disagreed permanently: the object still counted the element, still exported
     * it and still carried it when moved, while nothing was there.
     */
    public void remove(GraphElement s) throws ExceptionInvalidNetStructure {
        if (s == null) {
            return;
        }
        if (s == current) {
            current = null;

        }
        canvasModel.release(s);
        graphNet.delGraphElement(s); //added by Inna 4.12.2012
        canvasModel.removeDanglingFusions();

        repaint();
    }

    public class MouseWheelHendler implements MouseWheelListener {

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            if (e.getWheelRotation() == -1 && scale <= 0.15) {
                return;
            }
            scale += (double) e.getWheelRotation() / 10;
            repaint();
        }

    }

    /**
     * Ctrl+A: selects everything on the active canvas, of either kind - its own elements and the
     * Petri-objects drawn directly on it.
     *
     * <p>It stops at the boundary of an object rather than sweeping in everything on the whole
     * document. That used to be the easy route into the double-claim defect: selecting another
     * object's members and grouping them produced an object whose net was empty, because two
     * frames then claimed the same elements and only the first answered for them. Now the
     * selection can never hold something belonging to another object in the first place.
     */
    public void selectAll() { // works when key event is Ctrl+a
        selection.clear();
        for (GraphPetriPlace p : graphNet.getGraphPetriPlaceList()) {
            if (isOnThisCanvas(p)) {
                selection.add(p);
            }
        }
        for (GraphPetriTransition tr : graphNet.getGraphPetriTransitionList()) {
            if (isOnThisCanvas(tr)) {
                selection.add(tr);
            }
        }
        // A Petri-object frame is as much a selectable thing on this canvas as a place or a
        // transition is, so Ctrl+A reaches it too.
        for (GraphObjectFrame frame : canvasModel.childrenOf(focusedFrame)) {
            selection.add(frame);
        }
    }

    /**
     * Rubber-band select: everything on the active canvas whose centre the band covers, of either
     * kind.
     *
     * <p>A Petri-object is caught by its centre, exactly like a place or a transition. It used to
     * be caught only when the band enclosed it whole, while its own elements were caught by centre
     * with no owner check at all - so a band drawn across part of an object selected its contents
     * and left the object itself unselected, which is precisely the state that let a regrouping
     * claim elements another object still held.
     *
     * <p>A band is a fresh selection, so whatever was selected before it is dropped. That used not
     * to matter, because a frame could not be dragged with a selection at all; now that it can, an
     * object left selected by having just been created would be dragged along by the next
     * rubber-band drag that had nothing to do with it.
     *
     * @param band the rubber band, in canvas coordinates
     */
    public void selectIn(Rectangle band) {
        selection.clear();
        for (GraphPetriPlace p : graphNet.getGraphPetriPlaceList()) {
            if (isOnThisCanvas(p) && band.contains(p.getGraphElementCenter())) {
                selection.add(p);
            }
        }
        for (GraphPetriTransition tr : graphNet.getGraphPetriTransitionList()) {
            if (isOnThisCanvas(tr) && band.contains(tr.getGraphElementCenter())) {
                selection.add(tr);
            }
        }
        for (GraphObjectFrame frame : canvasModel.getFrames()) {
            if (frame == focusedFrame || !isFrameDrawnOnThisCanvas(frame)) {
                continue;
            }
            if (band.contains(frame.getBounds().getCenterX(), frame.getBounds().getCenterY())) {
                selection.add(frame);
            }
        }
    }

    /**
     * Single-click select: whichever one thing the active canvas draws at that point, of either
     * kind, replacing whatever was selected.
     *
     * <p>A frame wins over an element inside it, which is what locks a framed element on the
     * canvas it is not edited on. On the focused object's own canvas its rectangle is skipped, so
     * a click inside the room the user is standing in reaches the net rather than selecting the
     * room.
     *
     * @param point the click point in canvas coordinates
     * @return what got selected, {@code null} for a click on nothing
     */
    public Object selectAt(Point2D point) {
        selection.clear();
        GraphObjectFrame frame = frameAt(point);
        if (frame != null) {
            selection.setSelectedFrame(frame);
            return frame;
        }
        GraphElement element = find(point);
        if (element != null && isOnThisCanvas(element)) {
            selection.add(element);
            return element;
        }
        return null;
    }

    /**
     * Moves everything selected by the same offset, of either kind: elements directly, objects
     * with their whole subtree.
     *
     * @param dx horizontal offset in canvas units
     * @param dy vertical offset in canvas units
     */
    public void moveSelectionBy(int dx, int dy) {
        for (GraphElement element : selection.elements()) {
            Point2D centre = element.getGraphElementCenter();
            element.setNewCoordinates(new Point2D.Double(centre.getX() + dx, centre.getY() + dy));
        }
        for (GraphObjectFrame frame : selection.allFrames()) {
            moveFrame(frame, frame.getBounds().x + dx, frame.getBounds().y + dy);
        }
        canvasModel.syncFusions();
        updateArcCoordinates();
    }

    /**
     * Ctrl+C: picks up whatever is selected, of either kind, so Ctrl+V can put a copy of it down.
     */
    public void copySelection() {
        copiedElements = new ArrayList<>(selection.elements());
        copiedFrames = new ArrayList<>(selection.allFrames());
        // A selected object's own members are carried by the object, not as loose elements too:
        // otherwise pasting would produce both a copy of the object and a second, unframed copy of
        // its net on top of it.
        for (GraphObjectFrame frame : copiedFrames) {
            copiedElements.removeAll(canvasModel.membersOfSubtree(frame));
        }
    }

    /**
     * Ctrl+D: duplicates every selected Petri-object.
     *
     * <p>Every one of them, not only the last clicked. {@code Ctrl+A} then {@code Ctrl+D} used to
     * do nothing at all, because duplicate read the single-click frame and select-all never set
     * it - a promise the documentation already made and the mechanism never kept.
     */
    public void duplicateSelection() {
        for (GraphObjectFrame frame : selection.allFrames()) {
            duplicateObject(frame);
        }
    }

    /**
     * Delete: removes everything selected, of either kind, in one gesture, asking first.
     *
     * <p>Objects go first: removing one lifts its net one level out, and the element sweep below
     * then accounts for those elements as ordinary members of whatever now holds them.
     */
    public void deleteSelection() {
        List<GraphObjectFrame> frames = selection.allFrames();
        if (!frames.isEmpty() && choosenArc == null && choosen == null) {
            if (frames.size() == 1) {
                confirmRemoveObjectFrame(frames.getFirst());
            } else {
                confirmRemoveObjectFrames(frames);
            }
        }
        if (choosenArc != null) {
            removeArc(choosenArc);

            /* saving this edit for possible undoing */
            DeleteArcEdit edit = new DeleteArcEdit(PetriNetsPanel.this, choosenArc);
            PetriNetsFrame.getUndoSupport().postEdit(edit);

            choosenArc = null;
            currentArc = null;
        }
        if (choosen != null) {
            deleteElement(choosen);
            choosen = null;
            current = null;
        }
        if (!selection.elements().isEmpty()) {
            int result = JOptionPane.showConfirmDialog((Component) null,
                    "Are you sure you want to delete selected elements?",
                    "Delete", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                deleteSelectedElements();
            }
        }
    }

    /**
     * The bulk half of {@link #deleteSelection}: every selected place and transition, with the
     * arcs that touch them, as one undoable step.
     *
     * <p>Asks nothing, so it is reachable without a dialog - {@code JOptionPane} cannot run in a
     * test JVM, and an operation that only exists behind a modal confirmation cannot be exercised
     * at all. The confirmation lives in {@link #deleteSelection}.
     */
    public void deleteSelectedElements() {
        List<GraphElement> deleted = new ArrayList<>(selection.elements());
        List<GraphArcIn> inArcsToBeRemoved = new ArrayList<>();
        List<GraphArcOut> outArcsToBeRemoved = new ArrayList<>();
        DeleteGraphElementsEdit edit = new DeleteGraphElementsEdit(
                PetriNetsPanel.this, deleted, inArcsToBeRemoved, outArcsToBeRemoved);
        try {
            for (GraphElement graphElement : deleted) {
                /* finding arcs that will be deleted along with this element. It's mostly a copy-paste from
                 * PetriGraphNet.removeElement and this functionality probably should be merged,
                 * but copy-pasting was the least invasive method of implementing bulk delete undoing.
                 */
                for (GraphArcIn arc : getGraphNet().getGraphArcInList()) {
                    if (arc.getBeginElement() == graphElement
                            || arc.getEndElement() == graphElement) {
                        if (!inArcsToBeRemoved.contains(arc)) {
                            inArcsToBeRemoved.add(arc);
                        }
                    }
                }

                for (GraphArcOut arc : getGraphNet().getGraphArcOutList()) {
                    if (arc.getBeginElement() == graphElement
                            || arc.getEndElement() == graphElement) {
                        if (!outArcsToBeRemoved.contains(arc)) {
                            outArcsToBeRemoved.add(arc);
                        }
                    }
                }
                /* found all arcs that will be deleted */

                // Read before remove(): removing releases the element, so this is the last moment
                // the answer exists, and undo needs it to put the element back into its object.
                edit.rememberOwner(graphElement, canvasModel.ownerOf(graphElement));
                remove(graphElement);
                PetriNetsPanel.this.setDefaultColorGraphElements(); //27.07.2018
            }
            /* save this action into undo manager so that it can be undone */
            PetriNetsFrame.getUndoSupport().postEdit(edit);
        } catch (ExceptionInvalidNetStructure ex) {
            LOGGER.error("Unexpected error", ex);
        } finally {
            selection.clear();
            PetriNetsPanel.this.setDefaultColorGraphElements();//27.07.2018
        }
    }

    private void setDefaultColorGraphElements() {
        for (GraphPetriPlace p : graphNet.getGraphPetriPlaceList()) {
            p.setColor(Color.BLACK);
        }
        for (GraphPetriTransition tr : graphNet.getGraphPetriTransitionList()) {
            tr.setColor(Color.BLACK);
        }
    }

    private void setDefaultColorGraphArcs() {
        for (GraphArcIn ti : graphNet.getGraphArcInList()) {
            ti.setColor(Color.BLACK);
        }

        for (GraphArcOut to : graphNet.getGraphArcOutList()) {
            to.setColor(Color.BLACK);
        }
    }

    public void redraw() {
        setDefaultColorGraphElements();
        setDefaultColorGraphArcs();
        repaint();
    }

    /**
     * The window to centre dialogs over.
     *
     * <p>This panel is the viewport view of a scroll pane, so its own {@code getLocationOnScreen()}
     * reflects the full — possibly large, possibly scrolled off-screen — canvas content rather
     * than the visible window. Passing the panel itself as a dialog's parent therefore centres
     * the dialog over whatever the canvas origin happens to be instead of over the application
     * window; the enclosing window is what a "centered" dialog actually means to the user.
     *
     * @return the enclosing window, or {@code null} if this panel is not yet showing in one
     */
    private Window dialogOwner() {
        return SwingUtilities.getWindowAncestor(this);
    }

    // ------------------------------------------------------------------ Petri-object context menus

    /**
     * Shows the right-click menu for a popup-trigger click, if any of the Petri-object
     * actions apply at that point.
     *
     * <p>A click that lands on a Petri-object frame gets that object's own menu, selection or no
     * selection. A non-empty element selection otherwise wins: right-clicking while elements are
     * selected means grouping that selection into a Petri-object, regardless of exactly what is
     * under the pointer. A specific place or transition keeps its existing right-click behaviour
     * (opening its properties), so this menu only claims clicks that hit a frame or empty canvas.
     *
     * <p>The frame check used to come after the selection check, which made a frame's own menu -
     * the only way to rename it, collapse it or remove it - unreachable while anything at all was
     * selected.
     *
     * @param ev the triggering mouse event, used both to test and to position the menu
     * @param point the click point in canvas coordinates
     * @return true if a menu was shown, so the caller should not process the click further
     */
    private boolean maybeShowContextMenu(MouseEvent ev, Point point) {
        if (!editable || !ev.isPopupTrigger()) {
            return false;
        }
        GraphObjectFrame frame = frameAt(point);
        if (frame != null && find(point) == null) {
            showObjectFrameMenu(ev, frame);
            return true;
        }
        if (!selection.elements().isEmpty()) {
            showGroupSelectionMenu(ev, new ArrayList<>(selection.elements()));
            return true;
        }
        if (find(point) != null) {
            return false; // a single element keeps its own right-click behaviour
        }
        if (frame != null) {
            showObjectFrameMenu(ev, frame);
            return true;
        }
        showNewObjectMenu(ev, point);
        return true;
    }

    private void showGroupSelectionMenu(MouseEvent ev, List<GraphElement> chunk) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem group = new JMenuItem(focusedFrame == null
                ? "Group selection into Petri-object"
                : "Group selection into a nested Petri-object");
        group.addActionListener(e -> askAndGroupIntoObject(chunk));
        menu.add(group);
        menu.show(this, ev.getX(), ev.getY());
    }

    private void showObjectFrameMenu(MouseEvent ev, GraphObjectFrame frame) {
        setSelectedFrame(frame);

        JPopupMenu menu = new JPopupMenu();

        JMenuItem editNet = new JMenuItem("Open this object's canvas");
        editNet.setToolTipText("Edit this object's own net in place - the same as double-clicking it");
        editNet.addActionListener(e -> openObjectCanvas(frame));
        menu.add(editNet);

        JMenuItem collapse = new JMenuItem(frame.isCollapsed() ? "Expand" : "Collapse");
        collapse.setToolTipText(frame.isCollapsed()
                ? "Show this object's net inside its frame again"
                : "Shrink this object to a summary box, hiding its net");
        collapse.addActionListener(e -> toggleCollapsed(frame));
        menu.add(collapse);

        menu.addSeparator();

        JMenuItem rename = new JMenuItem("Rename Petri-object...");
        rename.addActionListener(e -> renameObject(frame));
        menu.add(rename);

        JMenuItem priority = new JMenuItem("Priority of Petri-object...");
        priority.addActionListener(e -> changeObjectPriority(frame));
        menu.add(priority);

        JMenuItem duplicate = new JMenuItem("Duplicate Petri-object");
        duplicate.setToolTipText("Copy the object together with its net — the way to get N alike");
        duplicate.addActionListener(e -> duplicateObject(frame));
        menu.add(duplicate);

        JMenuItem saveAsTemplate = new JMenuItem("Save as Petri-object...");
        saveAsTemplate.setToolTipText(
                "Keep this object to reuse — it joins the PObjects list and can live on the left toolbar");
        saveAsTemplate.addActionListener(e -> saveObjectAsTemplate(frame));
        menu.add(saveAsTemplate);

        menu.addSeparator();

        JMenuItem remove = new JMenuItem("Remove Petri-object frame");
        remove.setToolTipText("The net inside stays on the canvas");
        remove.addActionListener(e -> confirmRemoveObjectFrame(frame));
        menu.add(remove);

        menu.show(this, ev.getX(), ev.getY());
    }

    private void showNewObjectMenu(MouseEvent ev, Point at) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem empty = new JMenuItem("New empty Petri-object");
        empty.setToolTipText("Put an empty Petri-object frame on the canvas and draw its net inside");
        empty.addActionListener(e -> addEmptyObjectFrame(at));
        menu.add(empty);

        JMenuItem fromLibrary = new JMenuItem("Petri-object from net library...");
        fromLibrary.setToolTipText("Instantiate a net library template with arguments of its own");
        fromLibrary.addActionListener(e -> addObjectFromLibrary(at));
        menu.add(fromLibrary);

        menu.show(this, ev.getX(), ev.getY());
    }

    /**
     * Asks for a name and groups the chunk. Split from {@link #groupIntoObject} so the grouping
     * itself is reachable without a dialog: {@code JOptionPane} cannot run in a test JVM, and an
     * operation that can only be exercised through a modal dialog cannot be tested at all.
     */
    private void askAndGroupIntoObject(List<GraphElement> chunk) {
        String name = JOptionPane.showInputDialog(dialogOwner(), "Name of the Petri-object",
                "Object " + (canvasModel.getFrames().size() + 1));
        if (name == null || name.isBlank()) {
            return;
        }
        groupIntoObject(chunk, name.trim());
    }

    /**
     * Draws a Petri-object frame around the given elements, which is how an existing net is
     * split into objects.
     *
     * <p>Grouping always groups what is on the active canvas, so doing it inside an object
     * produces an object nested in that one - the defect where regrouping another object's
     * elements produced an empty object becomes the feature of nesting instead. A nested object is
     * created collapsed, since the user asked to see nested objects in collapsed form; from then
     * on its collapsed flag is theirs to change.
     *
     * @param chunk the elements to group; they are claimed for the new object, which releases them
     *        from whatever claimed them before
     * @param name name for the new Petri-object
     * @return the new frame, or {@code null} when there was nothing to group
     */
    public GraphObjectFrame groupIntoObject(List<GraphElement> chunk, String name) {
        if (chunk.isEmpty()) {
            return null;
        }
        GraphObjectFrame frame = new GraphObjectFrame(name, boundsAround(chunk));
        canvasModel.nest(frame, focusedFrame);
        addObjectFrame(frame);
        for (GraphElement element : chunk) {
            canvasModel.claim(frame, element);
        }
        if (focusedFrame != null) {
            growToContain(focusedFrame, frame.getBounds());
            frame.setCollapsed(true);
        }

        // The grouped elements are now locked inside their new frame — highlighting them as
        // "selected" afterward would be stale and, unlike before, no longer something Delete
        // or a drag could act on directly.
        for (GraphElement element : chunk) {
            element.setColor(Color.BLACK);
        }
        selection.clear();
        selection.setSelectedFrame(frame);
        choosen = null;
        repaint();
        return frame;
    }

    /**
     * Grows a frame just enough to contain a rectangle. Applied once, when a nested object is
     * created and the box fitted around its net escapes its parent - there is no auto-refit
     * afterwards, so moving things around later never resizes anything behind the user's back.
     */
    private void growToContain(GraphObjectFrame frame, Rectangle inner) {
        Rectangle grown = frame.getBounds().union(inner);
        if (!grown.equals(frame.getBounds())) {
            frame.setBounds(grown);
        }
    }

    /**
     * Shrinks a Petri-object to its summary box, or restores the size it had - the frame's own
     * context menu is the only way to reach this, which is why nothing but a loaded model could
     * collapse an object before.
     */
    private void toggleCollapsed(GraphObjectFrame frame) {
        frame.setCollapsed(!frame.isCollapsed());
        repaint();
    }

    /**
     * Puts an empty frame at the click location, to be drawn into.
     */
    private void addEmptyObjectFrame(Point at) {
        String name = JOptionPane.showInputDialog(dialogOwner(), "Name of the Petri-object",
                "Object " + (canvasModel.getFrames().size() + 1));
        if (name == null || name.isBlank()) {
            return;
        }
        Rectangle bounds = new Rectangle(Math.max(0, at.x - 180), Math.max(0, at.y - 120), 360, 240);
        GraphObjectFrame frame = new GraphObjectFrame(name.trim(), bounds);
        canvasModel.nest(frame, focusedFrame);
        addObjectFrame(frame);
        if (focusedFrame != null) {
            growToContain(focusedFrame, bounds);
        }
    }

    /**
     * Instantiates a net library template as a new Petri-object at the click location: its
     * net is laid out on the canvas and a frame is drawn around it.
     */
    private void addObjectFromLibrary(Point at) {
        NetTemplateDialog dialog = new NetTemplateDialog(
                dialogOwner(),
                "Object " + (canvasModel.getFrames().size() + 1));
        dialog.setVisible(true);
        if (dialog.getBuilt() == null) {
            return;
        }
        try {
            placeBuiltNet(dialog.getBuilt(), at, dialog.getObjectName(), dialog.getReference());
        } catch (Exception failure) {
            LOGGER.error("Failed to add a Petri-object from the net library", failure);
            MessageHelper.showException(dialogOwner(), "Cannot put the net library template on the canvas", failure);
        }
    }

    /**
     * Stamps the armed Petri-object template at the click point — the toolbar's placement
     * tool, which unlike {@link #addObjectFromLibrary} asks nothing and can therefore be
     * repeated as fast as the user can click.
     */
    private void stampArmedTemplate(Point at) {
        if (armedTemplate == null) {
            return;
        }
        try {
            String name = uniqueObjectName(armedTemplate.displayName());
            if (armedTemplate.kind() == PetriObjectTemplate.Kind.PROTOTYPE) {
                // Re-read per stamp rather than deep-copying one held prototype: the file is
                // the single source of truth, and two stamps then cannot share any state.
                PnmlParser parser = new PnmlParser();
                PetriNet net = parser.parse(armedTemplate.prototypeFile().toFile());
                placeGraphNet(GraphNetBuilder.build(net, parser.getAllPlaceCoordinates(),
                        parser.getAllTransitionCoordinates(), at), name, null);
            } else {
                PetriNet net = NetTemplateCatalog.instantiate(
                        armedTemplate.methodName(), armedTemplate.arguments());
                placeBuiltNet(net, at, name, armedTemplate.toReference());
            }
        } catch (Exception failure) {
            LOGGER.error("Failed to stamp the Petri-object template {}", armedTemplate.id(), failure);
            MessageHelper.showException(dialogOwner(),
                    "Cannot put '" + armedTemplate.displayName() + "' on the canvas", failure);
        }
    }

    /**
     * Hands a freshly loaded net to the user to position: an outline follows the pointer until
     * they click, and the net lands exactly there.
     *
     * <p>This replaced automatic placement. What used to happen was that the net was dropped
     * wherever a calculation guessed — measured from element centres only, with no vertical
     * component and no awareness of Petri-object frames — which routinely buried it under
     * whatever was already drawn. Where a net goes is a decision only the person looking at
     * the canvas can make.
     *
     * @param net the net to place, carrying whatever coordinates it was built or read with
     */
    public void placeNetInteractively(GraphPetriNet net) {
        if (net == null) {
            return;
        }
        setTool(CanvasTool.PLACE_LOADED_NET, null);
        pendingNet = net;
        // Until the pointer has been over the canvas there is nowhere to draw the outline;
        // mouseMoved fills this in on the first movement.
        placementPoint = null;
        requestFocusInWindow();
        repaint();
    }

    /** @return true while a loaded net is waiting for the user to place it */
    public boolean isPlacingNet() {
        return tool == CanvasTool.PLACE_LOADED_NET && pendingNet != null;
    }

    /** @return the canvas zoom, so callers can convert between screen and canvas coordinates */
    public double getScale() {
        return scale;
    }

    /**
     * Adds a net at the coordinates it already carries — the non-interactive counterpart of
     * {@link #placeNetInteractively}, for when there is nothing on the canvas to collide with
     * and therefore no placement to ask about.
     */
    public void addNet(GraphPetriNet net) {
        absorbNet(net);
        repaint();
    }

    /**
     * Drops the waiting net at the click point and leaves placement mode — one net loaded is
     * one net placed, so unlike the palette tools this does not stay armed.
     */
    private void commitPendingNet(Point at) {
        if (pendingNet == null) {
            return;
        }
        GraphPetriNet net = pendingNet;
        pendingNet = null;
        placementPoint = null;

        net.changeLocation(at);
        // No Petri-object frame around it: a loaded net is a net, and boxing it up would
        // declare it one object of a model, which is a modelling decision the user makes
        // themselves afterwards by grouping what they want.
        absorbNet(net);
        setTool(CanvasTool.SELECT, null);
        repaint();
    }

    /**
     * Abandons a net the user decided not to place. Called when they switch tools or press
     * Escape — without it the outline would follow the pointer forever with no way out.
     */
    private void cancelPendingNet() {
        if (pendingNet == null) {
            return;
        }
        pendingNet = null;
        placementPoint = null;
        repaint();
    }

    /**
     * Saves a Petri-object so it can be stamped again later, from the toolbar or the PObjects
     * list. The object's net is deep-copied first: writing it out calls {@code createPetriNet},
     * which renumbers the elements it is given, and doing that to the canvas's own instances
     * would renumber the live drawing underneath the user.
     */
    private void saveObjectAsTemplate(GraphObjectFrame frame) {
        GraphPetriNet inside = buildObjectNet(frame);
        if (inside.getGraphPetriPlaceList().isEmpty() && inside.getGraphPetriTransitionList().isEmpty()) {
            MessageHelper.showError(dialogOwner(), "This Petri-object has no net to save yet");
            return;
        }
        String name = JOptionPane.showInputDialog(dialogOwner(),
                "Name for the saved Petri-object", frame.getName());
        if (name == null || name.isBlank()) {
            return;
        }
        try {
            new PetriObjectPalette().saveCustom(new GraphPetriNet(inside), name.trim());
            MessageHelper.showInfo(dialogOwner(), "'" + name.trim()
                    + "' saved. Add it to the toolbar from the PObjects menu.");
        } catch (Exception failure) {
            LOGGER.error("Failed to save the Petri-object {} as a template", frame.getName(), failure);
            MessageHelper.showException(dialogOwner(), "Cannot save this Petri-object", failure);
        }
    }

    /**
     * Lays a freshly built net out at a point and wraps it in a Petri-object frame.
     *
     * <p>The net's own elements are appended to the canvas directly rather than through
     * {@link #addGraphNet}: that path merges via {@code GraphPetriNet.mergeGraphNet}, which
     * copies every element and then repositions the copies by its own layout calculator. The
     * frame would end up drawn around — and claiming as members — the originals, which by then
     * are not the instances on the canvas and are not where the user clicked either.
     *
     * @param net the newly instantiated net; must not already be on the canvas
     * @param at where the user clicked, in canvas coordinates
     * @param objectName name for the new Petri-object
     * @param template provenance recorded on the frame, so a saved model remembers the recipe
     */
    private void placeBuiltNet(PetriNet net, Point at, String objectName, NetTemplateRef template) {
        placeGraphNet(SimpleNetGraphBuilder.build(net, at), objectName, template);
    }

    /**
     * Adds an already laid-out net to the canvas as a Petri-object. Split from
     * {@link #placeBuiltNet} because a saved prototype arrives with its own coordinates from
     * the file and must not be re-laid-out, whereas a library template has none and has to be.
     */
    private void placeGraphNet(GraphPetriNet built, String objectName, NetTemplateRef template) {
        List<GraphElement> members = absorbNet(built);

        GraphObjectFrame frame = new GraphObjectFrame(objectName, boundsAround(members));
        frame.setTemplate(template);
        canvasModel.nest(frame, focusedFrame);
        addObjectFrame(frame);
        for (GraphElement element : members) {
            canvasModel.claim(frame, element);
            // Locked inside a frame from the moment it lands, so it is never left looking
            // selected — nothing on the canvas could act on that selection anyway.
            element.setColor(Color.BLACK);
        }
        selection.clear();
        choosen = null;
        if (focusedFrame != null) {
            growToContain(focusedFrame, frame.getBounds());
            frame.setCollapsed(true);
        }
        // addObjectFrame leaves what it added selected, which is right when the user created
        // one deliberately but wrong here: stamping drops object after object, and each would
        // sit highlighted with nothing having been selected at all.
        selection.setSelectedFrame(null);
        repaint();
    }

    /**
     * Takes a freshly built net's own places, transitions and arcs onto the canvas, at the
     * coordinates they already carry.
     *
     * <p>Deliberately not {@link #addGraphNet}: that merges, and merging copies every element
     * before adding it, so the instances the caller is holding are not the ones that end up on
     * screen — which matters to anything that needs to keep hold of them afterwards, like
     * drawing a frame around exactly this net.
     *
     * @return the elements now on the canvas, in the caller's own instances
     */
    private List<GraphElement> absorbNet(GraphPetriNet built) {
        List<GraphElement> members = new ArrayList<>();
        members.addAll(built.getGraphPetriPlaceList());
        members.addAll(built.getGraphPetriTransitionList());

        if (graphNet == null) {
            setCanvasNet(built);
        } else {
            graphNet.getGraphPetriPlaceList().addAll(built.getGraphPetriPlaceList());
            graphNet.getGraphPetriTransitionList().addAll(built.getGraphPetriTransitionList());
            graphNet.getGraphArcInList().addAll(built.getGraphArcInList());
            graphNet.getGraphArcOutList().addAll(built.getGraphArcOutList());
        }
        for (GraphArcIn arcIn : built.getGraphArcInList()) {
            arcIn.updateCoordinates();
        }
        for (GraphArcOut arcOut : built.getGraphArcOutList()) {
            arcOut.updateCoordinates();
        }
        protectPetriNumbering();
        return members;
    }

    /**
     * PetriP/PetriT number the whole canvas with one JVM-wide counter each, and every
     * {@code NetLibrary} factory method resets its counter back to zero right after building —
     * fine for one net built in isolation, but it means the next thing constructed (another
     * stamped Petri-object, a place added with the toolbar) can start from zero again and reuse
     * a number already on the canvas. {@code PetriNet}'s constructor resolves every arc by
     * comparing these numbers as plain ints with no identity fallback, so a collision doesn't
     * just mislabel something — it can silently misroute an arc onto the wrong transition.
     * Raising both counters past whatever the canvas already holds, right after anything is
     * merged in, keeps every number handed out afterward unique.
     */
    private void protectPetriNumbering() {
        if (graphNet == null) {
            return;
        }
        int maxP = graphNet.getGraphPetriPlaceList().stream()
                .mapToInt(p -> p.getPetriPlace().getNumber())
                .max().orElse(-1);
        int maxT = graphNet.getGraphPetriTransitionList().stream()
                .mapToInt(t -> t.getPetriTransition().getNumber())
                .max().orElse(-1);
        PetriP.ensureNextAtLeast(maxP + 1);
        PetriT.ensureNextAtLeast(maxT + 1);
    }

    /**
     * @return {@code base}, or {@code base} with a counter appended, so that two stamps of the
     *         same template never share a name — Petri-objects are addressed by name in the
     *         statistics formulas, where a duplicate would be ambiguous
     */
    private String uniqueObjectName(String base) {
        boolean taken = canvasModel.getFrames().stream()
                .anyMatch(frame -> base.equals(frame.getName()));
        if (!taken) {
            return base;
        }
        for (int suffix = 2; ; suffix++) {
            String candidate = base + " " + suffix;
            String attempt = candidate;
            if (canvasModel.getFrames().stream().noneMatch(frame -> attempt.equals(frame.getName()))) {
                return candidate;
            }
        }
    }

    /**
     * Copies a Petri-object with its net, which is the quick way to a model of several alike
     * objects.
     */
    private void duplicateObject(GraphObjectFrame frame) {
        duplicateObject(frame, frame.getName() + " copy");
    }

    /**
     * Copies a Petri-object with its net.
     *
     * @param frame the object to copy
     * @param name name for the copy - taken as a parameter so the operation is reachable without
     *        a dialog, the same reason {@link #groupIntoObject} takes one
     * @return the copy, or {@code null} when there was nothing to copy
     */
    public GraphObjectFrame duplicateObject(GraphObjectFrame frame, String name) {
        List<GraphElement> inside = new ArrayList<>();
        for (GraphPetriPlace place : graphNet.getGraphPetriPlaceList()) {
            if (canvasModel.ownerOf(place) == frame) {
                inside.add(place);
            }
        }
        for (GraphPetriTransition transition : graphNet.getGraphPetriTransitionList()) {
            if (canvasModel.ownerOf(transition) == frame) {
                inside.add(transition);
            }
        }
        if (inside.isEmpty()) {
            MessageHelper.showError(dialogOwner(), "The Petri-object has no net to copy yet");
            return null;
        }

        GraphPetriNet.GraphNetFragment copy = graphNet.bulkCopyNoPasteElements(inside);
        int dx = frame.getBounds().width + 40;
        for (GraphElement element : copy.elements) {
            Point2D centre = element.getGraphElementCenter();
            element.setNewCoordinates(new Point2D.Double(centre.getX() + dx, centre.getY()));
        }
        addNetFragment(copy);

        // Fitted around the copy it actually holds, exactly like every other creation path. It
        // used to be the original's rectangle translated by the same dx, which forgot that
        // addNetFragment adds a 15,15 paste offset of its own to every element - so the copy's net
        // sat 15 pixels off inside its own frame, a drift no other path had.
        GraphObjectFrame duplicate = new GraphObjectFrame(name, boundsAround(copy.elements));
        duplicate.setPriority(frame.getPriority());
        duplicate.setTemplate(frame.getTemplate());
        duplicate.setContentVisible(frame.isContentVisible());
        canvasModel.nest(duplicate, canvasModel.enclosingOf(frame));
        addObjectFrame(duplicate);
        for (GraphElement element : copy.elements) {
            canvasModel.claim(duplicate, element);
            // Locked inside the copy, so leaving it selected would let Delete or a drag act on an
            // object's own net from the shared canvas - which is exactly what every other creation
            // path deliberately avoids.
            element.setColor(Color.BLACK);
            selection.remove(element);
        }
        if (frame.isCollapsed()) {
            duplicate.setCollapsed(true);
        }
        repaint();
        return duplicate;
    }

    private void renameObject(GraphObjectFrame frame) {
        String name = JOptionPane.showInputDialog(dialogOwner(), "Name of the Petri-object", frame.getName());
        if (name != null && !name.isBlank()) {
            frame.setName(name.trim());
            canvasStack.notifyChanged();
            repaint();
        }
    }

    private void changeObjectPriority(GraphObjectFrame frame) {
        String value = JOptionPane.showInputDialog(dialogOwner(),
                "Priority of the Petri-object — the higher it is, the earlier this object acts "
                        + "when several want to act at the same moment",
                frame.getPriority());
        if (value == null) {
            return;
        }
        try {
            frame.setPriority(Integer.parseInt(value.trim()));
            repaint();
        } catch (NumberFormatException malformed) {
            MessageHelper.showError(dialogOwner(), "Priority has to be a whole number");
        }
    }

    /**
     * Switches the canvas to one Petri-object's own net.
     *
     * <p>There is no second panel, no second document and no second net. The one canvas simply
     * paints, hit-tests and edits a different level of the one document, and the viewport scrolls
     * to where that object actually is. That last part is the whole fix for the reported "I
     * grouped a chunk and its editor opened empty": the object's net was always intact, but the
     * modal editor sized its panel from the frame's width and height while painting at absolute
     * canvas coordinates, so a chunk grouped at 900,700 was drawn 900,700 out on a 500x400 view
     * whose scroll pane had nothing to scroll to. Coordinates stay absolute here and the viewport
     * moves instead.
     *
     * <p>There is nothing to save and nothing to cancel. An edit made on this canvas is an edit to
     * the model at the moment it is made, exactly like an edit on the net's canvas, and Ctrl+Z is
     * what undoes it.
     *
     * @param frame the object to edit, or {@code null} for the net's own canvas
     */
    public void openObjectCanvas(GraphObjectFrame frame) {
        if (frame != null && !canvasModel.getFrames().contains(frame)) {
            return;
        }
        canvasStack.open(frame);
        focusedFrame = frame;
        clearSelectionState();
        if (frame != null) {
            // A collapsed frame is a 170x56 summary box, which is not a room anything can be
            // edited inside: the net it holds is drawn at full size and would spill straight out
            // of it. Entering the object expands it back to the box it was fitted with.
            if (frame.isCollapsed()) {
                frame.setCollapsed(false);
            }
            Rectangle target = new Rectangle(frame.getBounds());
            target.grow(SCROLL_TO_MARGIN, SCROLL_TO_MARGIN);
            scrollRectToVisible(target);
        }
        repaint();
    }

    /** Slack left around an object when the viewport scrolls to it, so it is not flush to an edge. */
    private static final int SCROLL_TO_MARGIN = 40;

    /**
     * Closes one Petri-object's canvas, and with it the canvas of everything nested inside it.
     * Asks nothing, because nothing is pending: every edit made there is already in the model.
     *
     * @param frame the object whose canvas to close
     */
    public void closeObjectCanvas(GraphObjectFrame frame) {
        canvasStack.close(frame);
        openObjectCanvas(canvasStack.getActive());
    }

    /**
     * @return the Petri-object whose own canvas is active, or {@code null} for the net's canvas
     */
    public GraphObjectFrame getFocusedFrame() {
        return focusedFrame;
    }

    /**
     * Brings the net's own canvas to the front. Called when a run or an animation starts: a run is
     * a run of the whole model, so it has to be watched where the whole model is drawn - otherwise
     * pressing play inside an object shows a fragment of what is actually running.
     */
    public void activateRootCanvas() {
        if (focusedFrame != null) {
            openObjectCanvas(null);
        }
    }

    /**
     * Filters out one Petri-object's own places, transitions and internal arcs into a net of
     * their own - the same instances the canvas holds, not copies. An arc crossing to another
     * object is a link, not part of this object's own net, and is left out.
     *
     * <p>The whole subtree counts as this object's own, so saving an object that contains a nested
     * one as a reusable Petri-object keeps what the nested one holds. From outside, a nest is one
     * object.
     *
     * @param frame the object to filter out
     * @return a net of exactly that object's own elements and internal arcs
     */
    private GraphPetriNet buildObjectNet(GraphObjectFrame frame) {
        List<GraphObjectFrame> subtree = canvasModel.subtreeOf(frame);
        ArrayList<GraphPetriPlace> places = new ArrayList<>();
        ArrayList<GraphPetriTransition> transitions = new ArrayList<>();
        ArrayList<GraphArcIn> arcsIn = new ArrayList<>();
        ArrayList<GraphArcOut> arcsOut = new ArrayList<>();
        for (GraphPetriPlace place : graphNet.getGraphPetriPlaceList()) {
            if (subtree.contains(canvasModel.ownerOf(place))) {
                places.add(place);
            }
        }
        for (GraphPetriTransition transition : graphNet.getGraphPetriTransitionList()) {
            if (subtree.contains(canvasModel.ownerOf(transition))) {
                transitions.add(transition);
            }
        }
        for (GraphArcIn arc : graphNet.getGraphArcInList()) {
            if (subtree.contains(canvasModel.ownerOf(arc.getBeginElement()))
                    && subtree.contains(canvasModel.ownerOf(arc.getEndElement()))) {
                arcsIn.add(arc);
            }
        }
        for (GraphArcOut arc : graphNet.getGraphArcOutList()) {
            if (subtree.contains(canvasModel.ownerOf(arc.getBeginElement()))
                    && subtree.contains(canvasModel.ownerOf(arc.getEndElement()))) {
                arcsOut.add(arc);
            }
        }
        return new GraphPetriNet(null, places, transitions, arcsIn, arcsOut);
    }

    private void confirmRemoveObjectFrame(GraphObjectFrame frame) {
        if (MessageHelper.showConfirmation(dialogOwner(),
                "Remove the Petri-object frame '" + frame.getName() + "'? Its net stays on the canvas.")) {
            removeObjectFrame(frame);
        }
    }

    /**
     * The bulk-selection counterpart of {@link #confirmRemoveObjectFrame} — what Delete does
     * with several frames selected together, e.g. by Ctrl+A, the same way it already bulk-
     * removes a multi-selection of places and transitions.
     */
    private void confirmRemoveObjectFrames(List<GraphObjectFrame> frames) {
        if (MessageHelper.showConfirmation(dialogOwner(),
                "Remove " + frames.size() + " Petri-object frames? Their nets stay on the canvas.")) {
            for (GraphObjectFrame frame : frames) {
                removeObjectFrame(frame);
            }
            selection.frames().clear();
            selection.setSelectedFrame(null);
        }
    }

    /**
     * @return a frame that encloses the given elements with room to spare
     */
    /**
     * The rubber band as a rectangle. The two drag points arrive in whichever order the drag
     * happened, so they are normalised here — comparing them directly, as this used to, meant
     * only a drag going down-and-right ever enclosed anything, and one going up or left
     * silently selected nothing at all.
     */
    private Rectangle marqueeRectangle() {
        int x = Math.min(startDragMouseLocation.x, currentDragMouseLocation.x);
        int y = Math.min(startDragMouseLocation.y, currentDragMouseLocation.y);
        int width = Math.abs(currentDragMouseLocation.x - startDragMouseLocation.x);
        int height = Math.abs(currentDragMouseLocation.y - startDragMouseLocation.y);
        return new Rectangle(x, y, width, height);
    }

    /**
     * @param elements the elements a frame has to enclose
     * @return a rectangle around them with room to spare. An empty collection gives a degenerate
     *         rectangle at the origin rather than the two-billion-unit box the unreplaced min/max
     *         sentinels used to produce, complete with a width that came out of an integer
     *         overflow - reachable from a saved Petri-object prototype whose file builds nothing.
     */
    private static Rectangle boundsAround(List<? extends GraphElement> elements) {
        if (elements.isEmpty()) {
            return new Rectangle(0, 0, 0, 0);
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (GraphElement element : elements) {
            Point2D centre = element.getGraphElementCenter();
            int border = Math.max(element.getBorder(), 20);
            minX = Math.min(minX, (int) centre.getX() - border);
            minY = Math.min(minY, (int) centre.getY() - border);
            maxX = Math.max(maxX, (int) centre.getX() + border);
            maxY = Math.max(maxY, (int) centre.getY() + border);
        }
        // A transition draws its name above and, below it, both its parameter and its
        // probability on their own lines — together reaching about 65px past its own
        // centre, well outside the 20px "border" that boundsAround otherwise uses (which
        // only accounts for the transition's own shape, not the text stacked under it).
        // The padding has to clear that worst case on every side, not just what a place's
        // shorter single mark label underneath it would need.
        int padding = 48;
        return new Rectangle(
                Math.max(0, minX - padding),
                Math.max(0, minY - padding - GraphObjectFrame.HEADER_HEIGHT),
                maxX - minX + padding * 2,
                maxY - minY + padding * 2 + GraphObjectFrame.HEADER_HEIGHT);
    }

    public class MouseHandler extends MouseAdapter {

        private java.util.Timer timer;
        private boolean isMouseButtonHold = false;

        /**
         * Set once a right-click has already been handled as a Petri-object context menu, so
         * the {@code mouseClicked} that follows the same gesture does not also clear the
         * selection or reopen an element's own property dialog.
         */
        private boolean contextMenuShown = false;

        @Override
        public void mousePressed(MouseEvent ev) {
            dragCompleted = false;
            Point scaledCurrentMousePoint = new Point((int) (ev.getX() / scale), (int) (ev.getY() / scale));
            if (maybeShowContextMenu(ev, scaledCurrentMousePoint)) {
                contextMenuShown = true;
                return;
            }

            // Pan and Delete are exclusive of every other canvas gesture — including a frame's
            // eye icon, its header/corner, and a port — since dragging the view or removing
            // whatever is clicked is the whole point of picking either tool in the first place.
            if (tool == CanvasTool.PAN) {
                if (SwingUtilities.isLeftMouseButton(ev)) {
                    beginPan(ev.getPoint());
                }
                return;
            }

            if (tool == CanvasTool.DELETE) {
                if (SwingUtilities.isLeftMouseButton(ev)) {
                    handleDeleteClick(scaledCurrentMousePoint);
                }
                return;
            }

            // Same exclusivity as Pan/Delete: the whole point of these tools staying active
            // across clicks is that every click drops another element, never anything else.
            if (tool == CanvasTool.ADD_PLACE || tool == CanvasTool.ADD_TRANSITION) {
                if (SwingUtilities.isLeftMouseButton(ev)) {
                    addElementAt(tool, scaledCurrentMousePoint);
                }
                return;
            }

            if (tool == CanvasTool.ADD_PETRI_OBJECT) {
                if (SwingUtilities.isLeftMouseButton(ev)) {
                    stampArmedTemplate(scaledCurrentMousePoint);
                }
                return;
            }

            if (tool == CanvasTool.PLACE_LOADED_NET) {
                if (SwingUtilities.isLeftMouseButton(ev)) {
                    commitPendingNet(scaledCurrentMousePoint);
                }
                return;
            }

            // The eye icon sits inside the header's own rectangle, so it has to be checked
            // ahead of the header hit-test below — otherwise the click would be read as the
            // start of a frame drag instead of a toggle.
            GraphObjectFrame eyeFrame = frameEyeIconAt(scaledCurrentMousePoint);
            if (eyeFrame != null && SwingUtilities.isLeftMouseButton(ev)) {
                eyeFrame.setContentVisible(!eyeFrame.isContentVisible());
                selection.setSelectedFrame(eyeFrame);
                repaint();
                return;
            }

            // A frame's header or its corner is grabbed ahead of everything below — including
            // a port, or an owned element's own body standing in for one — since shrinking a
            // frame down small enough can put its own contents underneath either. Ports were
            // never able to overlap these before (they only ever sat on the border itself),
            // but an element's full body, now also reachable the same way while shown, can.
            // Gated on the left button: a right-click on a header used to start a frame drag,
            // which then had to be undone in mouseReleased once the popup trigger arrived there.
            if (SwingUtilities.isLeftMouseButton(ev)) {
                resizedFrame = frameHandleAt(scaledCurrentMousePoint);
                draggedFrame = resizedFrame == null ? frameHeaderAt(scaledCurrentMousePoint) : null;
            }
            if (resizedFrame != null || draggedFrame != null) {
                GraphObjectFrame grabbed = resizedFrame != null ? resizedFrame : draggedFrame;
                selection.setSelectedFrame(grabbed);
                frameDragOffset = new Point(
                        scaledCurrentMousePoint.x - grabbed.getBounds().x,
                        scaledCurrentMousePoint.y - grabbed.getBounds().y);
                setCursor(new Cursor(resizedFrame != null ? Cursor.SE_RESIZE_CURSOR : Cursor.MOVE_CURSOR));
                repaint();
                return;
            }

            // A port — or, while its object is shown, the real element it stands in for —
            // always starts a link next; no tool needs to be active first, since making
            // cross-object connections is the one thing a port is for.
            FramePort port = portOnCanvasAt(scaledCurrentMousePoint);
            if (port != null && SwingUtilities.isLeftMouseButton(ev)) {
                draggedFromPort = port;
                draggedPortCurrentPoint = scaledCurrentMousePoint;
                repaint();
                return;
            }

            startTimer();

            if (SwingUtilities.isLeftMouseButton(ev)) {
                leftMouseButtonPressed = true;
            }

            // Elements inside a Petri-object are edited on that object's own canvas, not
            // dragged around on the canvas above it - a click landing anywhere else in the
            // frame (not its header or resize handle) selects the object itself instead of
            // whatever net element happens to be underneath. The object being edited is skipped
            // by frameAt, so a click inside the room the user is standing in reaches its net.
            GraphObjectFrame frameAtPoint = frameAt(scaledCurrentMousePoint);
            if (frameAtPoint != null) {
                selection.setSelectedFrame(frameAtPoint);
                if (current != null) {
                    current.setColor(Color.BLACK);
                    current = null;
                }
                isSettingArc = false;
                repaint();
                return;
            }

            // A double-click that doesn't land on anything selectable pans the canvas
            // instead of doing nothing — restores a shortcut that existed before Pan became
            // its own dedicated tool, so nudging the view doesn't require switching tools.
            // Gated on clickCount so a normal single click still just clears the selection.
            if (tool == CanvasTool.SELECT && ev.getClickCount() >= 2
                    && SwingUtilities.isLeftMouseButton(ev)
                    && find(scaledCurrentMousePoint) == null
                    && findArc(scaledCurrentMousePoint) == null) {
                beginPan(ev.getPoint());
                selectToolPanning = true;
                setCursor(new Cursor(Cursor.HAND_CURSOR));
                return;
            }

            if (startDragMouseLocation == null) {
                startDragMouseLocation = scaledCurrentMousePoint;
            }
            prevMouseLocation = scaledCurrentMousePoint;
            if (tool == CanvasTool.MARQUEE) {
                // The marquee tool always rubber-band selects, even starting on top of an
                // element — never picks it up the way the default Select tool would.
                if (current != null) {
                    current.setColor(Color.BLACK);
                    current = null;
                }
                if (choosenArc != null) {
                    choosenArc.setColor(Color.BLACK);
                }
                choosenArc = null;
            } else if (current != null && SwingUtilities.isLeftMouseButton(ev)) {
                // A right-click never selects an element — it either falls through to its own
                // context menu above, or, on a lone element maybeShowContextMenu deliberately
                // leaves alone, does nothing at all now that that used to mean opening the
                // element's properties.
                current.setColor(Color.BLACK); //26.07.2018
                current = null;
                repaint();
            } else if (SwingUtilities.isLeftMouseButton(ev)) {
                current = find(scaledCurrentMousePoint);
                if (current != null) {
                    setDefaultColorGraphElements();
                    current.setColor(Color.BLUE); //26.07.2018
                    choosen = current;
                    // Remember where this element started: dragging it into another frame
                    // moves it to another Petri-object, which the user gets to confirm.
                    draggedElement = current;
                    ownerBeforeDrag = canvasModel.ownerOf(current);
                    Point2D centre = current.getGraphElementCenter();
                    positionBeforeDrag = centre == null ? null
                            : new Point2D.Double(centre.getX(), centre.getY());

                    if (!isSettingArc && isMouseButtonHold) {
                        current.setNewCoordinates(scaledCurrentMousePoint);
                        setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));

                        for (GraphArcIn ti : graphNet.getGraphArcInList()) {
                            ti.updateCoordinates();
                        }

                        for (GraphArcOut to : graphNet.getGraphArcOutList()) {
                            to.updateCoordinates();
                        }
                    }

                    if (choosenArc != null) {
                        choosenArc.setColor(Color.BLACK);//26.07.2018
                    }
                    choosenArc = null;
                }
                // currentPlacementPoint = e.getPoint();
            }

            if (isSettingArc == true && SwingUtilities.isLeftMouseButton(ev)) {
                current = find(scaledCurrentMousePoint);
                if (current != null) {
                    current.setColor(Color.BLUE);

                    if (current.getClass().equals(GraphPetriPlace.class)) {
                        currentArc = new GraphArcIn();
                        currentArc.setColor(Color.BLUE);//26.07.2018 
                        graphNet.getGraphArcInList().add((GraphArcIn) currentArc); //3.12.2012
                        currentArc.settingNewArc(current); //set begin element, point and setting LINe(0,0)
                    } else if (current.getClass().equals(GraphPetriTransition.class)) { //26.01.2013
                        currentArc = new GraphArcOut();
                        currentArc.setColor(Color.BLUE);//26.07.2018
                        graphNet.getGraphArcOutList().add((GraphArcOut) currentArc); //3.12.2012
                        currentArc.settingNewArc(current);
                    }
                } else {    //26.01.2013

                    isSettingArc = false;
                }
            }

            isSettingArc = false;//26.01.2013
            choosenArc = null;
            repaint();
        }

        @Override
        public void mouseClicked(MouseEvent ev) {
            if (contextMenuShown) {
                contextMenuShown = false;
                return;
            }
            if (dragCompleted) {
                // A drag just finished, so this click is the tail of that gesture rather than a
                // click on anything. AWT normally suppresses MOUSE_CLICKED after a drag and a
                // marquee selection survives its own release only because of that; this stops the
                // canvas depending on a platform promise, since the clear below would wipe the
                // selection the user just made.
                dragCompleted = false;
                return;
            }

            Point scaledCurrentMousePoint = new Point((int) (ev.getX() / scale), (int) (ev.getY() / scale));

            // The toggle already happened on mousePressed; this only keeps that same click
            // from also being read as a frame click below (the icon sits inside the header's
            // own rectangle, which canvasModel.frameAt would otherwise match too).
            if (frameEyeIconAt(scaledCurrentMousePoint) != null) {
                return;
            }

            // A frame — its header or its locked interior alike — selects the object on a
            // single click; a double click opens that object's own canvas, where its net can
            // actually be changed.
            GraphObjectFrame frameAtPoint = frameAt(scaledCurrentMousePoint);
            if (frameAtPoint != null) {
                selection.clear();
                selection.setSelectedFrame(frameAtPoint);
                if (ev.getClickCount() >= 2) {
                    openObjectCanvas(frameAtPoint);
                }
                repaint();
                return;
            }

            if (current == null && currentArc == null) { // previous click was empty

                //  PetriNetsPanel.this.printPointLocation(prevMouseLocation, "clear");
                setDefaultColorGraphElements();
                setDefaultColorGraphArcs();
                // Clicking nothing deselects the current Petri-object too. It used to survive
                // here, which went unnoticed only because a selected frame was drawn almost
                // identically to an unselected one.
                selection.clear();
                choosen = null;
            }
            if (current != null) {
                current.setColor(Color.BLUE); //26.07.2018
                selection.elements().clear(); // 27.08.2018
            } else {
                // A right-click never selects an element, here any more than in mousePressed —
                // it either hits the element's own context menu above, or, since
                // maybeShowContextMenu deliberately leaves a lone element alone, does nothing.
                if (SwingUtilities.isLeftMouseButton(ev)) {
                    current = find(scaledCurrentMousePoint);
                    if (current != null) {
                        current.setColor(Color.BLUE);//26.07.2018
                        choosen = current;
                    }
                    if (current != null && ev.getClickCount() >= 2) {
                        current.setColor(Color.BLUE);//26.07.2018
                        choosen = current;

                        if (choosen.getClass().equals(GraphPetriPlace.class)) {
                            setPositionFrame.setVisible(true);
                            setPositionFrame.setInfo(choosen);

                        } else {
                            setTransitionFrame.setVisible(true);
                            setTransitionFrame.setInfo(choosen);
                        }
                    }
                }

                currentArc = findArc(scaledCurrentMousePoint);
                if (currentArc != null && ev.getClickCount() >= 2) {
                    currentArc.setColor(Color.BLUE);
                    choosenArc = currentArc;
                    setArcFrame.setVisible(true);
                    setArcFrame.setInfo(choosenArc);
                }
                if (currentArc != null) {
                    currentArc.setColor(Color.BLUE);
                    choosenArc = currentArc;
                    choosen = null;
                    currentArc = null;
                }
            }
            setDefaultColorGraphElements();
            current = null;

            setCursor(Cursor.getDefaultCursor());
            repaint();
        }

        @Override
        public void mouseReleased(MouseEvent ev) {
            Point scaledCurrentMousePoint = new Point((int) (ev.getX() / scale), (int) (ev.getY() / scale));

            // On some platforms (notably Windows) the popup trigger fires on release, not
            // press — so a right-click on a frame being "dragged" by that same press still
            // has to end in the context menu, not a completed drag.
            if (maybeShowContextMenu(ev, scaledCurrentMousePoint)) {
                contextMenuShown = true;
                draggedFrame = null;
                resizedFrame = null;
                frameDragOffset = null;
                setCursor(Cursor.getDefaultCursor());
                return;
            }

            if (tool == CanvasTool.PAN) {
                endPan();
                return;
            }

            if (selectToolPanning) {
                endPan();
                selectToolPanning = false;
                setCursor(Cursor.getDefaultCursor());
                return;
            }

            if (tool == CanvasTool.DELETE || tool == CanvasTool.ADD_PLACE
                    || tool == CanvasTool.ADD_TRANSITION || tool == CanvasTool.ADD_PETRI_OBJECT
                    || tool == CanvasTool.PLACE_LOADED_NET) {
                return;
            }

            if (draggedFromPort != null) {
                FramePort targetPort = portOnCanvasAt(scaledCurrentMousePoint);
                if (targetPort != null) {
                    finishPortDrag(targetPort);
                } else {
                    finishPortDragToFreeElement(freeElementAt(scaledCurrentMousePoint));
                }
                repaint();
                return;
            }

            removeTimer();

            if (draggedFrame != null || resizedFrame != null) {
                draggedFrame = null;
                resizedFrame = null;
                frameDragOffset = null;
                leftMouseButtonPressed = false;
                startDragMouseLocation = null;
                currentDragMouseLocation = null;
                setCursor(Cursor.getDefaultCursor());
                repaint();
                return;
            }

            if (startDragMouseLocation != null && currentDragMouseLocation != null && leftMouseButtonPressed) {
                selectIn(marqueeRectangle());
                repaint();
            }

            confirmMoveBetweenObjects();
            if (selectionDragged) {
                selectionDragged = false;
                confirmBulkMoveBetweenObjects();
            }

            startDragMouseLocation = null;
            currentDragMouseLocation = null;
            current = null;
            //  setDefaultColorGraphElements();// deleted 27.07.2018

            setCursor(Cursor.getDefaultCursor());
            if (currentArc != null) {
                currentArc.setColor(Color.BLUE);
                current = find(scaledCurrentMousePoint);
                if (current != null && !isOnThisCanvas(current)) {
                    // An element belonging to another object only takes arcs through its ports -
                    // the arc tool cannot reach it directly, the same way it cannot be dragged.
                    // On an object's own canvas its members satisfy this, so the arc tool works
                    // inside it exactly as it does on a plain net.
                    current = null;
                }

                if (current != null) {
                    current.setColor(Color.BLUE);
                    if (currentArc.finishSettingNewArc(current)) {
                        currentArc.setPetriElements();
                        currentArc.changeBorder();
                        currentArc.updateCoordinates();
                        // Stays armed for the next arc instead of falling back to a plain
                        // click — the Arc tool is only actually left via setTool(), the same
                        // way Add Place/Add Transition already stay active across clicks.
                        isSettingArc = true;
                        currentArc.setColor(Color.BLACK);
                        int currBeginId, currEndId;
                        boolean isrepeat;
                        if (currentArc.getClass().equals(GraphArcIn.class)) {
                            currBeginId = ((GraphPetriPlace) currentArc.getBeginElement()).getId();
                            currEndId = ((GraphPetriTransition) currentArc.getEndElement()).getId();
                            isrepeat = false;
                            for (GraphArcIn ti : graphNet.getGraphArcInList()) {   // check if GraphArcOut is created for the same place and transition

                                if ((ti != currentArc)
                                        && ((GraphPetriPlace) ti.getBeginElement()).getId() == currBeginId
                                        && ((GraphPetriTransition) ti.getEndElement()).getId() == currEndId) {
                                    isrepeat = true;
                                    ti.getArcIn().setQuantity(ti.getArcIn().getQuantity() + 1);
                                    break;
                                }

                            }
                            if (isrepeat) {
                                graphNet.getGraphArcInList().remove((GraphArcIn) currentArc);
                            } else {
                                   // check if current GraphArcOut is opposite to one of the existed arcs 
                                for (GraphArcOut to : graphNet.getGraphArcOutList()) {

                                    if (((GraphPetriTransition) to.getBeginElement()).getId() == currEndId
                                            && ((GraphPetriPlace) to.getEndElement()).getId() == currBeginId) {

                                        if (!to.isFirstArc() && !to.isSecondArc()) { //july 2023

                                            currentArc.twoArcs(to);
                                            currentArc.updateCoordinates();
                                            break;
                                        }
                                    }

                                }
                                /* saving the action of adding an GraphArcOut for possible undoing */
                                AddArcEdit edit = new AddArcEdit(PetriNetsPanel.this, currentArc);
                                PetriNetsFrame.getUndoSupport().postEdit(edit);
                            }

                        } else { // current GraphArcOut is GraphArcOut
                            currBeginId = ((GraphPetriTransition) currentArc.getBeginElement()).getId();
                            currEndId = ((GraphPetriPlace) currentArc.getEndElement()).getId();
                            isrepeat = false;
                            for (GraphArcOut to : graphNet.getGraphArcOutList()) {    // check if GraphArcOut is created for the same place and transition

                                if ((to != currentArc)
                                        && ((GraphPetriTransition) to.getBeginElement()).getId() == currBeginId
                                        && ((GraphPetriPlace) to.getEndElement()).getId() == currEndId) {
                                    isrepeat = true;
                                    to.getArcOut().setQuantity(to.getArcOut().getQuantity() + 1);
                                    break;
                                }

                            }
                            if (isrepeat) { // new GraphArcOut is existed in the list of arcs
                                graphNet.getGraphArcOutList().remove((GraphArcOut) currentArc);
                            } else {

                                // check if current GraphArcOut is opposite to one of the existed arcs 
                                for (GraphArcIn ti : graphNet.getGraphArcInList()) {
                                    if (((GraphPetriPlace) ti.getBeginElement()).getId() == currEndId
                                            && ((GraphPetriTransition) ti.getEndElement()).getId() == currBeginId) {

                                        if (!ti.isFirstArc() && !ti.isSecondArc()) { //july 2023 to provide the oposite GraphArcOut does not exist

                                            currentArc.twoArcs(ti);
                                            currentArc.updateCoordinates();
                                            break;
                                        }
                                    }
                                }
                                /* saving the action of adding an GraphArcOut for possible undoing */
                                AddArcEdit edit = new AddArcEdit(PetriNetsPanel.this, currentArc);
                                PetriNetsFrame.getUndoSupport().postEdit(edit);
                            }
                        }

                        currentArc = null;
                        setDefaultColorGraphArcs();
                    } else {                        //1.02.2013 цей фрагмент дозволяє відслідковувати намагання
                        // Place to place, or transition to transition, is not a valid arc —
                        // and a shared place between two Petri-objects is now made by
                        // dragging between their ports instead, so this attempt is simply
                        // discarded.
                        removeCurrentArc();// з"єднати позицію з позицією чи перехід з переходом
                        //та знищувати неправильно намальовану дугу
                    }
                    current = null;
                    setDefaultColorGraphElements();
                } else {
                    removeCurrentArc();//1.02.2013;
                }
            }
            currentArc = null;
            setDefaultColorGraphArcs();
            leftMouseButtonPressed = false;
            repaint();

        }

        private void startTimer() {
            if (timer == null) {
                timer = new java.util.Timer();
            }
            timer.schedule(new TimerTask() {
                public void run() {
                    isMouseButtonHold = true;
                }
            }, 500);
        }

        private void removeTimer() {
            if (timer != null) {
                isMouseButtonHold = false;
                timer.cancel();
                timer = null;
            }
        }
    }

    /**
     * Checks whether the element that was just dragged ended up over another Petri-object's
     * frame, and if so, offers to actually join it.
     *
     * <p>Landing inside a frame's rectangle is only ever a proposal — claiming the element is
     * this method's own doing, not a side effect of where it was dropped. That is also what
     * keeps a frame's own move ({@link #moveFrame}) from doing the same thing to whatever it
     * happens to end up over: that method only ever moves elements it already owns
     * ({@link GraphObjectFrame#getMembers()}), it never looks at what else the frame's new
     * position covers.
     */
    private void confirmMoveBetweenObjects() {
        GraphElement element = draggedElement;
        GraphObjectFrame before = ownerBeforeDrag;
        Point2D origin = positionBeforeDrag;
        draggedElement = null;
        ownerBeforeDrag = null;
        positionBeforeDrag = null;

        if (element == null || origin == null || canvasModel.getFrames().isEmpty()) {
            return;
        }
        Point2D centre = element.getGraphElementCenter();
        GraphObjectFrame after = centre == null ? before : ownerForDropAt(centre);
        if (after == before) {
            return;
        }

        if (countArcsOf(element) > 0) {
            String question = "'" + element.getName() + "' has " + countArcsOf(element)
                    + " arc(s) and would move from " + describe(before) + " to " + describe(after)
                    + ". Move it to the other Petri-object?";
            if (!MessageHelper.showConfirmation(dialogOwner(), question)) {
                element.setNewCoordinates(new Point2D.Double(origin.getX(), origin.getY()));
                canvasModel.syncFusions();
                updateArcCoordinates();
                repaint();
                return;
            }
        }
        canvasModel.claim(after, element);
    }

    /**
     * @param point where an element was dropped, in canvas coordinates
     * @return the Petri-object it now belongs to: the innermost object drawn there, or the object
     *         whose canvas is active when it landed on that canvas's own empty space. Falling back
     *         to the focused object is what keeps dropping an element on its own object's canvas
     *         from freeing it from that object.
     */
    private GraphObjectFrame ownerForDropAt(Point2D point) {
        GraphObjectFrame frame = frameAt(point);
        return frame != null ? frame : focusedFrame;
    }

    /**
     * Moves every element of a multi-selection into whatever Petri-object each one landed in, with
     * one confirmation for the whole drag.
     *
     * <p>A bulk drag used to change no membership at all: the confirmation read a single
     * {@code draggedElement} that only a one-element drag ever set, so a rubber-band selection
     * dragged into an object ended up drawn inside it while still belonging to whoever held it
     * before - usually nobody.
     */
    private void confirmBulkMoveBetweenObjects() {
        Map<GraphElement, GraphObjectFrame> reparented = pendingReparenting();
        if (reparented.isEmpty()) {
            return;
        }
        String question = reparented.size() + " element(s) landed in a different Petri-object. "
                + "Move them there?";
        if (!MessageHelper.showConfirmation(dialogOwner(), question)) {
            return;
        }
        applyReparenting(reparented);
    }

    /**
     * @return every selected element that is now drawn in a different Petri-object from the one
     *         claiming it, mapped to the object it landed in ({@code null} for the free elements).
     *         Split out from the confirmation so the bulk-drag reparenting is reachable without a
     *         dialog.
     */
    public Map<GraphElement, GraphObjectFrame> pendingReparenting() {
        Map<GraphElement, GraphObjectFrame> reparented = new IdentityHashMap<>();
        for (GraphElement element : selection.elements()) {
            Point2D centre = element.getGraphElementCenter();
            if (centre == null) {
                continue;
            }
            GraphObjectFrame after = ownerForDropAt(centre);
            if (after != canvasModel.ownerOf(element)) {
                reparented.put(element, after);
            }
        }
        return reparented;
    }

    /**
     * @param reparented what {@link #pendingReparenting()} answered with
     */
    public void applyReparenting(Map<GraphElement, GraphObjectFrame> reparented) {
        for (Map.Entry<GraphElement, GraphObjectFrame> entry : reparented.entrySet()) {
            canvasModel.claim(entry.getValue(), entry.getKey());
        }
        repaint();
    }

    private static String describe(GraphObjectFrame frame) {
        return frame == null ? "the free elements" : "'" + frame.getName() + "'";
    }

    /**
     * @return how many arcs are attached to the element
     */
    private int countArcsOf(GraphElement element) {
        int count = 0;
        for (GraphArcIn arc : graphNet.getGraphArcInList()) {
            if (arc.getBeginElement() == element || arc.getEndElement() == element) {
                count++;
            }
        }
        for (GraphArcOut arc : graphNet.getGraphArcOutList()) {
            if (arc.getBeginElement() == element || arc.getEndElement() == element) {
                count++;
            }
        }
        return count;
    }

    /**
     * Completes a link dragged from {@link #draggedFromPort} to {@code targetPort} — a shared
     * place for two place ports, a crossing arc for a place and a transition port. Silently
     * cancels when the drag was not released on a port, matching how dropping the old arc
     * tool on empty space already behaves; anything that would not be a valid link is
     * reported instead of just discarded, since landing on some other port was clearly
     * intentional. {@link #finishPortDragToFreeElement} is the same completion for a drag
     * released on a free element instead of another port — a locked object reaches the free
     * part of the drawing through its port exactly the way it reaches another object.
     *
     * @param targetPort the port the drag ended on, or {@code null}
     */
    private void finishPortDrag(FramePort targetPort) {
        GraphElement source = beginFinishingPortDrag();
        if (source == null || targetPort == null || targetPort.getElement() == source) {
            return;
        }
        linkPortToElement(source, targetPort.getElement());
    }

    /**
     * @param target the free (unframed) place or transition the drag ended on, or {@code null}
     * @see #finishPortDrag(FramePort)
     */
    private void finishPortDragToFreeElement(GraphElement target) {
        GraphElement source = beginFinishingPortDrag();
        if (source == null || target == null || target == source) {
            return;
        }
        linkPortToElement(source, target);
    }

    /**
     * Reads and clears the drag-in-progress state, common to both ways a port drag can end.
     *
     * @return the port's own element the drag started from, or {@code null} if none was
     */
    private GraphElement beginFinishingPortDrag() {
        FramePort sourcePort = draggedFromPort;
        draggedFromPort = null;
        draggedPortCurrentPoint = null;
        hoveredPort = null;
        return sourcePort == null ? null : sourcePort.getElement();
    }

    /**
     * @param point a point on the canvas
     * @return the free (unframed) place or transition drawn there, or {@code null} — a port
     *         only ever reaches a framed element through that element's own port, never
     *         directly, so a framed hit here does not count
     */
    private GraphElement freeElementAt(Point2D point) {
        GraphElement element = find(point);
        return element != null && canvasModel.ownerOf(element) == null ? element : null;
    }

    /**
     * Creates the link that fits {@code source} and {@code target}: a shared place for two
     * places, a crossing arc for a place and a transition. Rejects a place-place or
     * transition-transition pairing.
     */
    private void linkPortToElement(GraphElement source, GraphElement target) {
        boolean sourceIsPlace = source instanceof GraphPetriPlace;
        boolean targetIsPlace = target instanceof GraphPetriPlace;
        try {
            if (sourceIsPlace && targetIsPlace) {
                canvasModel.joinPlaces((GraphPetriPlace) source, (GraphPetriPlace) target);
            } else if (!sourceIsPlace && !targetIsPlace) {
                throw new IllegalArgumentException(
                        "A transition cannot connect directly to another transition");
            } else if (sourceIsPlace) {
                // place -> transition: the place becomes an extra input of the transition
                canvasModel.getNet().getGraphArcInList().add(GraphArcFactory.inArc(
                        (GraphPetriPlace) source, (GraphPetriTransition) target, 1, false));
            } else {
                // transition -> place: the transition delivers tokens into the place
                canvasModel.getNet().getGraphArcOutList().add(GraphArcFactory.outArc(
                        (GraphPetriTransition) source, (GraphPetriPlace) target, 1));
            }
        } catch (IllegalArgumentException rejected) {
            MessageHelper.showError(dialogOwner(), rejected.getMessage());
        }
    }

    /**
     * Separates a shared place back into two places of their own.
     *
     * @param fusion the shared place to split
     */
    public void splitSharedPlace(GraphPlaceFusion fusion) {
        canvasModel.getFusions().remove(fusion);
        Point2D centre = fusion.getJoined().getGraphElementCenter();
        fusion.getJoined().setNewCoordinates(
                new Point2D.Double(centre.getX() + 60, centre.getY() + 40));
        updateArcCoordinates();
        repaint();
    }

    /**
     * @param point a point on the canvas
     * @return the shared place whose ring is under the point, or {@code null}
     */
    public GraphPlaceFusion findSharedPlace(Point2D point) {
        for (GraphPlaceFusion fusion : canvasModel.getFusions()) {
            if (fusion.isOnRing(point)) {
                return fusion;
            }
        }
        return null;
    }

    private void removeCurrentArc() { //1.02.2013 цей метод дозволяє знищувати намальовану дугу
        if (currentArc.getClass().equals(GraphArcIn.class)) // 
        {
            graphNet.getGraphArcInList().remove((GraphArcIn) currentArc);
        } else if (currentArc.getClass().equals(GraphArcOut.class)) {
            graphNet.getGraphArcOutList().remove((GraphArcOut) currentArc);
        } else ;
        currentArc = null;

        repaint();
    }

    private class MouseMotionHandler implements MouseMotionListener {

        @Override
        public void mouseDragged(MouseEvent ev) {
            if (tool == CanvasTool.PAN || selectToolPanning) {
                updatePan(ev.getPoint());
                return;
            }
            if (tool == CanvasTool.DELETE || tool == CanvasTool.ADD_PLACE
                    || tool == CanvasTool.ADD_TRANSITION || tool == CanvasTool.ADD_PETRI_OBJECT
                    || tool == CanvasTool.PLACE_LOADED_NET) {
                return;
            }

            dragCompleted = true;
            Point scaledCurrentMousePoint = new Point((int) (ev.getX() / scale), (int) (ev.getY() / scale));

            if (draggedFromPort != null) {
                draggedPortCurrentPoint = scaledCurrentMousePoint;
                hoveredPort = portOnCanvasAt(scaledCurrentMousePoint);
                repaint();
                return;
            }

            if (resizedFrame != null) {
                resizedFrame.resizeTo(scaledCurrentMousePoint.x, scaledCurrentMousePoint.y);
                repaint();
                return;
            }
            if (draggedFrame != null) {
                moveFrame(draggedFrame,
                        scaledCurrentMousePoint.x - frameDragOffset.x,
                        scaledCurrentMousePoint.y - frameDragOffset.y);
                repaint();
                return;
            }

            if (choosen == null && selection.elements().isEmpty()) {
                PetriNetsPanel.this.setDefaultColorGraphElements();
                currentDragMouseLocation = scaledCurrentMousePoint;
            }
            if (current != null && currentArc == null) {  // moving place or transition

                current.setColor(Color.BLUE);
                PetriNetsPanel.this.setDefaultColorGraphArcs(); //26.07.2018

                current.setNewCoordinates(scaledCurrentMousePoint);
                for (GraphArcIn ti : graphNet.getGraphArcInList()) {
                    ti.updateCoordinates();
                }
                for (GraphArcOut to : graphNet.getGraphArcOutList()) {
                    to.updateCoordinates();
                }
            }

            if (currentArc != null && current != null) { //creating the GraphArcOut
                currentArc.setColor(Color.BLUE);
                current.setColor(Color.BLUE);
                currentArc.setNewCoordinates(scaledCurrentMousePoint);
            }

            if (!selection.isEmpty() && leftMouseButtonPressed) { // moving the whole selection

                selection.paintHighlight();
                setCursor(new Cursor(Cursor.MOVE_CURSOR));
                // One operation for both kinds of selected thing: elements move directly, objects
                // move with their whole subtree. Frames used to be left behind entirely.
                moveSelectionBy(
                        (int) scaledCurrentMousePoint.getX() - prevMouseLocation.x,
                        (int) scaledCurrentMousePoint.getY() - prevMouseLocation.y);
                selectionDragged = true;
                prevMouseLocation = scaledCurrentMousePoint;
            }
            repaint();
        }

        @Override
        public void mouseMoved(MouseEvent ev) {
            if (tool == CanvasTool.PLACE_LOADED_NET) {
                // The one tool that does care where the pointer is without a button held: the
                // outline has to follow it, so this cannot take the early return below.
                placementPoint = new Point((int) (ev.getX() / scale), (int) (ev.getY() / scale));
                repaint();
                return;
            }

            if (tool == CanvasTool.PAN || tool == CanvasTool.DELETE
                    || tool == CanvasTool.ADD_PLACE || tool == CanvasTool.ADD_TRANSITION
                    || tool == CanvasTool.ADD_PETRI_OBJECT) {
                // These tools keep their own dedicated cursor regardless of what is underneath
                // the pointer — port hovering is a Select-tool affordance only.
                return;
            }
            Point scaledCurrentMousePoint = new Point((int) (ev.getX() / scale), (int) (ev.getY() / scale));
            FramePort hovered = portOnCanvasAt(scaledCurrentMousePoint);
            if (hovered != hoveredPort) {
                hoveredPort = hovered;
                setCursor(hovered != null ? new Cursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                repaint();
            }
            if (current != null && currentArc == null) {
                current.setColor(Color.BLUE);
                PetriNetsPanel.this.setDefaultColorGraphArcs();
                setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
                current.setNewCoordinates(scaledCurrentMousePoint);
                repaint();
            }
        }
    }

    public GraphElement getCurrent() {
        return current;
    }

    public void setCurrent(GraphElement e) {
        current = e;
    }

    public GraphElement getChoosen() {
        return choosen;
    }

    public void setChoosen(GraphElement chosen) {
        this.choosen = chosen;
    }

    public void setCurrentGraphArc(GraphArc t) {
        currentArc = t;
    }

    public GraphArc getCurrentGraphArc() {
        return currentArc;
    }

    public GraphArc getChoosenArc() {
        return choosenArc;
    }

    public void setChoosenArc(GraphArc arc) {
        this.choosenArc = arc;
    }

    public int getSavedId() {
        return savedId;
    }

    public void saveId() {
        this.savedId = id;
    }

    public static String getPetriTName() {
        return "T" + id;
    }

    public static String getPetriPName() {
        return "P" + id;
    }

    public void setIsSettingArc(boolean b) { //26.01.2013
        if (b) {
            setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
        } else {
            setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        }
        isSettingArc = b;
    }

    public CanvasTool getTool() {
        return tool;
    }

    /**
     * Switches which gesture a left-click-drag performs. Whatever the previous tool left
     * mid-flight — a selection, an arc half-drawn to its first endpoint — is abandoned first,
     * since none of that state means the same thing under a different tool.
     *
     * @param newTool the tool to activate
     */
    public void setTool(CanvasTool newTool) {
        setTool(newTool, null);
    }

    /**
     * Switches tool, arming a Petri-object template at the same time.
     *
     * @param newTool the tool to activate
     * @param template the template every click should stamp while {@link
     *        CanvasTool#ADD_PETRI_OBJECT} is active, or {@code null} for every other tool
     */
    public void setTool(CanvasTool newTool, PetriObjectTemplate template) {
        // Every template shares the one ADD_PETRI_OBJECT mode, so "same tool" is not the same
        // question as "nothing to do" any more: switching straight from one template's button
        // to another has to re-arm even though the tool itself did not change.
        if (tool == newTool && java.util.Objects.equals(armedTemplate, template)) {
            return;
        }
        if (currentArc != null) {
            removeCurrentArc();
        }
        isSettingArc = false;
        clearSelectionState();
        if (newTool != CanvasTool.PLACE_LOADED_NET) {
            // Switching away abandons a net that was waiting to be placed — otherwise its
            // outline would keep tracking the pointer under a tool that cannot commit it.
            cancelPendingNet();
        }
        tool = newTool;
        armedTemplate = template;
        setCursor(cursorFor(newTool));
        repaint();
    }

    private void clearSelectionState() {
        setDefaultColorGraphElements();
        setDefaultColorGraphArcs();
        current = null;
        choosen = null;
        choosenArc = null;
        selection.clear();
        startDragMouseLocation = null;
        currentDragMouseLocation = null;
        leftMouseButtonPressed = false;
        selectionDragged = false;
        dragCompleted = false;
    }

    private static Cursor cursorFor(CanvasTool t) {
        switch (t) {
            case PAN:
                return new Cursor(Cursor.HAND_CURSOR);
            case DELETE:
                return ERASER_CURSOR;
            case ADD_PLACE:
            case ADD_TRANSITION:
            case ADD_PETRI_OBJECT:
            case PLACE_LOADED_NET:
                return new Cursor(Cursor.CROSSHAIR_CURSOR);
            default:
                return Cursor.getDefaultCursor();
        }
    }

    private static Cursor buildEraserCursor() {
        try {
            URL url = ResourcePathConfig.getResource(PetriNetsPanel.class,
                    ResourcePathConfig.getIconPath(ResourcePathConfig.ERASER_CURSOR));
            if (url == null) {
                return new Cursor(Cursor.CROSSHAIR_CURSOR);
            }
            Image image = Toolkit.getDefaultToolkit().getImage(url);
            return Toolkit.getDefaultToolkit().createCustomCursor(
                    image, new Point(0, 0), "eraser");
        } catch (RuntimeException problem) {
            return new Cursor(Cursor.CROSSHAIR_CURSOR);
        }
    }

    /**
     * Drops a new place or transition at the click point — what the Add Place / Add Transition
     * tool does on every click, staying active so the next click drops another one instead of
     * having to reselect the tool each time.
     *
     * @param addTool {@link CanvasTool#ADD_PLACE} or {@link CanvasTool#ADD_TRANSITION}
     * @param scaledPoint where the new element is centred, in canvas coordinates
     */
    private void addElementAt(CanvasTool addTool, Point scaledPoint) {
        GraphElement element = addTool == CanvasTool.ADD_PLACE
                ? new GraphPetriPlace(new PetriP(GraphPetriPlace.setSimpleName(), 0), getIdElement())
                : new GraphPetriTransition(new PetriT(GraphPetriTransition.setSimpleName(), 0.0), getIdElement());
        element.setNewCoordinates(scaledPoint);

        // Drawn on an object's canvas means drawn into that object: the edit carries the owner so
        // redo claims it again, and its undo releases it.
        AddGraphElementEdit edit = new AddGraphElementEdit(this, element, focusedFrame);
        edit.doFirstTime();
        PetriNetsFrame.getUndoSupport().postEdit(edit);
        repaint();
    }

    /**
     * Deletes one element and whatever arcs touch it — the same rule the Delete key already
     * applies to a single selected element, factored out so the Delete tool's mouse click can
     * do the same thing without going through key focus at all.
     *
     * @param element the element to remove
     */
    private void deleteElement(GraphElement element) {
        try {
            List<GraphArcIn> inArcsToBeRemoved = new ArrayList<>();
            List<GraphArcOut> outArcsToBeRemoved = new ArrayList<>();

            for (GraphArcIn arc : getGraphNet().getGraphArcInList()) {
                if (arc.getBeginElement() == element || arc.getEndElement() == element) {
                    if (!inArcsToBeRemoved.contains(arc)) {
                        inArcsToBeRemoved.add(arc);
                    }
                }
            }
            for (GraphArcOut arc : getGraphNet().getGraphArcOutList()) {
                if (arc.getBeginElement() == element || arc.getEndElement() == element) {
                    if (!outArcsToBeRemoved.contains(arc)) {
                        outArcsToBeRemoved.add(arc);
                    }
                }
            }

            DeleteGraphElementsEdit edit = new DeleteGraphElementsEdit(this, element,
                    inArcsToBeRemoved, outArcsToBeRemoved);
            // Read before remove(), which releases the element: this is the last moment the
            // answer exists, and undo needs it to put the element back into its own object.
            edit.rememberOwner(element, canvasModel.ownerOf(element));
            remove(element);
            PetriNetsFrame.getUndoSupport().postEdit(edit);
        } catch (ExceptionInvalidNetStructure ex) {
            LOGGER.error("Unexpected error", ex);
        }
    }

    /**
     * Handles a click made with the Delete tool active: removes whatever single element or
     * arc is under the pointer, or does nothing on empty canvas.
     *
     * <p>A whole Petri-object is erasable too, with the same confirmation its own context menu
     * asks: the eraser reaching everything on the canvas except the objects was one of the places
     * where an operation written for elements silently skipped the other kind of thing the canvas
     * holds. A port, or an element belonging to an object whose canvas is not the active one, is
     * still left alone - that boundary is what {@code frameAt} enforces before {@link #find} is
     * ever consulted.
     *
     * @param scaledPoint the click point in canvas coordinates
     */
    private void handleDeleteClick(Point scaledPoint) {
        if (portOnCanvasAt(scaledPoint) != null) {
            return;
        }
        GraphObjectFrame frame = frameAt(scaledPoint);
        if (frame != null) {
            confirmRemoveObjectFrame(frame);
            return;
        }
        GraphElement element = find(scaledPoint);
        if (element != null && isOnThisCanvas(element)) {
            deleteElement(element);
            if (choosen == element) {
                choosen = null;
            }
            if (current == element) {
                current = null;
            }
            repaint();
            return;
        }
        GraphArc arc = findArc(scaledPoint);
        if (arc != null) {
            removeArc(arc);
            DeleteArcEdit edit = new DeleteArcEdit(this, arc);
            PetriNetsFrame.getUndoSupport().postEdit(edit);
            if (choosenArc == arc) {
                choosenArc = null;
            }
            repaint();
        }
    }

    /**
     * Starts panning: remembers the drag's origin and the viewport's scroll position so
     * {@link #updatePan} can compute an absolute offset rather than drifting on rounding.
     *
     * @param screenPoint the raw (unscaled) point the drag started at
     */
    private void beginPan(Point screenPoint) {
        panViewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
        if (panViewport == null) {
            return;
        }
        panDragOrigin = screenPoint;
        panViewportOrigin = panViewport.getViewPosition();
    }

    private void updatePan(Point screenPoint) {
        if (panViewport == null || panDragOrigin == null) {
            return;
        }
        int maxX = Math.max(0, panViewport.getViewSize().width - panViewport.getExtentSize().width);
        int maxY = Math.max(0, panViewport.getViewSize().height - panViewport.getExtentSize().height);
        int newX = panViewportOrigin.x - (screenPoint.x - panDragOrigin.x);
        int newY = panViewportOrigin.y - (screenPoint.y - panDragOrigin.y);
        panViewport.setViewPosition(new Point(
                Math.max(0, Math.min(newX, maxX)),
                Math.max(0, Math.min(newY, maxY))));
    }

    private void endPan() {
        panViewport = null;
        panDragOrigin = null;
        panViewportOrigin = null;
    }

    /**
     * Empties this panel's own drawing and clears its transient selection state — safe to call
     * from anywhere, including this panel's own constructor, since none of it is shared with
     * any other {@code PetriNetsPanel}.
     */
    private void resetOwnState() {
        current = null;
        currentArc = null;
        choosen = null;
        choosenArc = null;
        setCanvasNet(new GraphPetriNet());
        canvasModel.getFrames().clear();
        canvasModel.getFusions().clear();
        selection.clear();
        focusedFrame = null;
        canvasStack.reset();
        repaint();
    }

    /**
     * Starts an entirely new document: this panel's own state (see {@link #resetOwnState()}),
     * plus every numbering counter a freshly created place, transition or arc anywhere in the
     * application draws its id or number from.
     *
     * <p>Resetting those counters is safe only when the user has deliberately asked for a fresh
     * start — {@code File → New} is the one caller — never merely because a new
     * {@code PetriNetsPanel} was constructed: a Petri-object's own editor is a
     * {@code PetriNetsPanel} too, built and torn down every time it is opened, and it must
     * never renumber elements the main canvas, or any other still-open object's own editor,
     * already depends on. That used to happen here, and was the actual cause of arcs drawn
     * inside an object's editor being matched — and their weight incremented — against a
     * completely unrelated, pre-existing arc elsewhere: the new arc's ends collided in id with
     * that other arc's, purely because the ids had just been counted from zero again.
     */
    public final void setNullPanel() {
        resetOwnState();

        id = 0;
        GraphElementIdGenerator.reset();
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext(); //додано Інна 20.11.2012
        ArcOut.initNext(); //додано Інна 20.11.2012
        GraphPetriPlace.setNullSimpleName();
        GraphPetriTransition.setNullSimpleName();
    }

    /**
     * Enable drag and drop for both PNML and PNS files using a unified handler.
     * This method should be preferred over enablePnmlDragAndDrop() and enablePnsDragAndDrop()
     * to avoid conflicts between multiple DropTargets.
     *
     * @param parentFrame parent frame for dialogs
     */
    public void enableDragAndDrop(JFrame parentFrame) {
        UnifiedDropHandler dropHandler = new UnifiedDropHandler(this, parentFrame);
        new DropTarget(this, dropHandler);
    }

    /**
     * Enable drag and drop for PNML files only.
     * @deprecated Use {@link #enableDragAndDrop(JFrame)} instead to support multiple file formats.
     *
     * @param parentFrame parent frame for dialogs
     */
    @Deprecated
    public void enablePnmlDragAndDrop(JFrame parentFrame) {
        PnmlDropHandler dropHandler = new PnmlDropHandler(this, parentFrame);
        new DropTarget(this, dropHandler);
    }

    /**
     * Enable drag and drop for PNS files only.
     * @deprecated Use {@link #enableDragAndDrop(JFrame)} instead to support multiple file formats.
     *
     * @param parentFrame parent frame for dialogs
     */
    @Deprecated
    public void enablePnsDragAndDrop(JFrame parentFrame) {
        PnsDropHandler dropHandler = new PnsDropHandler(this, parentFrame);
        new DropTarget(this, dropHandler);
    }

    /**
     * Adds a whole Petri-object model to the canvas, the way {@link #addGraphNet} adds a plain
     * net: the drawing is merged into whatever is already there and the frames that mark out its
     * objects come with it, rather than replacing the canvas.
     *
     * <p>This is the path opening a saved file takes when that file has objects in it. A file
     * written before objects were persisted holds a bare net and still goes to
     * {@link #addGraphNet}, which is why both exist.
     *
     * @param model the canvas document to add, with its frames and shared places
     */
    public void addCanvasModel(GraphCanvasModel model) {
        addGraphNet(model.getNet());
        canvasModel.getFrames().addAll(model.getFrames());
        canvasModel.getFusions().addAll(model.getFusions());
        canvasModel.syncFusions();
        updateArcCoordinates();
        repaint();
    }

    public void addGraphNet(GraphPetriNet net) {
        // If there's no existing net, just set the new one
        if (graphNet == null) {
            setCanvasNet(net);
        } else {
            // Merge the new net into the existing one
            graphNet.mergeGraphNet(net);
        }
        protectPetriNumbering();

        int maxIdPetriNet = 0; //
        for (GraphPetriPlace pp : graphNet.getGraphPetriPlaceList()) {  //відшукуємо найбільший id для позицій
            if (maxIdPetriNet < pp.getId()) {
                maxIdPetriNet = pp.getId();
            }
        }
        for (GraphPetriTransition pt : graphNet.getGraphPetriTransitionList()) { //відшукуємо найбільший id для переходів і позицій
            if (maxIdPetriNet < pt.getId()) {
                maxIdPetriNet = pt.getId();
            }
        }
        if (maxIdPetriNet > id) // встановлюємо новий id - найбільший
        {
            id = maxIdPetriNet;
        }
        id++;
        GraphElementIdGenerator.ensureAtLeast(id);

        repaint();
    }

    public void deletePetriNet() {
        graphNet = null;
        canvasModel.getFrames().clear();
        canvasModel.getFusions().clear();
        selection.clear();
        focusedFrame = null;
        canvasStack.reset();
        repaint();
    }

    public GraphPetriNet getGraphNet() {
        return graphNet;
    }

    /**
     * Points both the drawing and the canvas model at the same net, so the Petri-object
     * frames always mark out regions of what is actually on screen.
     */
    private void setCanvasNet(GraphPetriNet net) {
        graphNet = net;
        canvasModel.setNet(net);
    }

    /**
     * @return the canvas read as a Petri-object model: the drawing, the frames that mark out
     *         its objects, and the places shared between them
     */
    public GraphCanvasModel getCanvasModel() {
        return canvasModel;
    }

    /**
     * Shows a whole Petri-object model on the canvas, replacing what was there.
     *
     * @param model the canvas document to display, with its drawing, frames and shared places
     */
    public void setCanvasModel(GraphCanvasModel model) {
        setCanvasNet(model.getNet());
        canvasModel.setName(model.getName());
        canvasModel.getFrames().clear();
        canvasModel.getFrames().addAll(model.getFrames());
        canvasModel.getFusions().clear();
        canvasModel.getFusions().addAll(model.getFusions());
        choosen = null;
        current = null;
        selection.clear();
        // Every open canvas was holding a frame from the document being replaced, and the frames
        // arriving now are different instances - so the strip goes back to the net alone rather
        // than showing pills for objects that are no longer anywhere.
        focusedFrame = null;
        canvasStack.reset();
        canvasStack.notifyChanged();
        canvasModel.syncFusions();
        updateArcCoordinates();
        repaint();
    }

    /**
     * @return the frame the user has selected, or {@code null}
     */
    public GraphObjectFrame getSelectedFrame() {
        return selection.getSelectedFrame();
    }

    public void setSelectedFrame(GraphObjectFrame frame) {
        selection.setSelectedFrame(frame);
        repaint();
    }

    /**
     * Adds a Petri-object frame, selects it, and records the creation as one undo step.
     *
     * <p>Creating an object used to post no undoable edit at all, which was survivable while an
     * object's net lived behind a modal window with its own Cancel. With editing in place there is
     * no Cancel anywhere, so Ctrl+Z has to reach object creation too.
     *
     * @param frame the frame to add
     */
    public void addObjectFrame(GraphObjectFrame frame) {
        canvasModel.getFrames().add(frame);
        selection.setSelectedFrame(frame);
        PetriNetsFrame.getUndoSupport().postEdit(new AddObjectFrameEdit(this, frame));
        canvasStack.notifyChanged();
        repaint();
    }

    /**
     * Removes a Petri-object frame, as one undo step. What it held moves one level out: its
     * elements to the object that enclosed it, or to the free elements, and the objects nested
     * inside it onto that same enclosing object.
     *
     * @param frame the frame to remove
     */
    public void removeObjectFrame(GraphObjectFrame frame) {
        if (!canvasModel.getFrames().contains(frame)) {
            return;
        }
        ObjectFrameSnapshot snapshot = new ObjectFrameSnapshot(canvasModel, frame);
        removeObjectFrameSilently(frame);
        PetriNetsFrame.getUndoSupport().postEdit(new RemoveObjectFrameEdit(this, snapshot));
    }

    /**
     * Takes a frame off the canvas without recording an undo step - what the undo of a creation
     * and the redo of a removal both do, so replaying an edit never posts another one.
     *
     * @param frame the frame to remove
     */
    public void removeObjectFrameSilently(GraphObjectFrame frame) {
        canvasModel.getFrames().remove(frame);
        canvasModel.releaseMembers(frame);
        selection.remove(frame);
        // Eager, where the web editor is lazy about an orphaned tab: a pill here holds the live
        // frame, so a canvas whose frame is gone cannot be painted at all.
        canvasStack.pruneRemoved(frame);
        if (focusedFrame == frame || !canvasModel.getFrames().contains(focusedFrame)) {
            focusedFrame = canvasStack.getActive();
        }
        canvasStack.notifyChanged();
        repaint();
    }

    /**
     * Puts a frame back exactly as it was - same position in the flat frame list, same enclosing
     * object, same membership, same nested objects - without recording an undo step.
     *
     * @param snapshot the frame's state, read while it was still on the canvas
     */
    public void reinstateObjectFrame(ObjectFrameSnapshot snapshot) {
        GraphObjectFrame frame = snapshot.getFrame();
        if (canvasModel.getFrames().contains(frame)) {
            return;
        }
        int index = Math.min(Math.max(0, snapshot.getIndex()), canvasModel.getFrames().size());
        canvasModel.getFrames().add(index, frame);
        canvasModel.nest(frame, snapshot.getEnclosing());
        for (GraphElement member : snapshot.getMembers()) {
            canvasModel.claim(frame, member);
        }
        for (GraphObjectFrame child : snapshot.getChildren()) {
            if (canvasModel.getFrames().contains(child)) {
                canvasModel.nest(child, frame);
            }
        }
        canvasStack.notifyChanged();
        repaint();
    }

    /**
     * @param frame a Petri-object frame
     * @return how many places and transitions it holds, counting everything nested inside it -
     *         from outside, a nest is one object, and this is the count its collapsed summary box
     *         reports. Identical to its own member count for an object with nothing nested in it.
     */
    public int countElementsIn(GraphObjectFrame frame) {
        return canvasModel.membersOfSubtree(frame).size();
    }

    /**
     * @param point a point on the canvas
     * @return the innermost Petri-object frame the active canvas draws at that point, or
     *         {@code null}. The object being edited is excluded: its rectangle is the room the
     *         user is standing in, so a click inside it has to reach its net rather than select
     *         the room. Deeper nesting wins, the same tie-break {@code ownerOf} uses.
     */
    private GraphObjectFrame frameAt(Point2D point) {
        return topmostFrame(point, GraphObjectFrame::contains);
    }

    /**
     * @param point a point on the canvas
     * @return the frame whose header is under the point, or {@code null}. The focused object's own
     *         header is inert on its own canvas: dragging it there would move the room around the
     *         net inside it, which is what its own canvas exists to edit.
     */
    private GraphObjectFrame frameHeaderAt(Point2D point) {
        return topmostFrame(point, GraphObjectFrame::isOnHeader);
    }

    /**
     * @param point a point on the canvas
     * @return the frame whose resize handle is under the point, or {@code null}; inert for the
     *         focused object, for the same reason as its header
     */
    private GraphObjectFrame frameHandleAt(Point2D point) {
        return topmostFrame(point, GraphObjectFrame::isOnResizeHandle);
    }

    /**
     * @param point a point on the canvas
     * @return the frame whose eye icon is under the point, or {@code null}
     */
    private GraphObjectFrame frameEyeIconAt(Point2D point) {
        return topmostFrame(point, GraphObjectFrame::isOnEyeIcon);
    }

    /**
     * The one frame hit test every gesture goes through: among the frames the active canvas
     * actually draws, the deepest one whose given part is under the point, later in canvas order
     * breaking a tie at the same depth.
     *
     * @param point a point on the canvas
     * @param part which part of a frame to test - its whole rectangle, its header, its handle
     * @return the frame, or {@code null}
     */
    private GraphObjectFrame topmostFrame(Point2D point,
            java.util.function.BiPredicate<GraphObjectFrame, Point2D> part) {
        GraphObjectFrame best = null;
        int bestLevel = -1;
        for (GraphObjectFrame frame : canvasModel.getFrames()) {
            if (frame == focusedFrame || !isFrameDrawnOnThisCanvas(frame) || !part.test(frame, point)) {
                continue;
            }
            int level = canvasModel.levelOf(frame);
            if (level >= bestLevel) {
                best = frame;
                bestLevel = level;
            }
        }
        return best;
    }

    /**
     * @param point a point on the canvas
     * @return the port under that point, across the frames the active canvas draws, or
     *         {@code null}. A port whose own object is not on this canvas is unreachable, so a
     *         press on empty screen space cannot start a link from an element that is not drawn.
     *         The focused object has no reachable ports at all: its own canvas draws neither its
     *         border nor the circles on it, and a hit target nobody can see is worse than one
     *         that is not there.
     */
    private FramePort portOnCanvasAt(Point2D point) {
        for (GraphObjectFrame frame : canvasModel.getFrames()) {
            if (frame == focusedFrame || !isFrameDrawnOnThisCanvas(frame)) {
                continue;
            }
            boolean contentShown = !isContentHidden(frame);
            for (FramePort port : canvasModel.portsOf(frame)) {
                // While the object's content is on screen, a point on the real element resolves to
                // its port as well: the circle is not drawn there in that case, but the port is
                // still exactly what a link from it should be.
                if (port.isNear(point)
                        || (contentShown && isDrawnOnThisCanvas(port.getElement())
                                && port.getElement().isGraphElement(point))) {
                    return port;
                }
            }
        }
        return null;
    }

    /**
     * Moves a frame together with everything it claims and everything nested inside it, so an
     * object's net always stays inside it - its elements are fixed relative to the frame, only
     * repositioned individually on the object's own canvas.
     *
     * <p>The delta applied to the elements is measured from the frame's own bounds after
     * {@link GraphObjectFrame#moveTo} runs, not before it: {@code moveTo} clamps its target to
     * stay on the canvas, so measuring the delta from the raw, unclamped target (as this used
     * to) let the elements keep moving by the full amount while the frame itself stopped dead
     * at the edge — they drifted away from it a little further with every such drag. Moving the
     * frame first and reading back where it actually ended up removes that mismatch outright.
     */
    private void moveFrame(GraphObjectFrame frame, int x, int y) {
        int beforeX = frame.getBounds().x;
        int beforeY = frame.getBounds().y;
        frame.moveTo(x, y);
        int dx = frame.getBounds().x - beforeX;
        int dy = frame.getBounds().y - beforeY;
        if (dx == 0 && dy == 0) {
            return;
        }
        for (GraphObjectFrame nested : canvasModel.subtreeOf(frame)) {
            if (nested != frame) {
                nested.moveTo(nested.getBounds().x + dx, nested.getBounds().y + dy);
            }
        }
        for (GraphElement element : canvasModel.membersOfSubtree(frame)) {
            Point2D centre = element.getGraphElementCenter();
            element.setNewCoordinates(new Point2D.Double(centre.getX() + dx, centre.getY() + dy));
        }
        canvasModel.syncFusions();
        updateArcCoordinates();
    }

    /**
     * Slides the whole document so the net's centroid lands on a point, keeping every
     * Petri-object frame around its own net.
     *
     * <p>"Locate net in center" used to call {@code GraphPetriNet.changeLocation} directly, which
     * has no notion of a frame: every object's net slid out from under its own frame by the same
     * offset, and the frames stayed where they were.
     *
     * @param centre where the net's centroid should end up, in canvas coordinates
     */
    public void centreCanvasAt(Point centre) {
        if (graphNet == null) {
            return;
        }
        Point before = graphNet.getCurrentLocation();
        graphNet.changeLocation(centre);
        Point after = graphNet.getCurrentLocation();
        int dx = after.x - before.x;
        int dy = after.y - before.y;
        for (GraphObjectFrame frame : canvasModel.getFrames()) {
            // moveTo only, not moveFrame: the elements have already moved with the net, so moving
            // them again with their frame would double the offset.
            frame.moveTo(frame.getBounds().x + dx, frame.getBounds().y + dy);
        }
        canvasModel.syncFusions();
        updateArcCoordinates();
        repaint();
    }

    private void updateArcCoordinates() {
        for (GraphArcIn arc : graphNet.getGraphArcInList()) {
            arc.updateCoordinates();
        }
        for (GraphArcOut arc : graphNet.getGraphArcOutList()) {
            arc.updateCoordinates();
        }
    }

    public void setGraphNet(GraphPetriNet net) { //коректно працює тільки якщо потім не змінювати граф
        //рекомендується використовувати addGraphNet
        setCanvasNet(net);
        repaint();
    }

    /*
    public List<GraphPetriNet> getGraphNetList() {  //11.01.13
        return graphNetList;
    }

    public GraphPetriNet getLastGraphNetList() {  //11.01.13
        return graphNetList.get(graphNetList.size() - 1);
    }
     */
    public static int getIdElement() {  //edited by Inna 1.10.2018
        return GraphElementIdGenerator.next();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setBackground(new java.awt.Color(229, 229, 229));
        setPreferredSize(new java.awt.Dimension(20000, 20000));
        setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables

    /**
     * @param tr a firing transition from a running simulation
     * @param scope the firing object's own graphical net, so a place or transition number is
     *        looked up only within it — every object's own net is renumbered from zero
     *        independently right before a run, so the same number can belong to a different
     *        element in each one; searching the whole canvas by number, as this used to,
     *        could match — and so animate — the wrong object's elements once a canvas held
     *        more than one. {@code null} searches the whole canvas, correct only when there is
     *        no Petri-object split to begin with.
     * @return the one canvas transition {@code tr} corresponds to, or {@code null} if it
     *         cannot be found
     */
    private GraphPetriTransition transitionInScope(PetriT tr, GraphPetriNet scope) {
        List<GraphPetriTransition> searchList =
                scope != null ? scope.getGraphPetriTransitionList() : graphNet.getGraphPetriTransitionList();
        for (GraphPetriTransition t : searchList) {
            if (t.getPetriTransition().getNumber() == tr.getNumber()) {
                return t;
            }
        }
        return null;
    }

    public void animateIn(PetriT tr, GraphPetriNet scope) {    //Саша 05.17
        List<GraphArcIn> searchList = scope != null ? scope.getGraphArcInList() : graphNet.getGraphArcInList();
        ArrayList<GraphArcIn> list = new ArrayList<>();
        for (GraphArcIn t : searchList) {
            if (t.getArcIn().getNumT() == tr.getNumber()) {
                list.add(t);
            }
        }
        animArcIn(list, 100, 3, new Color(255, 77, 77));
        animArcIn(list, 100, 5);
        animArcIn(list, 100, 7);
        animArcIn(list, 100, 5);
        animArcIn(list, 100, 3);
        animArcIn(list, 100, 1, Color.BLACK);
        animateCrossings(transitionInScope(tr, scope), true);
    }

    public void animateT(PetriT tr, GraphPetriNet scope) {   //Саша 05.17
        GraphPetriTransition transition = transitionInScope(tr, scope);
        ArrayList<GraphPetriTransition> list = new ArrayList<>();
        if (transition != null) {
            list.add(transition);
            setActiveAnimationFrame(canvasModel.ownerOf(transition));
        }
        animTransitions(list, 100, 7, new Color(255, 77, 77));
        animTransitions(list, 100, 10);
        animTransitions(list, 100, 12);
        animTransitions(list, 100, 10);
        animTransitions(list, 100, 7);
        animTransitions(list, 100, 5, Color.BLACK);

    }

    public void animateP(ArrayList<Integer> inP, GraphPetriNet scope) {  //Саша 05.17
        List<GraphPetriPlace> searchList =
                scope != null ? scope.getGraphPetriPlaceList() : graphNet.getGraphPetriPlaceList();
        ArrayList<GraphPetriPlace> list = new ArrayList<>();
        for (GraphPetriPlace p : searchList) {
            for (Integer inp : inP) {
                if (p.getPetriPlace().getNumber() == inp) {
                    list.add(p);
                }
            }
        }
        animPlaces(list, 100, 5, new Color(255, 77, 77));
        animPlaces(list, 100, 7);
        animPlaces(list, 100, 10);
        animPlaces(list, 100, 7);
        animPlaces(list, 100, 5);
        animPlaces(list, 100, 2, Color.BLACK);
    }

    public void animateOut(PetriT eventMin, GraphPetriNet scope) {   //Саша 05.17
        List<GraphArcOut> searchList = scope != null ? scope.getGraphArcOutList() : graphNet.getGraphArcOutList();
        ArrayList<GraphArcOut> list = new ArrayList<>();
        for (GraphArcOut t : searchList) {
            if (t.getArcOut().getNumT() == eventMin.getNumber()) {
                list.add(t);
            }
        }
        animArcOut(list, 50, 3, new Color(255, 77, 77));
        animArcOut(list, 50, 5);
        animArcOut(list, 50, 7);
        animArcOut(list, 50, 5);
        animArcOut(list, 50, 3);
        animArcOut(list, 50, 1, Color.BLACK);
        animateCrossings(transitionInScope(eventMin, scope), false);
    }

    /**
     * Briefly flags any link a just-fired transition has crossing to another object's own
     * element, or to a free one. {@link #animateIn}/{@link #animateOut}'s own, object-scoped
     * animation never sees this: a crossing link was never part of any one object's own net to
     * begin with ({@link GraphCanvasModel#toObjModel()} turns it into a link, not an arc of
     * either object), so without this, a token moving along it would animate nothing at all.
     *
     * @param transition the transition that just took part in a firing, or {@code null} if it
     *        could not be found — nothing to do, then
     * @param incoming true to look at its inputs, false for its outputs
     */
    private void animateCrossings(GraphPetriTransition transition, boolean incoming) {
        if (transition == null) {
            return;
        }
        GraphObjectFrame ownFrame = canvasModel.ownerOf(transition);
        if (incoming) {
            for (GraphArcIn arc : graphNet.getGraphArcInList()) {
                if (arc.getEndElement() == transition) {
                    animateCrossing(arc.getBeginElement(), ownFrame);
                }
            }
        } else {
            for (GraphArcOut arc : graphNet.getGraphArcOutList()) {
                if (arc.getBeginElement() == transition) {
                    animateCrossing(arc.getEndElement(), ownFrame);
                }
            }
        }
    }

    /**
     * @param otherEnd the place at the far end of one of the firing transition's arcs
     * @param ownFrame the firing transition's own frame, or {@code null} if it is free
     */
    private void animateCrossing(GraphElement otherEnd, GraphObjectFrame ownFrame) {
        GraphObjectFrame otherOwner = canvasModel.ownerOf(otherEnd);
        if (otherOwner == ownFrame) {
            return; // not a crossing: the same object on both ends, or both free
        }
        addActiveAnimationFrame(otherOwner);
        if (otherEnd instanceof GraphPetriPlace place) {
            ArrayList<GraphPetriPlace> single = new ArrayList<>(List.of(place));
            animPlaces(single, 80, 6, ANIMATION_CROSSING_COLOR);
            animPlaces(single, 80, 2, Color.BLACK);
        }
    }

    /**
     * Marks {@code frame} as the one currently doing something during a running animation,
     * replacing whichever frame(s) were marked that way before — the animation spotlight
     * belongs to one event at a time, {@link #addActiveAnimationFrame} to widen it to a
     * crossing without losing it.
     *
     * @param frame the firing transition's own frame, or {@code null} if it is free
     */
    private void setActiveAnimationFrame(GraphObjectFrame frame) {
        for (GraphObjectFrame active : activeAnimationFrames) {
            active.setHighlightColor(null);
        }
        activeAnimationFrames.clear();
        if (frame != null) {
            frame.setHighlightColor(ANIMATION_ACTIVE_COLOR);
            activeAnimationFrames.add(frame);
        }
        repaint();
    }

    /**
     * Adds {@code frame} to the current animation spotlight without clearing what is already
     * lit — a crossing highlights both ends of the link together, distinctly from a plain
     * local firing.
     */
    private void addActiveAnimationFrame(GraphObjectFrame frame) {
        if (frame != null && activeAnimationFrames.add(frame)) {
            frame.setHighlightColor(ANIMATION_CROSSING_COLOR);
            repaint();
        }
    }

    /**
     * Clears every frame's animation highlight — called before a run starts, so it does not
     * inherit whatever the previous one left lit, and once it ends, so nothing stays lit
     * forever.
     */
    public void clearAnimationHighlight() {
        for (GraphObjectFrame active : activeAnimationFrames) {
            active.setHighlightColor(null);
        }
        activeAnimationFrames.clear();
        repaint();
    }

    private void animArcIn(ArrayList<GraphArcIn> list, long sleepDelay, int lineWidth, Color color) {
        try {
            for (GraphArcIn a : list) {
                a.setLineWidth(lineWidth);
                a.setColor(color);
                this.repaint();
            }
            Thread.sleep(sleepDelay);
        } catch (InterruptedException ex) {
            LOGGER.error("Unexpected error", ex);
        }
    }

    private void animArcIn(ArrayList<GraphArcIn> list, long sleepDelay, int lineWidth) {
        try {
            for (GraphArcIn a : list) {
                a.setLineWidth(lineWidth);
                this.repaint();
            }
            Thread.sleep(sleepDelay);
        } catch (InterruptedException ex) {
            LOGGER.error("Unexpected error", ex);
        }
    }

    private void animArcOut(ArrayList<GraphArcOut> list, long sleepDelay, int lineWidth, Color color) {
        try {
            for (GraphArcOut a : list) {
                a.setLineWidth(lineWidth);
                a.setColor(color);
                this.repaint();
            }
            Thread.sleep(sleepDelay);
        } catch (InterruptedException ex) {
            LOGGER.error("Unexpected error", ex);
        }
    }

    private void animArcOut(ArrayList<GraphArcOut> list, long sleepDelay, int lineWidth) {
        try {
            for (GraphArcOut a : list) {
                a.setLineWidth(lineWidth);
                this.repaint();
            }
            Thread.sleep(sleepDelay);
        } catch (InterruptedException ex) {
            LOGGER.error("Unexpected error", ex);
        }
    }

    private void animPlaces(ArrayList<GraphPetriPlace> list, long sleepDelay, int lineWidth, Color color) {
        try {
            for (GraphPetriPlace p : list) {
                p.setLineWidth(lineWidth);
                p.setColor(color);
                this.repaint();
            }
            Thread.sleep(sleepDelay);
        } catch (InterruptedException ex) {
            LOGGER.error("Unexpected error", ex);
        }
    }

    private void animPlaces(ArrayList<GraphPetriPlace> list, long sleepDelay, int lineWidth) {
        try {
            for (GraphPetriPlace p : list) {
                p.setLineWidth(lineWidth);
                this.repaint();
            }
            Thread.sleep(sleepDelay);
        } catch (InterruptedException ex) {
            LOGGER.error("Unexpected error", ex);
        }
    }

    private void animTransitions(ArrayList<GraphPetriTransition> list, long sleepDelay, int lineWidth, Color color) {
        try {
            for (GraphPetriTransition tr : list) {
                tr.setLineWidth(lineWidth);
                tr.setColor(color);
                this.repaint();
            }
            Thread.sleep(sleepDelay);
        } catch (InterruptedException ex) {
            LOGGER.error("Unexpected error", ex);
        }
    }

    private void animTransitions(ArrayList<GraphPetriTransition> list, long sleepInterval, int lineWidth) {
        try {
            for (GraphPetriTransition tr : list) {
                tr.setLineWidth(lineWidth);
                this.repaint();
            }
            Thread.sleep(sleepInterval);
        } catch (InterruptedException ex) {
            LOGGER.error("Unexpected error", ex);
        }
    }

}
