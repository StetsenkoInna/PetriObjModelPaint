package ua.stetsenkoinna.graphnet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.stetsenkoinna.petriobj.ExceptionInvalidNetStructure;
import ua.stetsenkoinna.petriobj.ExceptionInvalidTimeDelay;
import ua.stetsenkoinna.petriobj.PetriObjLink;
import ua.stetsenkoinna.petriobj.PetriObjLinkType;
import ua.stetsenkoinna.petriobj.PetriObjModel;
import ua.stetsenkoinna.petriobj.PetriSim;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The graph-level Petri-object model: the Petri-objects a model is composed of and the
 * links between them.
 *
 * <p>This is the structure layer of the editor and the unit of storage — a model is what
 * gets saved to and loaded from PNML. {@link #createPetriObjModel(String)} turns it into
 * the executable {@link PetriObjModel} of the simulation engine.
 *
 * <p>Objects are addressed by their position in {@link #getObjects()}, and so are the
 * endpoints of every {@link PetriObjLink}. Removing an object therefore renumbers the links
 * that point past it and drops the ones attached to it — see {@link #removeObject(int)}.
 */
public class GraphPetriObjModel implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(GraphPetriObjModel.class);

    /** Name given to a model that was built from a single unnamed net. */
    public static final String DEFAULT_NAME = "PetriObjModel";

    private String name;
    private final List<GraphPetriObject> objects = new ArrayList<>();
    private final List<PetriObjLink> links = new ArrayList<>();

    public GraphPetriObjModel() {
        this(DEFAULT_NAME);
    }

    public GraphPetriObjModel(String name) {
        this.name = name == null || name.isBlank() ? DEFAULT_NAME : name;
    }

    /**
     * Wraps a single net as a one-object model — the shape every net drawn before
     * Petri-object composition existed still has.
     *
     * @param graphNet the net to wrap
     * @param name name for both the model and its only object
     */
    public static GraphPetriObjModel singleObject(GraphPetriNet graphNet, String name) {
        GraphPetriObjModel model = new GraphPetriObjModel(name);
        model.addObject(new GraphPetriObject(model.getName(), graphNet));
        return model;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null || name.isBlank() ? DEFAULT_NAME : name;
    }

    /**
     * @return the Petri-objects of this model, in the order that indexes them
     */
    public List<GraphPetriObject> getObjects() {
        return Collections.unmodifiableList(objects);
    }

    /**
     * @return the link declarations of this model
     */
    public List<PetriObjLink> getLinks() {
        return Collections.unmodifiableList(links);
    }

    public int getObjectCount() {
        return objects.size();
    }

    /**
     * @return {@code true} when the model holds exactly one Petri-object and no links, so
     *         it can be presented as a plain net
     */
    public boolean isSingleObject() {
        return objects.size() == 1 && links.isEmpty();
    }

    /**
     * @param index position of the object in this model
     * @return the object at that position
     * @throws IndexOutOfBoundsException if there is no such object
     */
    public GraphPetriObject getObject(int index) {
        return objects.get(index);
    }

    /**
     * @param object the object to look up
     * @return its position in this model, or -1 when it does not belong to it
     */
    public int indexOf(GraphPetriObject object) {
        return objects.indexOf(object);
    }

    /**
     * Appends a Petri-object to the model.
     *
     * @param object the object to add
     * @return the index the object was given
     */
    public int addObject(GraphPetriObject object) {
        objects.add(Objects.requireNonNull(object, "object"));
        return objects.size() - 1;
    }

    /**
     * Removes a Petri-object and every link attached to it, then renumbers the remaining
     * links so they keep pointing at the same objects.
     *
     * @param index position of the object to remove
     * @return the removed object
     * @throws IndexOutOfBoundsException if there is no such object
     */
    public GraphPetriObject removeObject(int index) {
        GraphPetriObject removed = objects.remove(index);
        links.removeIf(link -> link.getSourceObject() == index || link.getTargetObject() == index);
        List<PetriObjLink> renumbered = new ArrayList<>(links.size());
        for (PetriObjLink link : links) {
            renumbered.add(shiftObjects(link,
                    link.getSourceObject() > index ? link.getSourceObject() - 1 : link.getSourceObject(),
                    link.getTargetObject() > index ? link.getTargetObject() - 1 : link.getTargetObject()));
        }
        links.clear();
        links.addAll(renumbered);
        return removed;
    }

    private static PetriObjLink shiftObjects(PetriObjLink link, int sourceObject, int targetObject) {
        return switch (link.getType()) {
            case PLACE_FUSION -> PetriObjLink.placeFusion(
                    sourceObject, link.getSourceElement(), targetObject, link.getTargetElement());
            case TRANSITION_TO_PLACE -> PetriObjLink.transitionToPlace(
                    sourceObject, link.getSourceElement(), targetObject, link.getTargetElement(),
                    link.getQuantity());
            case PLACE_TO_TRANSITION -> PetriObjLink.placeToTransition(
                    sourceObject, link.getSourceElement(), targetObject, link.getTargetElement(),
                    link.getQuantity(), link.isInformational());
        };
    }

    /**
     * Adds a link after checking that both of its endpoints exist.
     *
     * @param link the link to add
     * @throws IllegalArgumentException if an endpoint addresses a missing object or element
     */
    public void addLink(PetriObjLink link) {
        validate(Objects.requireNonNull(link, "link"));
        links.add(link);
    }

    /**
     * @param index position of the link in {@link #getLinks()}
     * @return the removed link
     * @throws IndexOutOfBoundsException if there is no such link
     */
    public PetriObjLink removeLink(int index) {
        return links.remove(index);
    }

    public void clearLinks() {
        links.clear();
    }

    /**
     * Checks that a link can be added to this model.
     *
     * @param link the link to check
     * @throws IllegalArgumentException with a message naming the offending endpoint
     */
    public void validate(PetriObjLink link) {
        GraphPetriObject source = objectFor(link.getSourceObject(), link);
        GraphPetriObject target = objectFor(link.getTargetObject(), link);
        switch (link.getType()) {
            case PLACE_FUSION -> {
                checkPlace(source, link.getSourceElement(), link);
                checkPlace(target, link.getTargetElement(), link);
            }
            case TRANSITION_TO_PLACE -> {
                checkTransition(source, link.getSourceElement(), link);
                checkPlace(target, link.getTargetElement(), link);
            }
            case PLACE_TO_TRANSITION -> {
                checkPlace(source, link.getSourceElement(), link);
                checkTransition(target, link.getTargetElement(), link);
            }
        }
        if (link.getType() == PetriObjLinkType.PLACE_FUSION
                && link.getSourceObject() == link.getTargetObject()) {
            throw new IllegalArgumentException("A place cannot be fused with a place of the same object: " + link);
        }
    }

    private GraphPetriObject objectFor(int index, PetriObjLink link) {
        if (index < 0 || index >= objects.size()) {
            throw new IllegalArgumentException(
                    "Link " + link + " refers to Petri-object " + index
                            + " but the model has " + objects.size());
        }
        return objects.get(index);
    }

    private static void checkPlace(GraphPetriObject object, int index, PetriObjLink link) {
        if (index < 0 || index >= object.getPlaceCount()) {
            throw new IllegalArgumentException(
                    "Link " + link + " refers to place " + index + " but Petri-object '"
                            + object.getName() + "' has " + object.getPlaceCount());
        }
    }

    private static void checkTransition(GraphPetriObject object, int index, PetriObjLink link) {
        if (index < 0 || index >= object.getTransitionCount()) {
            throw new IllegalArgumentException(
                    "Link " + link + " refers to transition " + index + " but Petri-object '"
                            + object.getName() + "' has " + object.getTransitionCount());
        }
    }

    /**
     * Builds the executable model: every object's net is rebuilt from its graph elements,
     * wrapped in a {@link PetriSim}, and the declared links are wired in.
     *
     * @param id identifier handed to the resulting model, may be {@code null}
     * @return a model ready for {@link PetriObjModel#go(double)}
     * @throws ExceptionInvalidNetStructure if one of the nets is not a valid Petri net
     * @throws ExceptionInvalidTimeDelay if one of the nets has an invalid time delay
     */
    public PetriObjModel createPetriObjModel(String id)
            throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        ArrayList<PetriSim> simulators = new ArrayList<>(objects.size());
        for (GraphPetriObject object : objects) {
            simulators.add(createPetriSim(object));
        }
        PetriObjModel model = new PetriObjModel(id, simulators);
        for (PetriObjLink link : links) {
            model.addLink(link);
        }
        return model;
    }

    /**
     * Rebuilds one object's Petri net from its graph elements and wraps it in a simulator.
     *
     * @param object the Petri-object to instantiate
     * @return the simulator carrying the object's name and priority
     * @throws ExceptionInvalidNetStructure if the net is not a valid Petri net
     * @throws ExceptionInvalidTimeDelay if the net has an invalid time delay
     */
    public static PetriSim createPetriSim(GraphPetriObject object)
            throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        object.getGraphNet().createPetriNet(object.getName());
        PetriSim simulator = new PetriSim(object.getGraphNet().getPetriNet());
        simulator.setName(object.getName());
        simulator.setPriority(object.getPriority());
        return simulator;
    }

    /**
     * Drops the links whose endpoints no longer exist — for instance after places or
     * transitions were deleted from an object's net.
     *
     * @return the number of links that were dropped
     */
    public int dropBrokenLinks() {
        int before = links.size();
        links.removeIf(link -> {
            try {
                validate(link);
                return false;
            } catch (IllegalArgumentException invalid) {
                log.warn("Dropping link that no longer fits the model: {}", invalid.getMessage());
                return true;
            }
        });
        return before - links.size();
    }

    @Override
    public String toString() {
        return name + " [" + objects.size() + " objects, " + links.size() + " links]";
    }
}
