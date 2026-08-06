package ua.stetsenkoinna.pnml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriObject;
import ua.stetsenkoinna.graphnet.NetTemplateRef;
import ua.stetsenkoinna.petriobj.ExceptionInvalidNetStructure;
import ua.stetsenkoinna.petriobj.ExceptionInvalidTimeDelay;
import ua.stetsenkoinna.petriobj.PetriObjLink;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;

/**
 * Writes a composed {@link GraphPetriObjModel} as a PNML document.
 *
 * <p>The composition is expressed with the structuring PNML already has: one {@code <page>}
 * per Petri-object inside a single {@code <net>}. Everything a plain Petri net reader needs
 * — places, transitions, arcs — stays exactly where it was, so a one-object model is still
 * an ordinary PNML file. What is specific to the Petri-object technique lives in
 * {@code <toolspecific tool="PetriObjModel">} blocks: object name, priority and structure
 * layout on each page, and the list of links at net level.
 *
 * @see PnmlModelParser
 */
public class PnmlModelGenerator {

    /**
     * Writes the model to a PNML file.
     *
     * @param model the model to export
     * @param file destination file
     * @throws ExceptionInvalidNetStructure if one of the object nets is not a valid Petri net
     * @throws ExceptionInvalidTimeDelay if one of the object nets has an invalid time delay
     * @throws Exception if the document cannot be written
     */
    public void generate(GraphPetriObjModel model, File file) throws Exception {
        PnmlGenerator.writeDocument(buildDocument(model), file);
    }

    /**
     * Renders the model as a PNML string, for callers that send it over the wire instead of
     * storing it.
     *
     * @param model the model to export
     * @return the PNML text
     * @throws Exception if the document cannot be built
     */
    public String generateXml(GraphPetriObjModel model) throws Exception {
        return PnmlGenerator.toXml(buildDocument(model));
    }

    private Document buildDocument(GraphPetriObjModel model) throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document document = builder.newDocument();

        Element pnmlElement = document.createElement(PnmlConstants.ELEMENT_PNML);
        pnmlElement.setAttribute(PnmlConstants.ATTR_XMLNS, PnmlConstants.PNML_NAMESPACE);
        document.appendChild(pnmlElement);

        Element netElement = document.createElement(PnmlConstants.ELEMENT_NET);
        netElement.setAttribute(PnmlConstants.ATTR_ID, model.getName());
        netElement.setAttribute(PnmlConstants.ATTR_TYPE, PnmlConstants.PTNET_TYPE);
        netElement.appendChild(createNameElement(document, model.getName()));
        pnmlElement.appendChild(netElement);

        for (int index = 0; index < model.getObjectCount(); index++) {
            netElement.appendChild(createPage(document, model.getObject(index), index));
        }

