package ua.stetsenkoinna.graphnet;

import ua.stetsenkoinna.petriobj.ExceptionInvalidNetStructure;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.ExceptionInvalidTimeDelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * This class owns the information on the graphic elements and provides creation
 * of a Petri net in accordance
 *
 * @author Инна
 */
public class GraphPetriNet implements Cloneable, Serializable {

    private static final Logger log = LoggerFactory.getLogger(GraphPetriNet.class);

    private ArrayList<GraphPetriPlace> graphPetriPlaceList;
    private ArrayList<GraphPetriTransition> graphPetriTransitionList;
    private ArrayList<GraphArcIn> graphArcInList;
    private ArrayList<GraphArcOut> graphArcOutList;
    
    private PetriNet pNet;

    public GraphPetriNet() {
        graphPetriPlaceList = new ArrayList<>();
        graphPetriTransitionList = new ArrayList<>();
        graphArcInList = new ArrayList<>();
        graphArcOutList = new ArrayList<>();
    }

    // конструктор використовується під час копіювання
    public GraphPetriNet(PetriNet net, ArrayList<GraphPetriPlace> grPlaces,
            ArrayList<GraphPetriTransition> grTransitions,
            ArrayList<GraphArcIn> grArcIns,
            ArrayList<GraphArcOut> grArcOuts
    ) {
        graphPetriPlaceList = grPlaces;
        graphPetriTransitionList = grTransitions;
        graphArcInList = grArcIns;
        graphArcOutList = grArcOuts;

        pNet = net; // Немає гарантії, що списки елементів мережі Петрі відповідають спискам графічних елементів
        //використовувати ТІЛЬКИ при копіюванні
    }

    /**
     * Coping constructor
     * @param graphPetriNet: GraphPetriNet to copy
     */
    public GraphPetriNet(GraphPetriNet graphPetriNet) {
        this(graphPetriNet, new java.util.IdentityHashMap<>());
    }

    /**
     * Copying constructor that also reports which new element replaced which old one — for a
     * caller that holds something else pointing at the original instances (a Petri-object
     * frame's membership, a place fusion's two halves) and needs to translate it onto this
     * copy instead of leaving it referencing the net being copied FROM.
     *
     * @param graphPetriNet net to copy
     * @param oldToNew filled in by this constructor: every old place/transition mapped to the
     *        new instance copied from it
     */
    public GraphPetriNet(GraphPetriNet graphPetriNet, Map<GraphElement, GraphElement> oldToNew) {
        graphPetriPlaceList = new ArrayList<>();
        graphPetriTransitionList = new ArrayList<>();
        graphArcInList = new ArrayList<>();
        graphArcOutList = new ArrayList<>();

        // Copy the PetriNet if it exists
        if (graphPetriNet.pNet != null) {
            try {
                pNet = graphPetriNet.pNet.clone();
            } catch (CloneNotSupportedException e) {
                // This should not happen since PetriNet implements Cloneable correctly
                throw new RuntimeException("Failed to clone PetriNet", e);
            }
        } else {
            pNet = null;
        }

        List<GraphElement> elementsToCopy = new ArrayList<>();
        elementsToCopy.addAll(graphPetriNet.graphPetriPlaceList);
        elementsToCopy.addAll(graphPetriNet.graphPetriTransitionList);

        // Use the current method instead of deprecated one
        GraphNetFragment fragment = bulkCopyNoPasteElements(elementsToCopy, graphPetriNet.graphArcInList, graphPetriNet.graphArcOutList);

        // Add copied elements to this net's lists
        for (GraphElement element : fragment.elements) {
            if (element instanceof GraphPetriPlace) {
                graphPetriPlaceList.add((GraphPetriPlace) element);
            } else if (element instanceof GraphPetriTransition) {
                graphPetriTransitionList.add((GraphPetriTransition) element);
            }
        }
        graphArcInList.addAll(fragment.inArcs);
        graphArcOutList.addAll(fragment.outArcs);
        oldToNew.putAll(fragment.oldToNew);
    }

