package ua.stetsenkoinna.recognition;

import ua.stetsenkoinna.PetriObj.*;
import ua.stetsenkoinna.graphnet.*;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import ua.stetsenkoinna.pnml.CoordinateNormalizer;
import ua.stetsenkoinna.pnml.PnmlParser;

import java.awt.geom.Point2D;
import java.io.File;
import java.util.Map;

public class ModelLoaderService {
    private final PnmlParser pnmlParser;

    public ModelLoaderService(PnmlParser pnmlParser) {
        this.pnmlParser = pnmlParser;
    }

    public GraphPetriNet loadModelFromFile(File modelFile) throws Exception {
        String fileExtension = modelFile.getName().substring(modelFile.getName().lastIndexOf(".") + 1);

        return switch (fileExtension) {
            case "pnml" -> this.importPnml(modelFile);
            case "petriobg" -> this.importPetriObj(modelFile);
            default -> throw new IllegalArgumentException("Unsupported file type: " + fileExtension);
        };
    }

    private GraphPetriNet importPnml(File selectedModelFile) throws Exception {
        PetriNet petriNet = pnmlParser.parse(selectedModelFile);

        Map<Integer, Point2D.Double> placeCoordinates = pnmlParser.getAllPlaceCoordinates();
        Map<Integer, Point2D.Double> transitionCoordinates = pnmlParser.getAllTransitionCoordinates();

        CoordinateNormalizer.NormalizationResult normalization =
                CoordinateNormalizer.normalize(placeCoordinates, transitionCoordinates);

        GraphPetriNet graphNet = new GraphPetriNet();

        for (PetriP place : petriNet.getListP()) {
            GraphPetriPlace graphPlace = new GraphPetriPlace(place, PetriNetsPanel.getIdElement());

            java.awt.geom.Point2D.Double coords = normalization.normalizedPlaceCoordinates.get(place.getNumber());
            if (coords != null) {
                graphPlace.setNewCoordinates(new java.awt.geom.Point2D.Double(coords.x, coords.y));
            } else {
                graphPlace.setNewCoordinates(new java.awt.geom.Point2D.Double(100 + place.getNumber() * 100, 100));
            }

            graphNet.getGraphPetriPlaceList().add(graphPlace);
        }

        for (PetriT transition : petriNet.getListT()) {
            GraphPetriTransition graphTransition = new GraphPetriTransition(transition, PetriNetsPanel.getIdElement());

            java.awt.geom.Point2D.Double coords = normalization.normalizedTransitionCoordinates.get(transition.getNumber());
            if (coords != null) {
                graphTransition.setNewCoordinates(new java.awt.geom.Point2D.Double(coords.x, coords.y));
            } else {
                graphTransition.setNewCoordinates(new java.awt.geom.Point2D.Double(100 + transition.getNumber() * 100, 200));
            }

            graphNet.getGraphPetriTransitionList().add(graphTransition);
        }

        // Create GraphArcIn objects from ArcIn objects
        for (ArcIn arcIn : petriNet.getArcIn()) {
            GraphPetriPlace beginPlace = null;
            GraphPetriTransition endTransition = null;

            // Find corresponding graph elements
            for (GraphPetriPlace gp : graphNet.getGraphPetriPlaceList()) {
                if (gp.getPetriPlace().getNumber() == arcIn.getNumP()) {
                    beginPlace = gp;
                    break;
                }
            }

            for (GraphPetriTransition gt : graphNet.getGraphPetriTransitionList()) {
                if (gt.getPetriTransition().getNumber() == arcIn.getNumT()) {
                    endTransition = gt;
                    break;
                }
            }

            if (beginPlace != null && endTransition != null) {
                GraphArcIn graphArcIn = new GraphArcIn(arcIn);
                graphArcIn.settingNewArc(beginPlace);
                graphArcIn.setEndElement(endTransition);
                graphArcIn.setPetriElements();
                graphArcIn.updateCoordinates();
                graphNet.getGraphArcInList().add(graphArcIn);
            }
        }

        // Create GraphArcOut objects from ArcOut objects
        for (ArcOut arcOut : petriNet.getArcOut()) {
            GraphPetriTransition beginTransition = null;
            GraphPetriPlace endPlace = null;

            // Find corresponding graph elements
            for (GraphPetriTransition gt : graphNet.getGraphPetriTransitionList()) {
                if (gt.getPetriTransition().getNumber() == arcOut.getNumT()) {
                    beginTransition = gt;
                    break;
                }
            }

            for (GraphPetriPlace gp : graphNet.getGraphPetriPlaceList()) {
                if (gp.getPetriPlace().getNumber() == arcOut.getNumP()) {
                    endPlace = gp;
                    break;
                }
            }

            if (beginTransition != null && endPlace != null) {
                GraphArcOut graphArcOut = new GraphArcOut(arcOut);
                graphArcOut.settingNewArc(beginTransition);
                graphArcOut.setEndElement(endPlace);
                graphArcOut.setPetriElements();
                graphArcOut.updateCoordinates();
                graphNet.getGraphArcOutList().add(graphArcOut);
            }
        }

        return graphNet;
    }

    private GraphPetriNet importPetriObj(File selectedModelFile) {
        return new GraphPetriNet();
    }
}