        netElement.appendChild(createLinksBlock(document, model));
        return document;
    }

    /**
     * Builds the page that carries one Petri-object: its metadata and its net.
     */
    private Element createPage(Document document, GraphPetriObject object, int index)
            throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        Element page = document.createElement(PnmlConstants.ELEMENT_PAGE);
        page.setAttribute(PnmlConstants.ATTR_ID, PnmlConstants.OBJECT_PAGE_ID_PREFIX + index);
        page.appendChild(createNameElement(document, object.getName()));

        Element toolspecific = createToolspecific(document);
        Element objectElement = document.createElement(PnmlConstants.ELEMENT_PETRI_OBJECT);
        objectElement.setAttribute(PnmlConstants.ATTR_INDEX, String.valueOf(index));
        objectElement.setAttribute(PnmlConstants.ATTR_NAME, object.getName());
        objectElement.setAttribute(PnmlConstants.ATTR_PRIORITY, String.valueOf(object.getPriority()));
        objectElement.setAttribute(PnmlConstants.ATTR_X, String.valueOf(object.getPosition().x));
        objectElement.setAttribute(PnmlConstants.ATTR_Y, String.valueOf(object.getPosition().y));
        toolspecific.appendChild(objectElement);

        NetTemplateRef template = object.getTemplate();
        if (template != null) {
            Element templateElement = document.createElement(PnmlConstants.ELEMENT_NET_TEMPLATE);
            templateElement.setAttribute(PnmlConstants.ATTR_METHOD, template.getMethodName());
            for (String argument : template.getArguments()) {
                Element argumentElement = document.createElement(PnmlConstants.ELEMENT_TEMPLATE_ARGUMENT);
                argumentElement.setTextContent(argument);
                templateElement.appendChild(argumentElement);
            }
            toolspecific.appendChild(templateElement);
        }
        page.appendChild(toolspecific);

        // Rebuilding the net makes element numbers match their position in the graph lists,
        // which is what the link declarations address.
        object.getGraphNet().createPetriNet(object.getName());
        new PnmlGenerator().writeNetInto(document, page,
                object.getGraphNet().getPetriNet(), object.getGraphNet());
        return page;
    }

    /**
     * Builds the net-level block listing the links between Petri-objects.
     */
    private Element createLinksBlock(Document document, GraphPetriObjModel model) {
        Element toolspecific = createToolspecific(document);
        Element linksElement = document.createElement(PnmlConstants.ELEMENT_PETRI_OBJECT_LINKS);
        for (PetriObjLink link : model.getLinks()) {
            Element linkElement = document.createElement(PnmlConstants.ELEMENT_LINK);
            linkElement.setAttribute(PnmlConstants.ATTR_LINK_TYPE, linkTypeName(link));
            linkElement.setAttribute(PnmlConstants.ATTR_SOURCE_OBJECT, String.valueOf(link.getSourceObject()));
            linkElement.setAttribute(PnmlConstants.ATTR_SOURCE_ELEMENT, String.valueOf(link.getSourceElement()));
            linkElement.setAttribute(PnmlConstants.ATTR_TARGET_OBJECT, String.valueOf(link.getTargetObject()));
            linkElement.setAttribute(PnmlConstants.ATTR_TARGET_ELEMENT, String.valueOf(link.getTargetElement()));
            switch (link.getType()) {
                case PLACE_FUSION -> { /* a fusion has neither multiplicity nor a test flag */ }
                case TRANSITION_TO_PLACE ->
                        linkElement.setAttribute(PnmlConstants.ATTR_QUANTITY, String.valueOf(link.getQuantity()));
                case PLACE_TO_TRANSITION -> {
                    linkElement.setAttribute(PnmlConstants.ATTR_QUANTITY, String.valueOf(link.getQuantity()));
                    linkElement.setAttribute(PnmlConstants.ATTR_INFORMATIONAL, String.valueOf(link.isInformational()));
                }
            }
            linksElement.appendChild(linkElement);
        }
        toolspecific.appendChild(linksElement);
        return toolspecific;
    }

    /**
     * @return the stable document name of a link type, independent of the enum constant name
     */
    static String linkTypeName(PetriObjLink link) {
        return switch (link.getType()) {
            case PLACE_FUSION -> PnmlConstants.LINK_TYPE_PLACE_FUSION;
            case TRANSITION_TO_PLACE -> PnmlConstants.LINK_TYPE_TRANSITION_TO_PLACE;
            case PLACE_TO_TRANSITION -> PnmlConstants.LINK_TYPE_PLACE_TO_TRANSITION;
        };
    }

    private Element createToolspecific(Document document) {
        Element toolspecific = document.createElement(PnmlConstants.ELEMENT_TOOLSPECIFIC);
        toolspecific.setAttribute(PnmlConstants.ATTR_TOOL, PnmlConstants.TOOL_PETRI_OBJ_MODEL);
        toolspecific.setAttribute(PnmlConstants.ATTR_VERSION, PnmlConstants.TOOL_VERSION_OBJECT_MODEL);
        return toolspecific;
    }

    private Element createNameElement(Document document, String name) {
        Element nameElement = document.createElement(PnmlConstants.ELEMENT_NAME);
        Element textElement = document.createElement(PnmlConstants.ELEMENT_TEXT);
        textElement.setTextContent(name);
        nameElement.appendChild(textElement);
        return nameElement;
    }
}
