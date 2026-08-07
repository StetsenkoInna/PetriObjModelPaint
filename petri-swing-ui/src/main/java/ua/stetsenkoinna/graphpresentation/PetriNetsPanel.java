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
import ua.stetsenkoinna.graphpresentation.objmodel.NetTemplateDialog;
import ua.stetsenkoinna.graphpresentation.objmodel.ObjectEditorFrame;
import ua.stetsenkoinna.graphpresentation.objmodel.PetriObjectPalette;
import ua.stetsenkoinna.graphpresentation.objmodel.PetriObjectTemplate;
import ua.stetsenkoinna.graphnet.GraphNetBuilder;
import ua.stetsenkoinna.libnet.NetTemplateCatalog;
import ua.stetsenkoinna.pnml.PnmlParser;
import ua.stetsenkoinna.graphpresentation.undoable_edits.AddArcEdit;
import ua.stetsenkoinna.graphpresentation.undoable_edits.AddGraphElementEdit;
import ua.stetsenkoinna.graphpresentation.undoable_edits.DeleteArcEdit;
import ua.stetsenkoinna.graphpresentation.undoable_edits.DeleteGraphElementsEdit;
import ua.stetsenkoinna.graphpresentation.undoable_edits.PasteElementsEdit;
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
    private List<GraphElement> choosenElements = new ArrayList<>();
    private double scale = 1.0;
    private boolean leftMouseButtonPressed = false;

    private List<GraphElement> copiedElements;

    /** False for a panel that only displays a net, e.g. while animating a whole model. */
    private final boolean editable;

    /**
     * The canvas seen as a Petri-object model: the drawing above, plus the frames that mark
     * out the objects in it and the places shared between them.
     */
    private final GraphCanvasModel canvasModel = new GraphCanvasModel();

    /** Frame the user is currently moving, resizing, or has selected. */
    private GraphObjectFrame draggedFrame;
    private GraphObjectFrame resizedFrame;
    private GraphObjectFrame selectedFrame;
    /** Frames selected together, e.g. by Ctrl+A — the frame-level equivalent of choosenElements. */
    private final List<GraphObjectFrame> choosenFrames = new ArrayList<>();
    /** Offset between the pointer and the dragged frame's corner, so it does not jump. */
    private Point frameDragOffset;

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

    /** The scroll pane viewport being panned, and where the drag started, while tool == PAN
     *  — or, during a double-click-to-pan gesture on the Select tool, while
     *  {@link #selectToolPanning} is true instead. */
    private JViewport panViewport;
    private Point panDragOrigin;
    private Point panViewportOrigin;
    /** True mid-gesture after a double-click on empty canvas with the Select tool active,
     *  panning the view without actually switching tools. */
    private boolean selectToolPanning;

    private static final Cursor ERASER_CURSOR = buildEraserCursor();

    public List<GraphElement> getChoosenElements() {
        return choosenElements;
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
                if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    // A selected Petri-object frame is deleted as a whole, the same way its
                    // context-menu "Remove" does — but only when nothing more specific (an
                    // arc, a single element, a rubber-band selection) is also selected, so
                    // Delete keeps its existing per-element meaning everywhere else.
                    if (selectedFrame != null && choosenArc == null && choosen == null
                            && choosenElements.isEmpty() && choosenFrames.isEmpty()) {
                        confirmRemoveObjectFrame(selectedFrame);
                    }
                    // Deliberately not also requiring choosenElements to be empty: Ctrl+A fills
                    // both lists, so that condition meant a select-all followed by Delete wiped
                    // every place and transition and left the objects behind as empty
                    // rectangles. Frames go first, since removing one releases its members back
                    // to the canvas and the element sweep below then accounts for them.
                    if (!choosenFrames.isEmpty() && choosenArc == null && choosen == null) {
                        confirmRemoveObjectFrames(new ArrayList<>(choosenFrames));
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
                    if (!choosenElements.isEmpty()) {

                        int result = JOptionPane.showConfirmDialog((Component) null, "Are you sure you want to delete selected elements?",
                                "Delete", JOptionPane.OK_CANCEL_OPTION);
                        if (result == JOptionPane.OK_OPTION) {
                            try {
                                List<GraphArcIn> inArcsToBeRemoved = new ArrayList<>();
                                List<GraphArcOut> outArcsToBeRemoved = new ArrayList<>();

                                for (GraphElement graphElement : choosenElements) {
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

                                    remove(graphElement);
                                    PetriNetsPanel.this.setDefaultColorGraphElements(); //27.07.2018
                                }
                                /* save this action into undo manager so that it can be undone */
                                DeleteGraphElementsEdit edit
                                        = new DeleteGraphElementsEdit(PetriNetsPanel.this,
                                                new ArrayList(choosenElements),
                                                inArcsToBeRemoved, outArcsToBeRemoved);

                                PetriNetsFrame.getUndoSupport().postEdit(edit);
                            } catch (ExceptionInvalidNetStructure ex) {
                                LOGGER.error("Unexpected error", ex);
                            } finally {
                                choosenElements.clear();
                                PetriNetsPanel.this.setDefaultColorGraphElements();//27.07.2018

                            }
                        }
                    }
                }

                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_A) {

                    selectAll();
                    repaint();
                }

                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_C) {
                    copiedElements = new ArrayList<>(choosenElements);
                }

                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_V) {
                    pasteAction();
                }

                // Duplicating a Petri-object is a distinct gesture from copy/paste of plain
                // elements, since it also carries the object's name, priority and template —
                // Ctrl+D matches what the frame's own context menu offers.
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_D && selectedFrame != null) {
                    duplicateObject(selectedFrame);
                }
            }
        });

    }

    /**
     * A handler for ctrl+V. Clones elements and arcs associated with them and
     * pastes them onto the canvas
     */
    public void pasteAction() {
        if (copiedElements != null && !copiedElements.isEmpty()) {
            GraphPetriNet.GraphNetFragment clonedFragment
                    = graphNet.bulkCopyNoPasteElements(copiedElements);

            addNetFragment(clonedFragment);

            copiedElements = new ArrayList<>(clonedFragment.elements);

            PetriNetsFrame.getUndoSupport().postEdit(
                    new PasteElementsEdit(this, clonedFragment)
            );
        }
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
        for (GraphElement prevElement : choosenElements) {
            prevElement.setColor(Color.BLACK);
        }
        choosenElements.clear();

        for (GraphElement element : elementsToSpawn) {
            Point2D spawnPoint = element.getGraphElementCenter();
            spawnPoint.setLocation(spawnPoint.getX() + 15, spawnPoint.getY() + 15);

            element.setNewCoordinates(spawnPoint);

            if (element instanceof GraphPetriPlace) {
                this.getGraphNet().getGraphPetriPlaceList().add((GraphPetriPlace) element);
            } else {
                this.getGraphNet().getGraphPetriTransitionList().add((GraphPetriTransition) element);
            }

            choosenElements.add(element);
            element.setColor(Color.GREEN);
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
                Point masterPoint = connectionEndpoint(fusion.getMasterOwner(), fusion.getMaster());
                Point joinedPoint = connectionEndpoint(fusion.getJoinedOwner(), fusion.getJoined());
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
        for (GraphElement graphElement : choosenElements) {

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
    }

    /**
     * Draws the Petri-object frames.
     *
     * @param collapsedOnes true to draw the collapsed frames, which hide their net and are
     *        therefore painted over it, false for the expanded ones painted under it
     */
    private void paintObjectFrames(Graphics2D g2, boolean collapsedOnes) {
        List<GraphObjectFrame> frames = canvasModel.getFrames();
        for (int index = 0; index < frames.size(); index++) {
            GraphObjectFrame frame = frames.get(index);
            if (frame.isCollapsed() != collapsedOnes) {
                continue;
            }
            if (collapsedOnes) {
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(frame.getBounds().x, frame.getBounds().y,
                        frame.getBounds().width, frame.getBounds().height, 14, 14);
            }
            frame.draw(g2, index, frame == selectedFrame || choosenFrames.contains(frame), countElementsIn(frame));
        }
    }

    /**
     * Draws the ports of every frame whose content is currently hidden — while an object's own
     * net is on screen there is nothing a port needs to stand in for, so drawing its circle
     * over the real element right next to it would only be clutter.
     */
    private void paintPorts(Graphics2D g2) {
        for (GraphObjectFrame frame : canvasModel.getFrames()) {
            if (!isContentHidden(frame)) {
                continue;
            }
            for (FramePort port : canvasModel.portsOf(frame)) {
                port.draw(g2, port == draggedFromPort || port == hoveredPort);
            }
        }
    }

    /**
     * @param frame the owner of an element whose content might currently be hidden
     * @return true if that frame's net is not painted right now
     */
    private boolean isContentHidden(GraphObjectFrame frame) {
        return frame != null && !frame.isContentShown();
    }

    /**
     * @return every place and transition that is not currently painted, because the frame that
     *         claims it is collapsed or has its content hidden behind the eye icon
     */
    private java.util.Set<GraphElement> hiddenElements() {
        java.util.Set<GraphElement> hidden = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (GraphPetriPlace place : graphNet.getGraphPetriPlaceList()) {
            if (isContentHidden(canvasModel.ownerOf(place))) {
                hidden.add(place);
            }
        }
        for (GraphPetriTransition transition : graphNet.getGraphPetriTransitionList()) {
            if (isContentHidden(canvasModel.ownerOf(transition))) {
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
        if (!isContentHidden(frame)) {
            return element;
        }
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
        if (!isContentHidden(beginOwner) && !isContentHidden(endOwner)) {
            return; // both ends are on screen already, drawn directly by paintGraphPetriNet
        }
        GraphElement beginAnchor = connectionAnchor(beginOwner, begin);
        GraphElement endAnchor = connectionAnchor(endOwner, end);
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

    public GraphElement find(Point2D p) {
        for (GraphPetriPlace pp : graphNet.getGraphPetriPlaceList()) {
            if (pp.isGraphElement(p)) {
                return pp;
            }
        }
        for (GraphPetriTransition pt : graphNet.getGraphPetriTransitionList()) {
            if (pt.isGraphElement(p)) {
                return pt;
            }
        }
        return null;
    }

    public GraphArc findArc(Point2D p) {
        for (GraphArcOut to : graphNet.getGraphArcOutList()) {
            if (to.isEnoughDistance(p)) {
                return to;
            }
        }
        for (GraphArcIn ti : graphNet.getGraphArcInList()) {
            if (ti.isEnoughDistance(p)) {
                return ti;
            }
        }
        return null;
    }

    public void remove(GraphElement s) throws ExceptionInvalidNetStructure {
        if (s == null) {
            return;
        }
        if (s == current) {
            current = null;

        }
        graphNet.delGraphElement(s); //added by Inna 4.12.2012

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

    public void selectAll() { // works when key event is Ctrl+a
        choosenElements.clear();
        for (GraphPetriPlace p : graphNet.getGraphPetriPlaceList()) {
            choosenElements.add(p);
            p.setColor(Color.GREEN);

        }
        for (GraphPetriTransition tr : graphNet.getGraphPetriTransitionList()) {
            choosenElements.add(tr);
            tr.setColor(Color.GREEN);

        }
        // A Petri-object frame is as much a selectable thing on this canvas as a place or a
        // transition is, so Ctrl+A reaches it too.
        choosenFrames.clear();
        choosenFrames.addAll(canvasModel.getFrames());
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
     * <p>A non-empty selection always wins: right-clicking while elements are selected means
     * grouping that selection into a Petri-object, regardless of exactly what is under the
     * pointer. Otherwise a specific place or transition keeps its existing right-click
     * behaviour (opening its properties) — this menu only claims clicks that hit a
     * Petri-object frame or empty canvas.
     *
     * @param ev the triggering mouse event, used both to test and to position the menu
     * @param point the click point in canvas coordinates
     * @return true if a menu was shown, so the caller should not process the click further
     */
    private boolean maybeShowContextMenu(MouseEvent ev, Point point) {
        if (!editable || !ev.isPopupTrigger()) {
            return false;
        }
        if (!choosenElements.isEmpty()) {
            showGroupSelectionMenu(ev, new ArrayList<>(choosenElements));
            return true;
        }
        if (find(point) != null) {
            return false; // a single element keeps its own right-click behaviour
        }
        GraphObjectFrame frame = canvasModel.frameAt(point);
        if (frame != null) {
            showObjectFrameMenu(ev, frame);
            return true;
        }
        showNewObjectMenu(ev, point);
        return true;
    }

    private void showGroupSelectionMenu(MouseEvent ev, List<GraphElement> selection) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem group = new JMenuItem("Group selection into Petri-object");
        group.addActionListener(e -> groupIntoObject(selection));
        menu.add(group);
        menu.show(this, ev.getX(), ev.getY());
    }

    private void showObjectFrameMenu(MouseEvent ev, GraphObjectFrame frame) {
        setSelectedFrame(frame);

        JPopupMenu menu = new JPopupMenu();

        JMenuItem editNet = new JMenuItem("Edit net...");
        editNet.setToolTipText("Open this object's own net for editing — the same as double-clicking it");
        editNet.addActionListener(e -> openObjectEditor(frame));
        menu.add(editNet);

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
     * Draws a Petri-object frame around the given elements, which is how an existing net is
     * split into objects.
     */
    private void groupIntoObject(List<GraphElement> selection) {
        String name = JOptionPane.showInputDialog(dialogOwner(), "Name of the Petri-object",
                "Object " + (canvasModel.getFrames().size() + 1));
        if (name == null || name.isBlank()) {
            return;
        }
        GraphObjectFrame frame = new GraphObjectFrame(name.trim(), boundsAround(selection));
        for (GraphElement element : selection) {
            frame.addMember(element);
        }
        addObjectFrame(frame);

        // The grouped elements are now locked inside their new frame — highlighting them as
        // "selected" afterward would be stale and, unlike before, no longer something Delete
        // or a drag could act on directly.
        for (GraphElement element : selection) {
            element.setColor(Color.BLACK);
        }
        choosenElements.clear();
        choosen = null;
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
        addObjectFrame(new GraphObjectFrame(name.trim(), bounds));
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

        GraphObjectFrame frame = new GraphObjectFrame(objectName, boundsAround(members));
        frame.setTemplate(template);
        for (GraphElement element : members) {
            frame.addMember(element);
            // Locked inside a frame from the moment it lands, so it is never left looking
            // selected — nothing on the canvas could act on that selection anyway.
            element.setColor(Color.BLACK);
        }
        choosenElements.clear();
        choosen = null;
        addObjectFrame(frame);
        // addObjectFrame leaves what it added selected, which is right when the user created
        // one deliberately but wrong here: stamping drops object after object, and each would
        // sit highlighted with nothing having been selected at all.
        selectedFrame = null;
        repaint();
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
            return;
        }

        GraphPetriNet.GraphNetFragment copy = graphNet.bulkCopyNoPasteElements(inside);
        int dx = frame.getBounds().width + 40;
        for (GraphElement element : copy.elements) {
            Point2D centre = element.getGraphElementCenter();
            element.setNewCoordinates(new Point2D.Double(centre.getX() + dx, centre.getY()));
        }
        addNetFragment(copy);

        Rectangle bounds = new Rectangle(frame.getBounds().x + dx, frame.getBounds().y,
                frame.getBounds().width, frame.getBounds().height);
        GraphObjectFrame duplicate = new GraphObjectFrame(frame.getName() + " copy", bounds);
        duplicate.setPriority(frame.getPriority());
        duplicate.setTemplate(frame.getTemplate());
        for (GraphElement element : copy.elements) {
            duplicate.addMember(element);
        }
        addObjectFrame(duplicate);
        repaint();
    }

    private void renameObject(GraphObjectFrame frame) {
        String name = JOptionPane.showInputDialog(dialogOwner(), "Name of the Petri-object", frame.getName());
        if (name != null && !name.isBlank()) {
            frame.setName(name.trim());
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
     * Opens the dedicated editor for one Petri-object's own net.
     *
     * <p>The editor operates on the very same place, transition and arc instances the main
     * canvas already holds for this object — filtered into a net of their own, but not
     * copied. That is what makes structural change (an element added or removed) safe to hold
     * back until the user presses Save: the editor's net is a separate list, so nothing this
     * method does not explicitly copy over ever reaches the main canvas's own lists. Moving an
     * element is a different story — it changes the same instance the canvas already shows —
     * so a position snapshot taken before the dialog opens is what a Cancel restores instead.
     *
     * @param frame the object to edit
     */
    private void openObjectEditor(GraphObjectFrame frame) {
        GraphPetriNet objectNet = buildObjectNet(frame);
        List<GraphPetriPlace> placesBefore = List.copyOf(objectNet.getGraphPetriPlaceList());
        List<GraphPetriTransition> transitionsBefore = List.copyOf(objectNet.getGraphPetriTransitionList());
        List<GraphArcIn> arcsInBefore = List.copyOf(objectNet.getGraphArcInList());
        List<GraphArcOut> arcsOutBefore = List.copyOf(objectNet.getGraphArcOutList());
        Map<GraphElement, Point2D> positionsBefore = snapshotPositions(placesBefore, transitionsBefore);

        PetriNetsPanel editorPanel = new PetriNetsPanel(null, true);
        editorPanel.setCanvasNet(objectNet);
        editorPanel.setPreferredSize(new java.awt.Dimension(
                Math.max(500, frame.getBounds().width), Math.max(400, frame.getBounds().height)));

        ObjectEditorFrame editor = new ObjectEditorFrame(dialogOwner(), frame.getName(), editorPanel);
        editor.setVisible(true);
        // Modal: execution resumes here only once the user closes the editor.

        if (!editor.wasSaved()) {
            restorePositions(positionsBefore);
            canvasModel.syncFusions();
            updateArcCoordinates();
            repaint();
            return;
        }

        reconcile(graphNet.getGraphPetriPlaceList(), placesBefore, objectNet.getGraphPetriPlaceList());
        reconcile(graphNet.getGraphPetriTransitionList(), transitionsBefore, objectNet.getGraphPetriTransitionList());
        reconcile(graphNet.getGraphArcInList(), arcsInBefore, objectNet.getGraphArcInList());
        reconcile(graphNet.getGraphArcOutList(), arcsOutBefore, objectNet.getGraphArcOutList());
        reconcileMembership(frame, placesBefore, objectNet.getGraphPetriPlaceList());
        reconcileMembership(frame, transitionsBefore, objectNet.getGraphPetriTransitionList());

        // The object may have gained, lost or repositioned elements while its own editor had
        // it, so the frame drawn for it on the shared canvas is refit around what it now
        // actually holds — an empty object, having nothing to fit around, keeps its size.
        if (!frame.getMembers().isEmpty()) {
            frame.setBounds(boundsAround(new ArrayList<>(frame.getMembers())));
        }

        canvasModel.removeDanglingFusions();
        updateArcCoordinates();
        repaint();
    }

    private static Map<GraphElement, Point2D> snapshotPositions(
            List<? extends GraphElement> places, List<? extends GraphElement> transitions) {
        Map<GraphElement, Point2D> snapshot = new java.util.IdentityHashMap<>();
        for (GraphElement element : places) {
            snapshot.put(element, element.getGraphElementCenter());
        }
        for (GraphElement element : transitions) {
            snapshot.put(element, element.getGraphElementCenter());
        }
        return snapshot;
    }

    private static void restorePositions(Map<GraphElement, Point2D> positions) {
        for (Map.Entry<GraphElement, Point2D> entry : positions.entrySet()) {
            if (entry.getValue() != null) {
                entry.getKey().setNewCoordinates(entry.getValue());
            }
        }
    }

    /**
     * Filters out one Petri-object's own places, transitions and internal arcs into a net of
     * their own — the same instances the main canvas holds, not copies. An arc crossing to
     * another object is a link, not part of this object's own net, and is left out: it stays
     * exactly as it is, addressed through the frame's ports, whatever happens in the editor.
     *
     * @param frame the object to filter out
     * @return a net ready to hand to the object's own editor panel
     */
    private GraphPetriNet buildObjectNet(GraphObjectFrame frame) {
        ArrayList<GraphPetriPlace> places = new ArrayList<>();
        ArrayList<GraphPetriTransition> transitions = new ArrayList<>();
        ArrayList<GraphArcIn> arcsIn = new ArrayList<>();
        ArrayList<GraphArcOut> arcsOut = new ArrayList<>();
        for (GraphPetriPlace place : graphNet.getGraphPetriPlaceList()) {
            if (canvasModel.ownerOf(place) == frame) {
                places.add(place);
            }
        }
        for (GraphPetriTransition transition : graphNet.getGraphPetriTransitionList()) {
            if (canvasModel.ownerOf(transition) == frame) {
                transitions.add(transition);
            }
        }
        for (GraphArcIn arc : graphNet.getGraphArcInList()) {
            if (canvasModel.ownerOf((GraphPetriPlace) arc.getBeginElement()) == frame
                    && canvasModel.ownerOf((GraphPetriTransition) arc.getEndElement()) == frame) {
                arcsIn.add(arc);
            }
        }
        for (GraphArcOut arc : graphNet.getGraphArcOutList()) {
            if (canvasModel.ownerOf((GraphPetriTransition) arc.getBeginElement()) == frame
                    && canvasModel.ownerOf((GraphPetriPlace) arc.getEndElement()) == frame) {
                arcsOut.add(arc);
            }
        }
        return new GraphPetriNet(null, places, transitions, arcsIn, arcsOut);
    }

    /**
     * Applies to the main canvas's list of one kind of element whatever the object editor did
     * to its own copy of that same kind: an element the editor no longer has was deleted,
     * anything it now has that it did not start with was added. Surviving elements need no
     * change here — they are the same instances in both lists already.
     *
     * @param mainList the main canvas's list to update
     * @param before what this object contributed to that list when the editor opened
     * @param after what the editor's own list contains now that it has closed
     */
    private static <T> void reconcile(List<T> mainList, List<T> before, List<T> after) {
        mainList.removeIf(element -> before.contains(element) && !after.contains(element));
        for (T element : after) {
            if (!before.contains(element)) {
                mainList.add(element);
            }
        }
    }

    /**
     * Claims for {@code frame} whatever its editor's own list gained, releases whatever it
     * lost — the same before/after comparison as {@link #reconcile}, but updating the frame's
     * explicit membership instead of the main canvas's element lists.
     */
    private static void reconcileMembership(GraphObjectFrame frame,
            List<? extends GraphElement> before, List<? extends GraphElement> after) {
        for (GraphElement element : before) {
            if (!after.contains(element)) {
                frame.removeMember(element);
            }
        }
        for (GraphElement element : after) {
            if (!before.contains(element)) {
                frame.addMember(element);
            }
        }
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
            choosenFrames.clear();
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

    private static Rectangle boundsAround(List<? extends GraphElement> elements) {
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

            // The eye icon sits inside the header's own rectangle, so it has to be checked
            // ahead of the header hit-test below — otherwise the click would be read as the
            // start of a frame drag instead of a toggle.
            GraphObjectFrame eyeFrame = frameEyeIconAt(scaledCurrentMousePoint);
            if (eyeFrame != null && SwingUtilities.isLeftMouseButton(ev)) {
                eyeFrame.setContentVisible(!eyeFrame.isContentVisible());
                selectedFrame = eyeFrame;
                repaint();
                return;
            }

            // A frame's header or its corner is grabbed ahead of everything below — including
            // a port, or an owned element's own body standing in for one — since shrinking a
            // frame down small enough can put its own contents underneath either. Ports were
            // never able to overlap these before (they only ever sat on the border itself),
            // but an element's full body, now also reachable the same way while shown, can.
            resizedFrame = frameHandleAt(scaledCurrentMousePoint);
            draggedFrame = resizedFrame == null ? frameHeaderAt(scaledCurrentMousePoint) : null;
            if (resizedFrame != null || draggedFrame != null) {
                GraphObjectFrame grabbed = resizedFrame != null ? resizedFrame : draggedFrame;
                selectedFrame = grabbed;
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
            FramePort port = canvasModel.portAt(scaledCurrentMousePoint);
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

            // Elements inside a Petri-object are edited in that object's own window, not
            // dragged around on the shared canvas — a click landing anywhere else in the
            // frame (not its header or resize handle) selects the object itself instead of
            // whatever net element happens to be underneath.
            GraphObjectFrame frameAtPoint = canvasModel.frameAt(scaledCurrentMousePoint);
            if (frameAtPoint != null) {
                selectedFrame = frameAtPoint;
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

            Point scaledCurrentMousePoint = new Point((int) (ev.getX() / scale), (int) (ev.getY() / scale));

            // The toggle already happened on mousePressed; this only keeps that same click
            // from also being read as a frame click below (the icon sits inside the header's
            // own rectangle, which canvasModel.frameAt would otherwise match too).
            if (frameEyeIconAt(scaledCurrentMousePoint) != null) {
                return;
            }

            // A frame — its header or its locked interior alike — selects the object on a
            // single click; a double click opens that object's own editor, the only place its
            // net can actually be changed.
            GraphObjectFrame frameAtPoint = canvasModel.frameAt(scaledCurrentMousePoint);
            if (frameAtPoint != null) {
                selectedFrame = frameAtPoint;
                choosenFrames.clear();
                if (ev.getClickCount() >= 2) {
                    openObjectEditor(frameAtPoint);
                }
                repaint();
                return;
            }

            if (current == null && currentArc == null) { // previous click was empty

                //  PetriNetsPanel.this.printPointLocation(prevMouseLocation, "clear");
                setDefaultColorGraphElements();
                setDefaultColorGraphArcs();
                choosenElements.clear();
                choosenFrames.clear();
                // Clicking nothing deselects the current Petri-object too. It used to survive
                // here, which went unnoticed only because a selected frame was drawn almost
                // identically to an unselected one.
                selectedFrame = null;
                choosen = null;
            }
            if (current != null) {
                current.setColor(Color.BLUE); //26.07.2018
                choosenElements.clear(); // 27.08.2018
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
                    || tool == CanvasTool.ADD_TRANSITION || tool == CanvasTool.ADD_PETRI_OBJECT) {
                return;
            }

            if (draggedFromPort != null) {
                FramePort targetPort = canvasModel.portAt(scaledCurrentMousePoint);
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
                Rectangle marquee = marqueeRectangle();
                for (GraphPetriPlace p : graphNet.getGraphPetriPlaceList()) {
                    if (marquee.contains(p.getGraphElementCenter())) {
                        choosenElements.add(p);
                        p.setColor(Color.GREEN);
                    }
                }
                for (GraphPetriTransition tr : graphNet.getGraphPetriTransitionList()) {
                    if (marquee.contains(tr.getGraphElementCenter())) {
                        choosenElements.add(tr);
                        tr.setColor(Color.GREEN);
                    }
                }
                // A Petri-object is caught the same way its elements are — by the rubber band
                // covering it — so a drag across the canvas selects what it visibly encloses
                // rather than silently skipping every object drawn there.
                for (GraphObjectFrame frame : canvasModel.getFrames()) {
                    if (marquee.contains(frame.getBounds()) && !choosenFrames.contains(frame)) {
                        choosenFrames.add(frame);
                    }
                }
                repaint();
            }

            confirmMoveBetweenObjects();

            startDragMouseLocation = null;
            currentDragMouseLocation = null;
            current = null;
            //  setDefaultColorGraphElements();// deleted 27.07.2018

            setCursor(Cursor.getDefaultCursor());
            if (currentArc != null) {
                currentArc.setColor(Color.BLUE);
                current = find(scaledCurrentMousePoint);
                if (current != null && canvasModel.ownerOf(current) != null) {
                    // A framed element only takes arcs through its ports now — the arc tool
                    // cannot reach it directly, the same way it can no longer be dragged.
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
        GraphObjectFrame after = centre == null ? null : canvasModel.frameAt(centre);
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
        if (before != null) {
            before.removeMember(element);
        }
        if (after != null) {
            after.addMember(element);
        }
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
                    || tool == CanvasTool.ADD_TRANSITION || tool == CanvasTool.ADD_PETRI_OBJECT) {
                return;
            }

            Point scaledCurrentMousePoint = new Point((int) (ev.getX() / scale), (int) (ev.getY() / scale));

            if (draggedFromPort != null) {
                draggedPortCurrentPoint = scaledCurrentMousePoint;
                hoveredPort = canvasModel.portAt(scaledCurrentMousePoint);
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

            if (choosen == null && choosenElements.isEmpty()) {
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

            if (!choosenElements.isEmpty() && leftMouseButtonPressed) { //moving choosenElements

                for (GraphElement e : choosenElements) {
                    e.setColor(Color.GREEN);
                }

                setCursor(new Cursor(Cursor.MOVE_CURSOR));
                for (GraphElement graphElement : choosenElements) {
                    Point currentLocation = new Point(
                            (int) graphElement.getGraphElementCenter().getX(),
                            (int) graphElement.getGraphElementCenter().getY());

                    Point newLocation = new Point(
                            currentLocation.x
                            + (int) scaledCurrentMousePoint.getX()
                            - prevMouseLocation.x, currentLocation.y
                            + (int) scaledCurrentMousePoint.getY()
                            - prevMouseLocation.y);
                    graphElement.setNewCoordinates(newLocation);
                }
                for (GraphArcIn ti : graphNet.getGraphArcInList()) {
                    ti.updateCoordinates();
                }
                for (GraphArcOut to : graphNet.getGraphArcOutList()) {
                    to.updateCoordinates();
                }
                prevMouseLocation = scaledCurrentMousePoint;
            }
            repaint();
        }

        @Override
        public void mouseMoved(MouseEvent ev) {
            if (tool == CanvasTool.PAN || tool == CanvasTool.DELETE
                    || tool == CanvasTool.ADD_PLACE || tool == CanvasTool.ADD_TRANSITION
                    || tool == CanvasTool.ADD_PETRI_OBJECT) {
                // These tools keep their own dedicated cursor regardless of what is underneath
                // the pointer — port hovering is a Select-tool affordance only.
                return;
            }
            Point scaledCurrentMousePoint = new Point((int) (ev.getX() / scale), (int) (ev.getY() / scale));
            FramePort hovered = canvasModel.portAt(scaledCurrentMousePoint);
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
        selectedFrame = null;
        choosenElements.clear();
        choosenFrames.clear();
        startDragMouseLocation = null;
        currentDragMouseLocation = null;
        leftMouseButtonPressed = false;
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

        AddGraphElementEdit edit = new AddGraphElementEdit(this, element);
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

            remove(element);
            DeleteGraphElementsEdit edit = new DeleteGraphElementsEdit(this, element,
                    inArcsToBeRemoved, outArcsToBeRemoved);
            PetriNetsFrame.getUndoSupport().postEdit(edit);
        } catch (ExceptionInvalidNetStructure ex) {
            LOGGER.error("Unexpected error", ex);
        }
    }

    /**
     * Handles a click made with the Delete tool active: removes whatever single element or
     * arc is under the pointer, or does nothing on empty canvas.
     *
     * <p>A port, or a framed element's own drawing while its object's content is shown, is left
     * alone — same as every other direct-canvas gesture, deleting a locked object's element
     * this way would reach past the boundary that {@code frameAt}/{@code portAt} normally
     * enforce before {@link #find} is ever consulted; whole-object removal already has its own,
     * confirmed path through the frame's own context menu.
     *
     * @param scaledPoint the click point in canvas coordinates
     */
    private void handleDeleteClick(Point scaledPoint) {
        if (canvasModel.portAt(scaledPoint) != null || canvasModel.frameAt(scaledPoint) != null) {
            return;
        }
        GraphElement element = find(scaledPoint);
        if (element != null) {
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

    public void addGraphNet(GraphPetriNet net) {
        // If there's no existing net, just set the new one
        if (graphNet == null) {
            setCanvasNet(net);
        } else {
            // Merge the new net into the existing one
            graphNet.mergeGraphNet(net);
        }

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
        selectedFrame = null;
        choosenFrames.clear();
        choosen = null;
        current = null;
        choosenElements.clear();
        canvasModel.syncFusions();
        updateArcCoordinates();
        repaint();
    }

    /**
     * @return the frame the user has selected, or {@code null}
     */
    public GraphObjectFrame getSelectedFrame() {
        return selectedFrame;
    }

    public void setSelectedFrame(GraphObjectFrame frame) {
        selectedFrame = frame;
        repaint();
    }

    /**
     * Adds a Petri-object frame and selects it.
     *
     * @param frame the frame to add
     */
    public void addObjectFrame(GraphObjectFrame frame) {
        canvasModel.getFrames().add(frame);
        selectedFrame = frame;
        repaint();
    }

    /**
     * Removes a Petri-object frame. What was drawn inside stays on the canvas and becomes
     * part of whatever frame now covers it, or of the free elements.
     *
     * @param frame the frame to remove
     */
    public void removeObjectFrame(GraphObjectFrame frame) {
        canvasModel.getFrames().remove(frame);
        canvasModel.releaseMembers(frame);
        if (selectedFrame == frame) {
            selectedFrame = null;
        }
        repaint();
    }

    /**
     * @param frame a Petri-object frame
     * @return how many places and transitions are drawn inside it
     */
    public int countElementsIn(GraphObjectFrame frame) {
        int count = 0;
        for (GraphPetriPlace place : graphNet.getGraphPetriPlaceList()) {
            if (canvasModel.ownerOf(place) == frame) {
                count++;
            }
        }
        for (GraphPetriTransition transition : graphNet.getGraphPetriTransitionList()) {
            if (canvasModel.ownerOf(transition) == frame) {
                count++;
            }
        }
        return count;
    }

    /**
     * @param point a point on the canvas
     * @return the topmost frame whose header is under the point, or {@code null}
     */
    private GraphObjectFrame frameHeaderAt(Point2D point) {
        List<GraphObjectFrame> frames = canvasModel.getFrames();
        for (int index = frames.size() - 1; index >= 0; index--) {
            if (frames.get(index).isOnHeader(point)) {
                return frames.get(index);
            }
        }
        return null;
    }

    /**
     * @param point a point on the canvas
     * @return the topmost frame whose resize handle is under the point, or {@code null}
     */
    private GraphObjectFrame frameHandleAt(Point2D point) {
        List<GraphObjectFrame> frames = canvasModel.getFrames();
        for (int index = frames.size() - 1; index >= 0; index--) {
            if (frames.get(index).isOnResizeHandle(point)) {
                return frames.get(index);
            }
        }
        return null;
    }

    /**
     * @param point a point on the canvas
     * @return the topmost frame whose eye icon is under the point, or {@code null}
     */
    private GraphObjectFrame frameEyeIconAt(Point2D point) {
        List<GraphObjectFrame> frames = canvasModel.getFrames();
        for (int index = frames.size() - 1; index >= 0; index--) {
            if (frames.get(index).isOnEyeIcon(point)) {
                return frames.get(index);
            }
        }
        return null;
    }

    /**
     * Moves a frame together with everything it claims, so its elements always stay inside it —
     * they are still fixed relative to the frame itself, only ever repositioned individually
     * through the object's own editor, not draggable or connectable-away one at a time here.
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
        for (GraphElement element : frame.getMembers()) {
            Point2D centre = element.getGraphElementCenter();
            element.setNewCoordinates(new Point2D.Double(centre.getX() + dx, centre.getY() + dy));
        }
        canvasModel.syncFusions();
        updateArcCoordinates();
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