    @Override
    public GraphPetriNet clone() throws CloneNotSupportedException {
        super.clone();
        ArrayList<GraphPetriPlace> copyGraphPlaceList = new ArrayList<>(graphPetriPlaceList);
        ArrayList<GraphPetriTransition> copyGraphTransitionList = new ArrayList<>(graphPetriTransitionList);
        ArrayList<GraphArcIn> copyGraphArcIn = new ArrayList<>(graphArcInList);
        ArrayList<GraphArcOut> copyGraphArcOut = new ArrayList<>(graphArcOutList);

        PetriNet copyNet = pNet.clone();

        return new GraphPetriNet(copyNet,
                copyGraphPlaceList,
                copyGraphTransitionList,
                copyGraphArcIn,
                copyGraphArcOut);
    }
    
    public boolean hasParameters() { // added by Katya 08.12.2016
        return pNet.hasParameters();
    }

    public void print() {
        log.info("Information about GraphPetriNet");
        for (PetriP pp : this.getPetriPList()) {
            pp.printParameters();
        }
        for (PetriT pt : this.getPetriTList()) {
            pt.printParameters();
        }
        for (ArcIn ti : this.getArcInList()) {
            ti.printParameters();
        }
        for (ArcOut to : this.getArcOutList()) {
            to.printParameters();
        }
    }
    
    public PetriNet getPetriNet() {
        return pNet;
    }

    public void setPetriNet(PetriNet net) {
        pNet = net;
    }

    public ArrayList<GraphPetriPlace> getGraphPetriPlaceList() {
        return graphPetriPlaceList;
    }

    public ArrayList<GraphPetriTransition> getGraphPetriTransitionList() {
        return graphPetriTransitionList;
    }

    public ArrayList<GraphArcIn> getGraphArcInList() {
        return graphArcInList;
    }

    public ArrayList<GraphArcOut> getGraphArcOutList() {
        return graphArcOutList;
    }

    public void setGraphPetriPlaceList(ArrayList<GraphPetriPlace> graphPetriPlaceList) {
        this.graphPetriPlaceList = graphPetriPlaceList;
    }

    public void setGraphPetriTransitionList(ArrayList<GraphPetriTransition> graphPetriTransitionList) {
        this.graphPetriTransitionList = graphPetriTransitionList;
    }

    public void setGraphArcInList(ArrayList<GraphArcIn> graphArcInList) {
        this.graphArcInList = graphArcInList;
    }

    public void setGraphArcOutList(ArrayList<GraphArcOut> graphArcOutList) {
        this.graphArcOutList = graphArcOutList;
    }

    public ArrayList<PetriP> getPetriPList() {
        ArrayList<PetriP> array = new ArrayList<>();
        for (GraphPetriPlace e : graphPetriPlaceList) {
            array.add(e.getPetriPlace());
        }
        return array;
    }

    public ArrayList<PetriT> getPetriTList() {
        ArrayList<PetriT> array = new ArrayList<>();
        for (GraphPetriTransition e : graphPetriTransitionList) {
            array.add(e.getPetriTransition());
        }
        return array;
    }

    public ArrayList<ArcIn> getArcInList() {
        ArrayList<ArcIn> array = new ArrayList<>();
        for (GraphArcIn e : graphArcInList) {
            array.add(e.getArcIn());
        }
        return array;
    }

    public ArrayList<ArcOut> getArcOutList() {
        ArrayList<ArcOut> array = new ArrayList<>();
        for (GraphArcOut e : graphArcOutList) {
            array.add(e.getArcOut());
        }
        return array;
    }

    public boolean isCorrectInArcs() {
        for (GraphPetriTransition grT : graphPetriTransitionList) {
            boolean hasInArc = false;
            for (GraphArcIn in : graphArcInList) {
                if (in.getArcIn().getNumT() == grT.getNumber()) {
                    hasInArc = true;
                    break;
                }
            }
            if (!hasInArc) {
                return false;
            }
        }
        return true;
    }

