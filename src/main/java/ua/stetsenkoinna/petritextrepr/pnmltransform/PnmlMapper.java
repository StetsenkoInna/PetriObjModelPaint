package ua.stetsenkoinna.petritextrepr.pnmltransform;

import org.w3c.dom.*;
import ua.stetsenkoinna.petritextrepr.dto.PetriNetDTO;
import ua.stetsenkoinna.petritextrepr.dto.Place;
import ua.stetsenkoinna.petritextrepr.dto.Transition;
import ua.stetsenkoinna.petritextrepr.dto.ArcImpl.ArcIn;
import ua.stetsenkoinna.petritextrepr.dto.ArcImpl.ArcOut;
import ua.stetsenkoinna.petritextrepr.dto.mathstats.DistributionLaws;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PnmlMapper {

    private static final Logger logger = Logger.getLogger(PnmlMapper.class.getName());

    public static PetriNetDTO parse(File pnmlFile) throws Exception {
        logger.info("Starting PNML parsing for file: " + pnmlFile.getAbsolutePath());

        Document doc = buildDocument(pnmlFile);
        PetriNetDTO net = new PetriNetDTO();

        Map<String, Place> placesById = parsePlaces(doc, net);
        Map<String, Transition> transitionsById = parseTransitions(doc, net);
        parseArcs(doc, net, placesById, transitionsById);

        logger.info(String.format("PNML parsing finished. Places: %d, Transitions: %d, ArcsIn: %d, ArcsOut: %d",
                net.getPlaces().size(),
                net.getTransitions().size(),
                net.getInputArcs().size(),
                net.getOutputArcs().size()));

        return net;
    }

    private static Document buildDocument(File pnmlFile) throws Exception {
        if (pnmlFile == null || !pnmlFile.exists()) {
            logger.severe("PNML file is null or does not exist");
            throw new IllegalArgumentException("PNML file is null or does not exist");
        }
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(pnmlFile);
        doc.getDocumentElement().normalize();
        logger.fine("Document parsed successfully");
        return doc;
    }


    private static Map<String, Place> parsePlaces(Document doc, PetriNetDTO net) {
        Map<String, Place> placesById = new HashMap<>();
        NodeList placeNodes = doc.getElementsByTagName("place");
        logger.info("Found " + placeNodes.getLength() + " place elements");

        for (int i = 0; i < placeNodes.getLength(); i++) {
            Node node = placeNodes.item(i);
            if (!(node instanceof Element)) continue;

            Element elem = (Element) node;
            String pid = elem.getAttribute("id");

            Place place = buildPlace(elem);
            net.addPlace(place);
            if (pid != null && !pid.isEmpty()) {
                placesById.put(pid, place);
            }

            logger.fine(String.format("Parsed place: id=%s, name=%s, tokens=%d", pid, place.getName(), place.getTokens()));
        }
        return placesById;
    }

    private static Place buildPlace(Element placeElem) {
        Place place = new Place();
        String name = getInnerNameText(placeElem);
        if (name != null) place.setName(name);

        String initialMarking = getChildText(placeElem, "initialMarking");
        if (initialMarking == null) {
            Element imElem = getFirstElementByTagName(placeElem, "initialMarking");
            if (imElem != null) initialMarking = getChildText(imElem, "text");
        }
        if (initialMarking != null) {
            try {
                place.setTokens(Integer.parseInt(initialMarking.trim()));
            } catch (NumberFormatException e) {
                logger.log(Level.WARNING, "Invalid initialMarking '" + initialMarking + "' for place '" + name + "'", e);
            }
        }
        return place;
    }

    private static Map<String, Transition> parseTransitions(Document doc, PetriNetDTO net) {
        Map<String, Transition> transitionsById = new HashMap<>();
        NodeList transNodes = doc.getElementsByTagName("transition");
        int transitionIdCounter = 1;
        logger.info("Found " + transNodes.getLength() + " transition elements");

        for (int i = 0; i < transNodes.getLength(); i++) {
            Node node = transNodes.item(i);
            if (!(node instanceof Element)) continue;

            Element elem = (Element) node;
            String tid = elem.getAttribute("id");

            Transition transition = buildTransition(elem, transitionIdCounter++);
            net.addTransition(transition);

            if (tid != null && !tid.isEmpty()) transitionsById.put(tid, transition);
            logger.fine(String.format("Parsed transition: id=%s, name=%s", tid, transition.getName()));
        }
        return transitionsById;
    }

    private static Transition buildTransition(Element transElem, int id) {
        Transition transition = new Transition(id);
        String name = getInnerNameText(transElem);
        if (name != null) transition.setName(name);
        parseTransitionProperties(transElem, transition);
        return transition;
    }

    private static void parseTransitionProperties(Element transElem, Transition transition) {
        String delayText = getChildText(transElem, "delay");
        if (delayText == null) delayText = getChildText(transElem, "timeDelay");
        if (delayText != null) {
            try {
                transition.setTimeDelay(Double.parseDouble(delayText.trim()));
            } catch (NumberFormatException e) {
                logger.warning("Invalid delay '" + delayText + "' for transition '" + transition.getName() + "'. Using default 0.0.");
                transition.setTimeDelay(0.0);
            }
        }

        String priorityText = getChildText(transElem, "priority");
        if (priorityText != null) {
            try {
                transition.setPriority(Integer.parseInt(priorityText.trim()));
            } catch (NumberFormatException e) {
                logger.warning("Invalid priority '" + priorityText + "' for transition '" + transition.getName() + "'. Using default 1.");
                transition.setPriority(1);
            }
        }

        String probText = getChildText(transElem, "probability");
        if (probText != null) {
            try {
                transition.setProbability(Double.parseDouble(probText.trim()));
            } catch (NumberFormatException e) {
                logger.warning("Invalid probability '" + probText + "' for transition '" + transition.getName() + "'. Using default 1.0.");
                transition.setProbability(1.0);
            }
        }

        String distLawText = getChildText(transElem, "distributionLaw");
        if (distLawText != null && !distLawText.trim().isEmpty()) {
            try {
                DistributionLaws law = DistributionLaws.valueOf(distLawText.trim().toUpperCase());
                transition.setDistributionLaw(law);
                logger.fine("Parsed distributionLaw '" + law + "' for transition '" + transition.getName() + "'");
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid distributionLaw '" + distLawText + "' for transition '" + transition.getName() + "'. Keeping default.");
            }
        }
    }

    private static void parseArcs(Document doc, PetriNetDTO net,
                                  Map<String, Place> placesById,
                                  Map<String, Transition> transitionsById) {
        NodeList arcNodes = doc.getElementsByTagName("arc");
        int arcIdCounter = 1;
        logger.info("Found " + arcNodes.getLength() + " arc elements");

        for (int i = 0; i < arcNodes.getLength(); i++) {
            Node node = arcNodes.item(i);
            if (!(node instanceof Element)) continue;

            Element arcElem = (Element) node;
            String source = arcElem.getAttribute("source");
            String target = arcElem.getAttribute("target");
            if (source == null || target == null) {
                logger.warning("Arc with missing source or target skipped");
                continue;
            }

            int multiplicity = parseArcMultiplicity(arcElem);
            boolean isInhibitor = parseArcInhibitor(arcElem);

            Place fromPlace = placesById.get(source);
            Place toPlace = placesById.get(target);
            Transition fromTrans = transitionsById.get(source);
            Transition toTrans = transitionsById.get(target);

            if (fromPlace != null && toTrans != null) {
                ArcIn arcIn = new ArcIn(arcIdCounter++);
                arcIn.setMultiplicity(multiplicity);
                arcIn.setArcIn(fromPlace, toTrans, isInhibitor);
                net.addInputArc(arcIn);
                logger.fine("Parsed ArcIn: " + fromPlace.getName() + " -> " + toTrans.getName()
                        + (isInhibitor ? " (inhibitor)" : ""));

            } else if (fromTrans != null && toPlace != null) {
                ArcOut arcOut = new ArcOut(arcIdCounter++);
                arcOut.setMultiplicity(multiplicity);
                arcOut.setArcOut(toPlace, fromTrans);

                if (isInhibitor) {
                    logger.warning("Detected inhibitor property on Output Arc (Transition -> Place). Ignoring, as it's logically invalid.");
                }

                fromTrans.addOutputArc(arcOut);
                net.addOutputArc(arcOut);
                logger.fine("Parsed ArcOut: " + fromTrans.getName() + " -> " + toPlace.getName());
            } else {
                logger.warning("Unknown arc endpoints: source='" + source + "', target='" + target + "'");
            }
        }
    }

    private static int parseArcMultiplicity(Element arcElem) {
        int multiplicity = 1;
        String inscription = getChildText(arcElem, "inscription");
        if (inscription == null) {
            Element insElem = getFirstElementByTagName(arcElem, "inscription");
            if (insElem != null) inscription = getChildText(insElem, "text");
        }
        if (inscription != null) {
            try {
                multiplicity = Integer.parseInt(inscription.trim());
            } catch (NumberFormatException e) {
                logger.log(Level.WARNING, "Invalid arc multiplicity '" + inscription + "'", e);
            }
        }
        return multiplicity;
    }

    private static boolean parseArcInhibitor(Element arcElem) {
        String typeAttr = arcElem.getAttribute("type");
        if (typeAttr != null && typeAttr.toLowerCase().contains("inhibitor")) return true;

        String classAttr = arcElem.getAttribute("class");
        if (classAttr != null && classAttr.toLowerCase().contains("inhibitor")) return true;

        String inhibitorText = getChildText(arcElem, "inhibitor");
        return inhibitorText != null && inhibitorText.trim().equalsIgnoreCase("true");
    }


    private static String getInnerNameText(Element parent) {
        Element nameElem = getFirstElementByTagName(parent, "name");
        if (nameElem != null) {
            String text = getChildText(nameElem, "text");
            if (text != null) return text.trim();
            String direct = nameElem.getTextContent();
            if (direct != null && !direct.trim().isEmpty()) return direct.trim();
        }
        String label = getChildText(parent, "label");
        if (label != null) return label.trim();
        String title = getChildText(parent, "title");
        return title != null ? title.trim() : null;
    }

    private static Element getFirstElementByTagName(Element parent, String name) {
        NodeList nl = parent.getElementsByTagName(name);
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n instanceof Element) return (Element) n;
        }
        return null;
    }

    private static String getChildText(Element parent, String childTag) {
        NodeList nl = parent.getElementsByTagName(childTag);
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n instanceof Element) {
                Element e = (Element) n;
                String txt = getChildTextSimple(e, "text");
                if (txt != null) return txt.trim();
                String direct = e.getTextContent();
                if (direct != null && !direct.trim().isEmpty()) return direct.trim();
            }
        }
        return null;
    }

    private static String getChildTextSimple(Element parent, String childTag) {
        NodeList nl = parent.getElementsByTagName(childTag);
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n instanceof Element) {
                String text = n.getTextContent();
                if (text != null && !text.trim().isEmpty()) return text.trim();
            }
        }
        return null;
    }
}