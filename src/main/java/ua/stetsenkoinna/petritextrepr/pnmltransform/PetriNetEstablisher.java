package ua.stetsenkoinna.petritextrepr.pnmltransform;

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.model.mxGeometry;
import com.mxgraph.util.mxRectangle;
import com.mxgraph.view.mxGraph;
import javax.swing.SwingConstants;

import ua.stetsenkoinna.PetriObj.*;
import ua.stetsenkoinna.graphnet.GraphArcIn;
import ua.stetsenkoinna.graphnet.GraphArcOut;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.petritextrepr.dto.PetriNetDTO;
import ua.stetsenkoinna.petritextrepr.dto.Place;
import ua.stetsenkoinna.petritextrepr.dto.Transition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class PetriNetEstablisher {
    private static final Logger logger = Logger.getLogger(PetriNetEstablisher.class.getName());


    public static GraphPetriNet establishPetriNetFromDto(PetriNetDTO dto) {
        logger.info("Establishing GRAPHICAL components from DTO using JGraphX...");

        dto.getPlaces().sort((p1, p2) -> {
            int tokensComp = Integer.compare(p2.getTokens(), p1.getTokens());
            if (tokensComp != 0) return tokensComp;
            return p1.getName().compareTo(p2.getName());
        });

        Map<Place, PetriP> placeMap = new HashMap<>();
        Map<Transition, PetriT> transitionMap = new HashMap<>();
        ArrayList<PetriP> petriPlaces = new ArrayList<>();
        ArrayList<PetriT> petriTransitions = new ArrayList<>();

        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();

        for (Place place : dto.getPlaces()) {
            PetriP petriP = new PetriP(place.getName(), place.getTokens());
            petriPlaces.add(petriP);
            placeMap.put(place, petriP);
        }
        for (Transition trans : dto.getTransitions()) {
            PetriT petriT = new PetriT(trans.getName(), trans.getTimeDelay());
            petriT.setPriority(trans.getPriority());
            petriT.setProbability(trans.getProbability());
            if (trans.getDistributionLaw() != null) {
                petriT.setDistribution(trans.getDistributionLaw().name().toLowerCase(), trans.getTimeDelay());
            }
            petriTransitions.add(petriT);
            transitionMap.put(trans, petriT);
        }

        ArrayList<ArcIn> arcInList = new ArrayList<>();
        ArrayList<ArcOut> arcOutList = new ArrayList<>();

        for (ua.stetsenkoinna.petritextrepr.dto.ArcImpl.ArcIn arcInDto : dto.getInputArcs()) {
            PetriP sourceP = placeMap.get(arcInDto.getFrom());
            PetriT targetT = transitionMap.get(arcInDto.getTo());
            if (sourceP != null && targetT != null) arcInList.add(new ArcIn(sourceP, targetT, arcInDto.getMultiplicity()));
        }
        for (ua.stetsenkoinna.petritextrepr.dto.ArcImpl.ArcOut arcOutDto : dto.getOutputArcs()) {
            PetriT sourceT = transitionMap.get(arcOutDto.getFrom());
            PetriP targetP = placeMap.get(arcOutDto.getTo());
            if (sourceT != null && targetP != null) arcOutList.add(new ArcOut(sourceT, targetP, arcOutDto.getMultiplicity()));
        }

        PetriNet pNet = null;
        try {
            pNet = new PetriNet(dto.getName() != null ? dto.getName() : "PetriNet", petriPlaces, petriTransitions, arcInList, arcOutList);
        } catch (ExceptionInvalidTimeDelay e) { e.printStackTrace(); }


        mxGraph jgxGraph = new mxGraph();
        Object parent = jgxGraph.getDefaultParent();
        Map<PetriP, Object> placeCellMap = new HashMap<>();
        Map<PetriT, Object> transitionCellMap = new HashMap<>();

        jgxGraph.getModel().beginUpdate();
        try {
            for (PetriP p : petriPlaces) {
                Object vertex = jgxGraph.insertVertex(parent, null, p, 0, 0, 40, 40, "shape=ellipse");
                placeCellMap.put(p, vertex);
            }
            for (PetriT t : petriTransitions) {
                Object vertex = jgxGraph.insertVertex(parent, null, t, 0, 0, 19, 50, "shape=rectangle");
                transitionCellMap.put(t, vertex);
            }
            for (ArcIn arcIn : arcInList) {
                Object source = placeCellMap.get(petriPlaces.get(arcIn.getNumP()));
                Object target = transitionCellMap.get(petriTransitions.get(arcIn.getNumT()));
                if (source != null && target != null) jgxGraph.insertEdge(parent, null, "", source, target);
            }
            for (ArcOut arcOut : arcOutList) {
                Object source = transitionCellMap.get(petriTransitions.get(arcOut.getNumT()));
                Object target = placeCellMap.get(petriPlaces.get(arcOut.getNumP()));
                if (source != null && target != null) jgxGraph.insertEdge(parent, null, "", source, target);
            }
        } finally {
            jgxGraph.getModel().endUpdate();
        }

        jgxGraph.setCellsBendable(false);
        jgxGraph.setCellsResizable(false);
        jgxGraph.setCellsDisconnectable(false);

        mxHierarchicalLayout layout = configureAdaptiveLayout(jgxGraph, petriPlaces.size(), petriTransitions.size());
        layout.execute(parent);

        int padding = 50;
        mxRectangle bounds = jgxGraph.getGraphBounds();
        if (bounds != null) {
            jgxGraph.moveCells(jgxGraph.getChildCells(parent), padding - bounds.getX(), padding - bounds.getY());
        }

        ArrayList<GraphPetriPlace> graphPlaces = new ArrayList<>();
        ArrayList<GraphPetriTransition> graphTransitions = new ArrayList<>();
        ArrayList<GraphArcIn> graphArcIns = new ArrayList<>();
        ArrayList<GraphArcOut> graphArcOuts = new ArrayList<>();
        Map<PetriP, GraphPetriPlace> finalPlaceMap = new HashMap<>();
        Map<PetriT, GraphPetriTransition> finalTransitionMap = new HashMap<>();
        int graphIdCounter = 0;
        for (PetriP p : petriPlaces) {
            Object cell = placeCellMap.get(p);
            mxGeometry geo = jgxGraph.getModel().getGeometry(cell);
            GraphPetriPlace gp = new GraphPetriPlace(p, graphIdCounter++);
            gp.setNewCoordinates(new java.awt.geom.Point2D.Double(geo.getX(), geo.getY()));
            graphPlaces.add(gp);
            finalPlaceMap.put(p, gp);
        }
        for (PetriT t : petriTransitions) {
            Object cell = transitionCellMap.get(t);
            mxGeometry geo = jgxGraph.getModel().getGeometry(cell);
            GraphPetriTransition gt = new GraphPetriTransition(t, graphIdCounter++);
            gt.setNewCoordinates(new java.awt.geom.Point2D.Double(geo.getX(), geo.getY()));
            graphTransitions.add(gt);
            finalTransitionMap.put(t, gt);
        }
        for (ArcIn arcIn : arcInList) {
            PetriP sourceP = petriPlaces.get(arcIn.getNumP());
            PetriT targetT = petriTransitions.get(arcIn.getNumT());
            GraphPetriPlace sourceG = finalPlaceMap.get(sourceP);
            GraphPetriTransition targetG = finalTransitionMap.get(targetT);
            if (sourceG != null && targetG != null) {
                GraphArcIn gaIn = new GraphArcIn(arcIn);
                gaIn.setBeginElement(sourceG);
                gaIn.setEndElement(targetG);
                gaIn.updateCoordinates();
                graphArcIns.add(gaIn);
            }
        }
        for (ArcOut arcOut : arcOutList) {
            PetriT sourceT = petriTransitions.get(arcOut.getNumT());
            PetriP targetP = petriPlaces.get(arcOut.getNumP());
            GraphPetriTransition sourceG = finalTransitionMap.get(sourceT);
            GraphPetriPlace targetG = finalPlaceMap.get(targetP);
            if (sourceG != null && targetG != null) {
                GraphArcOut gaOut = new GraphArcOut(arcOut);
                gaOut.setBeginElement(sourceG);
                gaOut.setEndElement(targetG);
                gaOut.updateCoordinates();
                graphArcOuts.add(gaOut);
            }
        }

        GraphPetriNet net = new GraphPetriNet();
        net.setGraphPetriPlaceList(graphPlaces);
        net.setGraphPetriTransitionList(graphTransitions);
        net.setGraphArcInList(graphArcIns);
        net.setGraphArcOutList(graphArcOuts);
        net.setPetriNet(pNet);
        net.fixOverlappingArcs();

        return net;
    }

    private static mxHierarchicalLayout configureAdaptiveLayout(mxGraph graph, int placeCount, int transitionCount) {
        mxHierarchicalLayout layout = new mxHierarchicalLayout(graph);
        layout.setOrientation(SwingConstants.WEST);
        layout.setDisableEdgeStyle(true);
        layout.setUseBoundingBox(true);

        layout.setInterRankCellSpacing(1);
        layout.setIntraCellSpacing(150);

        layout.setInterHierarchySpacing(1);
        layout.setParallelEdgeSpacing(5);

        layout.setFineTuning(true);
        layout.setResizeParent(true);
        layout.setMoveParent(true);

        return layout;
    }
}