    /**
     * A transition with no output arc at all is a legitimate sink — {@link
     * ua.stetsenkoinna.petriobj.PetriT#createOutP} only warns about one, it does not reject it —
     * so unlike {@link #isCorrectInArcs()} this deliberately is not "every transition must have
     * an output": that would block models that use sinks on purpose. Kept lenient, matching its
     * behavior before {@link #isCorrectInArcs()} was tightened alongside it.
     */
    public boolean isCorrectOutArcs() {
        boolean b = false;
        for (GraphPetriTransition grT : graphPetriTransitionList) {
            for (GraphArcOut out : graphArcOutList) {
                if (out.getArcOut().getNumT() == grT.getNumber()) {
                    b = true;
                    break;
                }
            }
            if (!b) {
                break;
            }
        }
        return b;
    }

    public void createPetriNet(String s) throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        //створюється мережа Петрі у відповідності до графічних елементів
        renumberFromGraph();
        pNet = new PetriNet(s, this.getPetriPList(), this.getPetriTList(), this.getArcInList(), this.getArcOutList());
    }

    /**
     * Numbers every place and transition by its position in this net's own lists — which is what
     * {@link PetriNet} needs, since it indexes its arrays by those numbers — and then re-derives
     * every arc's place and transition number from the element the arc is actually attached to.
     *
     * <p>Deriving each arc's endpoints from its own {@code getBeginElement()}/{@code getEndElement()}
     * references, rather than from the number it last cached, is the whole point. One canvas holds
     * several nets: each Petri-object is numbered from zero independently of every other, so two
     * elements sharing a number is normal rather than exceptional, and the only thing that can tell
     * one of them from the other is which element an arc actually points at. Matching on the cached
     * number instead used to sweep both elements' arcs onto whichever one came last, leaving the
     * other with none and the net rejected as having a transition with no input positions.
     */
    private void renumberFromGraph() {
        for (int j = 0; j < graphPetriPlaceList.size(); j++) {
            graphPetriPlaceList.get(j).getPetriPlace().setNumber(j);
        }
        for (int j = 0; j < graphPetriTransitionList.size(); j++) {
            graphPetriTransitionList.get(j).getPetriTransition().setNumber(j);
        }
        for (GraphArcIn arc : graphArcInList) {
            // An arc still being dragged has no end yet; it is not part of the net until dropped.
            if (arc.getBeginElement() == null || arc.getEndElement() == null) {
                continue;
            }
            arc.getArcIn().setNumP(arc.getBeginElement().getNumber());
            arc.getArcIn().setNumT(arc.getEndElement().getNumber());
        }
        for (GraphArcOut arc : graphArcOutList) {
            if (arc.getBeginElement() == null || arc.getEndElement() == null) {
                continue;
            }
            arc.getArcOut().setNumT(arc.getBeginElement().getNumber());
            arc.getArcOut().setNumP(arc.getEndElement().getNumber());
        }
    }

    public void delGraphElement(GraphElement s) throws ExceptionInvalidNetStructure {
        pNet = null; // тому що порушується структура мережі Петрі
        for (GraphPetriPlace pp : graphPetriPlaceList) {
              if (pp.getId() == s.getId()) {
                boolean b = true;
                while (b) {
                    b = false;
                    for (GraphArcIn arc : graphArcInList) {
                        if (arc.getBeginElement().getId() == s.getId()) {
                            graphArcInList.remove(arc);
                            b = true;
                            break;
                        }
                    }
                }
                b = true;
                while (b) {
                    b = false;
                    for (GraphArcOut arc : graphArcOutList) {
                        if (arc.getEndElement().getId() == s.getId()) {
                            graphArcOutList.remove(arc);
                            b = true;
                            break;
                        }
                    }
                }
                graphPetriPlaceList.remove(pp);
                break;
            }
        }
        for (GraphPetriTransition tt : graphPetriTransitionList) {
            if (tt.getId() == s.getId()) {
                boolean b = true;
                while (b) {
                    b = false;
                    for (GraphArcIn arc : graphArcInList) {
                        if (arc.getEndElement().getId() == s.getId()) {
                            graphArcInList.remove(arc);
                            b = true;
                            break;
                        }
                    }
                }
                b = true;
                while (b) {
                    b = false;
                    for (GraphArcOut arc : graphArcOutList) {
                        if (arc.getBeginElement().getId() == s.getId()) {
                            graphArcOutList.remove(arc);
                            b = true;
                            break;
                        }
                    }
                }
                graphPetriTransitionList.remove(tt);
                break;

            }
        }
     }
    
    /**
     * Makes a copy of an element and adds it into this graph net
     */
    public GraphElement copyElement(GraphElement element) {
        if (element instanceof GraphPetriPlace) {
            GraphPetriPlace newPlace = new GraphPetriPlace(
                    new PetriP(((GraphPetriPlace) element).getPetriPlace()),
                    GraphElementIdGenerator.next()
            );

            newPlace.setNewCoordinates(element.getGraphElementCenter());
            graphPetriPlaceList.add(newPlace);
            return newPlace;
        }

        if (element instanceof GraphPetriTransition) {
            GraphPetriTransition newTransition = new GraphPetriTransition(
                    new PetriT(((GraphPetriTransition) element).getPetriTransition()),
                    GraphElementIdGenerator.next()
            );

            newTransition.setNewCoordinates(element.getGraphElementCenter());
            graphPetriTransitionList.add(newTransition);
            return newTransition;
        }

        return null;
    }
    
    /**
     * Makes a copy of an element without adding it into the graph net
     */
    public GraphElement copyElementNoPaste(GraphElement element) {
        if (element instanceof GraphPetriPlace) {
            GraphPetriPlace newPlace = new GraphPetriPlace(
                    new PetriP(((GraphPetriPlace) element).getPetriPlace()),
                    GraphElementIdGenerator.next()
            );
            newPlace.setNewCoordinates(element.getGraphElementCenter());
            return newPlace;
        }

        if (element instanceof GraphPetriTransition) {
            GraphPetriTransition newTransition = new GraphPetriTransition(
                    new PetriT(((GraphPetriTransition) element).getPetriTransition()),
                    GraphElementIdGenerator.next()
            );
            newTransition.setNewCoordinates(element.getGraphElementCenter());
            return newTransition;
        }
        return null;
    }

    public GraphNetFragment bulkCopyElements(List<GraphElement> elements) {
        // Use the current method to copy elements without adding to lists
        GraphNetFragment fragment = bulkCopyNoPasteElements(elements, graphArcInList, graphArcOutList);

        // Add copied elements to this net's lists (to maintain original behavior)
        for (GraphElement element : fragment.elements) {
            if (element instanceof GraphPetriPlace) {
                graphPetriPlaceList.add((GraphPetriPlace) element);
            } else if (element instanceof GraphPetriTransition) {
                graphPetriTransitionList.add((GraphPetriTransition) element);
            }
        }
        graphArcInList.addAll(fragment.inArcs);
        graphArcOutList.addAll(fragment.outArcs);

        return fragment;
    }
    
    /* a bean representing a fragement of a GraphNet */
    public static class GraphNetFragment {
        public List<GraphElement> elements;
        public List<GraphArcIn> inArcs;
        public List<GraphArcOut> outArcs;
        /** Every source element mapped to the copy made of it — how a caller that also needs
         *  to translate something ELSE that referenced the originals (a frame's membership, a
         *  fusion's places) finds the corresponding new instance. */
        public Map<GraphElement, GraphElement> oldToNew;

        public GraphNetFragment(List<GraphElement> e, List<GraphArcIn> i, List<GraphArcOut> o) {
            this(e, i, o, new java.util.IdentityHashMap<>());
        }

        public GraphNetFragment(List<GraphElement> e, List<GraphArcIn> i, List<GraphArcOut> o,
                                Map<GraphElement, GraphElement> oldToNew) {
            this.elements = e;
            this.inArcs = i;
            this.outArcs = o;
            this.oldToNew = oldToNew;
        }
    }
    
    public GraphNetFragment bulkCopyNoPasteElements(List<GraphElement> elements) {
        return bulkCopyNoPasteElements(elements, graphArcInList, graphArcOutList);
    }
    
    /**
     * Copies elements and arcs connected to them, but doesn't add them into the net
     */
    public GraphNetFragment bulkCopyNoPasteElements(List<GraphElement> elements, List<GraphArcIn> arcInSource, List<GraphArcOut> arcOutSource) {
        Map<GraphPetriTransition, GraphPetriTransition> transitionsCopies = new HashMap<>();
        Map<GraphPetriPlace, GraphPetriPlace> positionCopies = new HashMap<>();

        for (GraphElement element : elements) {
            GraphElement copiedElement = copyElementNoPaste(element);

            if (copiedElement instanceof GraphPetriPlace) {
                positionCopies.put((GraphPetriPlace) element, (GraphPetriPlace) copiedElement);
            } else if (copiedElement instanceof GraphPetriTransition) {
                transitionsCopies.put((GraphPetriTransition) element, (GraphPetriTransition) copiedElement);
            }
        }

        List<GraphArcIn> arcInsToAdd = new ArrayList<>();
        List<GraphArcOut> arcOutsToAdd = new ArrayList<>();

        for (GraphPetriTransition transition : transitionsCopies.keySet()) {
            for (GraphArcIn arc : arcInSource) {
                if (arc.getEndElement().getId() == transition.getId()) {
                    GraphPetriPlace position = positionCopies.get(arc.getBeginElement());

                    if (position != null) {
                        GraphArcIn arcIn = new GraphArcIn(new ArcIn(arc.getArcIn()));
                        arcIn.setEndElement(transitionsCopies.get(transition));
                        arcIn.settingNewArc(position);
                        arcIn.setPetriElements();
                        arcIn.changeBorder();
                        arcIn.updateCoordinates();
                        arcInsToAdd.add(arcIn);
                    }
                }
            }

            for (GraphArcOut arc : arcOutSource) {
                if (arc.getBeginElement().getId() == transition.getId()) {
                    GraphPetriPlace position = positionCopies.get(arc.getEndElement());

                    if (position != null) {
                        GraphArcOut arcOut = new GraphArcOut(new ArcOut(arc.getArcOut()));
                        arcOut.settingNewArc(transitionsCopies.get(transition));
                        arcOut.setEndElement(position);
                        arcOut.setPetriElements();
                        arcOut.changeBorder();
                        arcOut.updateCoordinates();
                        arcOutsToAdd.add(arcOut);
                    }
                }
            }
        }

        // Added logic to handle two arcs between the same place and transition
        // This prevents arc overlapping during simulation
        for (GraphArcOut arcOut : arcOutsToAdd) {
            for (GraphArcIn arcIn : arcInsToAdd) {
                int inBeginId = arcIn.getBeginElement().getId();
                int inEndId = arcIn.getEndElement().getId();
                int outBeginId = arcOut.getBeginElement().getId();
                int outEndId = arcOut.getEndElement().getId();
                if (inBeginId == outEndId && inEndId == outBeginId) {
                    arcIn.twoArcs(arcOut); // two arcs
                    arcIn.updateCoordinates();
                    arcOut.updateCoordinates();
                }
            }
        }

        List<GraphElement> copiedElements = new ArrayList<>(transitionsCopies.values());
        copiedElements.addAll(positionCopies.values());

        Map<GraphElement, GraphElement> oldToNew = new java.util.IdentityHashMap<>();
        oldToNew.putAll(positionCopies);
        oldToNew.putAll(transitionsCopies);

        return new GraphNetFragment(copiedElements, arcInsToAdd, arcOutsToAdd, oldToNew);
    }

    /**
     * Merges another GraphPetriNet into this one by adding all its elements.
     * The elements from the other net will be copied with new IDs to avoid conflicts, and
     * keep the coordinates they already carry — position them before merging.
     * @param other The GraphPetriNet to merge into this one
     */
    public void mergeGraphNet(GraphPetriNet other) {
        if (other == null) {
            return;
        }

        // Create lists of all elements to copy
        List<GraphElement> elementsToCopy = new ArrayList<>();
        elementsToCopy.addAll(other.graphPetriPlaceList);
        elementsToCopy.addAll(other.graphPetriTransitionList);

        // Copy all elements with their arcs
        GraphNetFragment fragment = bulkCopyNoPasteElements(
            elementsToCopy,
            other.graphArcInList,
            other.graphArcOutList
        );

        // The merged net keeps the coordinates it arrives with. It used to be repositioned
        // here to "the right of everything already on the canvas", which was the source of the
        // overlapping messes: the calculation measured only element centres (so shapes, labels
        // and arcs were never accounted for), had no vertical component at all (so every import
        // landed in the same horizontal band), and treated the whole canvas as one blob, so
        // repeated imports marched off-screen. Where a net should land is the caller's
        // business now — a drop point, a click, or the coordinates its own file recorded.

        // Add copied elements to this net's lists
        for (GraphElement element : fragment.elements) {
            if (element instanceof GraphPetriPlace) {
                graphPetriPlaceList.add((GraphPetriPlace) element);
            } else if (element instanceof GraphPetriTransition) {
                graphPetriTransitionList.add((GraphPetriTransition) element);
            }
        }
        graphArcInList.addAll(fragment.inArcs);
        graphArcOutList.addAll(fragment.outArcs);

        // Update arc coordinates after element repositioning
        for (GraphArcIn arcIn : fragment.inArcs) {
            arcIn.updateCoordinates();
        }
        for (GraphArcOut arcOut : fragment.outArcs) {
            arcOut.updateCoordinates();
        }
    }

    public void printStatistics(Consumer<String> output) {

        output.accept("\n Statistics of Petri net places:\n");
        for (GraphPetriPlace grP : graphPetriPlaceList) {
            PetriP P = grP.getPetriPlace();
            output.accept("Place " + P.getName() + ": mean value = " + P.getMean() + "\n"
                    + "         max value = " + P.getObservedMax() + "\n"
                    + "         min value = " + P.getObservedMin() + "\n");
        }
        output.accept("\n Statistics of Petri net transitions:\n");
        for (GraphPetriTransition grT : graphPetriTransitionList) {
            PetriT T = grT.getPetriTransition();
            output.accept("Transition " + T.getName() + " has mean value " + T.getMean() + "\n"
                    + "         max value = " + T.getObservedMax() + "\n"
                    + "         min value = " + T.getObservedMin() + "\n");
        }

    }

    /**
     * Gets the rightmost X coordinate of all elements in the net
     * @return the maximum X coordinate, or 0 if the net is empty
     */
    public double getRightmostX() {
        double maxX = 0.0;

        for (GraphPetriPlace place : graphPetriPlaceList) {
            double x = place.getGraphElementCenter().getX();
            if (x > maxX) {
                maxX = x;
            }
        }
        for (GraphPetriTransition transition : graphPetriTransitionList) {
            double x = transition.getGraphElementCenter().getX();
            if (x > maxX) {
                maxX = x;
            }
        }

        return maxX;
    }

    /**
     * Gets the leftmost X coordinate of all elements in the net
     * @return the minimum X coordinate, or 0 if the net is empty
     */
    public double getLeftmostX() {
        if (graphPetriPlaceList.isEmpty() && graphPetriTransitionList.isEmpty()) {
            return 0.0;
        }

        double minX = Double.MAX_VALUE;

        for (GraphPetriPlace place : graphPetriPlaceList) {
            double x = place.getGraphElementCenter().getX();
            if (x < minX) {
                minX = x;
            }
        }
        for (GraphPetriTransition transition : graphPetriTransitionList) {
            double x = transition.getGraphElementCenter().getX();
            if (x < minX) {
                minX = x;
            }
        }

        return minX;
    }

    public Point getCurrentLocation(){
    	double x;
        double y;
        double placeX = 0.0;
        double placeY = 0.0;
        double transitionX = 0.0;
        double transitionY = 0.0;

        for (GraphPetriPlace place : graphPetriPlaceList) {
            placeX = placeX + place.getGraphElementCenter().getX();
            placeY = placeY + place.getGraphElementCenter().getY();
        }
        for (GraphPetriTransition transition : graphPetriTransitionList) {
            transitionX = transitionX + transition.getGraphElementCenter().getX();
            transitionY = transitionY + transition.getGraphElementCenter().getY();
        }
    	x = (placeX + transitionX) / (graphPetriPlaceList.size() + graphPetriTransitionList.size());
        y = (placeY + transitionY) / (graphPetriPlaceList.size() + graphPetriTransitionList.size());
    	return new Point((int) x, (int) y);
    }

    public void changeLocation(Point newCenter) {
        // змінити центр розташування мережі відповідно до заданої точки
        Point currentCenter = getCurrentLocation();
        double differenceX = currentCenter.getX() - newCenter.getX();
        double differenceY = currentCenter.getY() - newCenter.getY();

        for (GraphPetriPlace place : graphPetriPlaceList) {
            double newX = place.getGraphElementCenter().getX() - differenceX;
            double newY = place.getGraphElementCenter().getY() - differenceY;
            place.setNewCoordinates(new Point((int) newX, (int) newY));
        }
        for (GraphPetriTransition transition : graphPetriTransitionList) {
            double newX = transition.getGraphElementCenter().getX() - differenceX;
            double newY = transition.getGraphElementCenter().getY() - differenceY;
            transition.setNewCoordinates(new Point((int) newX, (int) newY));
        }
        for (GraphArcIn arcIn : graphArcInList) {
            arcIn.updateCoordinates();
        }
        for (GraphArcOut arcOut : graphArcOutList) {
            arcOut.updateCoordinates();
        }
    }
    
    public void setDefaultColorGraphElements(){
        if (!graphPetriPlaceList.isEmpty()) {
            for (GraphPetriPlace e : graphPetriPlaceList) {
                e.setColor(Color.BLACK);

            }
        }
        if (!graphPetriTransitionList.isEmpty()) {
            for (GraphPetriTransition e : graphPetriTransitionList) {
               e.setColor(Color.BLACK);

            }
        }
    }

    /**
     * Detects and fixes overlapping arcs between the same elements going in opposite directions
     * This method should be called after importing PNML or creating a net to prevent arc overlap
     */
    public void fixOverlappingArcs() {
        // Check for overlapping arcs and apply twoArcs logic to separate them
        for (GraphArcOut arcOut : graphArcOutList) {
            for (GraphArcIn arcIn : graphArcInList) {
                // Check if arcs connect the same elements in opposite directions
                int inBeginId = arcIn.getBeginElement().getId();
                int inEndId = arcIn.getEndElement().getId();
                int outBeginId = arcOut.getBeginElement().getId();
                int outEndId = arcOut.getEndElement().getId();

                // If input arc goes from place to transition and output arc goes from same transition to same place
                if (inBeginId == outEndId && inEndId == outBeginId) {
                    // Apply two arcs logic to separate them visually
                    arcIn.twoArcs(arcOut);
                    arcIn.updateCoordinates();
                    arcOut.updateCoordinates();
                }
            }
        }
    }

    public void paintGraphPetriNet(Graphics2D g2, Graphics g) {
        paintGraphPetriNet(g2, g, java.util.Collections.emptySet());
    }

    /**
     * Draws the net, leaving out whatever is in {@code hiddenElements} — a place or transition
     * there is not drawn at all, and an arc is left out the moment either of its ends is, since
     * a line to or from something not on screen would not mean anything. Nothing here decides
     * what belongs in that set: it is how a Petri-object's own eye icon, on the canvas this net
     * is drawn on, actually hides its content without this net needing to know what a
     * Petri-object or an eye icon even is.
     *
     * @param hiddenElements places and transitions to skip drawing
     */
    public void paintGraphPetriNet(Graphics2D g2, Graphics g, java.util.Set<GraphElement> hiddenElements) {
        if (!graphPetriPlaceList.isEmpty()) {
            for (GraphPetriPlace e : graphPetriPlaceList) {
                if (!hiddenElements.contains(e)) {
                    e.drawGraphElement(g2);
                }
            }
        }
        if (!graphPetriTransitionList.isEmpty()) {
            for (GraphPetriTransition e : graphPetriTransitionList) {
                if (!hiddenElements.contains(e)) {
                    e.drawGraphElement(g2);
                }
            }
        }
        if (!graphArcOutList.isEmpty()) {
            for (GraphArcOut a : graphArcOutList) {
                if (!hiddenElements.contains(a.getBeginElement()) && !hiddenElements.contains(a.getEndElement())) {
                    a.drawGraphElement(g2);
                }
            }
        }
        if (!graphArcInList.isEmpty()) {
            for (GraphArcIn a : graphArcInList) {
                if (!hiddenElements.contains(a.getBeginElement()) && !hiddenElements.contains(a.getEndElement())) {
                    a.drawGraphElement(g2);
                }
            }
        }
    }
}
