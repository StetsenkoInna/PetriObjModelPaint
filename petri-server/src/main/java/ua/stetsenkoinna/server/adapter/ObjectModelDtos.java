package ua.stetsenkoinna.server.adapter;

import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriObject;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.PetriObjLink;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;
import ua.stetsenkoinna.server.dto.NetParseResultDto;
import ua.stetsenkoinna.server.dto.ObjectModelParseResultDto;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps a parsed {@link GraphPetriObjModel} to the JSON the v2 parse endpoint returns.
 *
 * <p>Places and transitions are listed in the order that indexes them, because that is what
 * a link declaration addresses.
 */
public final class ObjectModelDtos {

    private ObjectModelDtos() {}

    /**
     * @param model the parsed model
     * @return its description: objects with their nets, and the links between them
     */
    public static ObjectModelParseResultDto of(GraphPetriObjModel model) {
        List<ObjectModelParseResultDto.ObjectDto> objects = new ArrayList<>();
        for (int index = 0; index < model.getObjectCount(); index++) {
            objects.add(describe(model.getObject(index), index));
        }

        List<ObjectModelParseResultDto.LinkDto> links = new ArrayList<>();
        for (PetriObjLink link : model.getLinks()) {
            links.add(new ObjectModelParseResultDto.LinkDto(
                    linkTypeName(link),
                    link.getSourceObject(), link.getSourceElement(),
                    link.getTargetObject(), link.getTargetElement(),
                    link.getQuantity(), link.isInformational()
            ));
        }

        return new ObjectModelParseResultDto(model.getName(), objects, links);
    }

    private static ObjectModelParseResultDto.ObjectDto describe(GraphPetriObject object, int index) {
        GraphPetriNet graphNet = object.getGraphNet();

        List<NetParseResultDto.PlaceDto> places = new ArrayList<>();
        for (GraphPetriPlace graphPlace : graphNet.getGraphPetriPlaceList()) {
            PetriP place = graphPlace.getPetriPlace();
            Point2D center = graphPlace.getGraphElementCenter();
            places.add(new NetParseResultDto.PlaceDto(
                    place.getId(), place.getName(), place.getMark(),
                    center != null ? center.getX() : null,
                    center != null ? center.getY() : null
            ));
        }

        List<NetParseResultDto.TransitionDto> transitions = new ArrayList<>();
        for (GraphPetriTransition graphTransition : graphNet.getGraphPetriTransitionList()) {
            PetriT transition = graphTransition.getPetriTransition();
            Point2D center = graphTransition.getGraphElementCenter();
            transitions.add(new NetParseResultDto.TransitionDto(
                    transition.getId(), transition.getName(),
                    transition.getParameter(), transition.getParamDeviation(),
                    transition.getDistribution(), transition.getPriority(), transition.getProbability(),
                    center != null ? center.getX() : null,
                    center != null ? center.getY() : null
            ));
        }

        List<NetParseResultDto.ArcDto> arcs = new ArrayList<>();
        for (ArcIn arc : graphNet.getArcInList()) {
            arcs.add(new NetParseResultDto.ArcDto(
                    arc.getId(), arc.getNameP(), arc.getNameT(),
                    arc.getQuantity(), arc.getIsInf() ? "inhibitor" : "normal"
            ));
        }
        for (ArcOut arc : graphNet.getArcOutList()) {
            arcs.add(new NetParseResultDto.ArcDto(
                    arc.getId(), arc.getNameT(), arc.getNameP(),
                    arc.getQuantity(), "normal"
            ));
        }

        return new ObjectModelParseResultDto.ObjectDto(
                index, object.getName(), object.getPriority(),
                (double) object.getPosition().x, (double) object.getPosition().y,
                object.getTemplate() != null ? object.getTemplate().toString() : null,
                places, transitions, arcs);
    }

    /**
     * @return the wire name of a link type, kept in step with the PNML representation
     */
    private static String linkTypeName(PetriObjLink link) {
        return switch (link.getType()) {
            case PLACE_FUSION -> "placeFusion";
            case TRANSITION_TO_PLACE -> "transitionToPlace";
            case PLACE_TO_TRANSITION -> "placeToTransition";
        };
    }
}
