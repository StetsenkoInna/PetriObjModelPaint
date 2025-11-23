package ua.stetsenkoinna.pnml;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.io.File;

public class PnmlCoordinatesCaptor {

    public static boolean hasCoordinates(File f) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setNamespaceAware(false);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

            Document doc = dBuilder.parse(f);
            doc.getDocumentElement().normalize();

            NodeList places = doc.getElementsByTagName("place");
            if (!areAllNodesWithCoordinates(places)) {
                return false;
            }

            NodeList transitions = doc.getElementsByTagName("transition");
            if (!areAllNodesWithCoordinates(transitions)) {
                return false;
            }

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    private static boolean areAllNodesWithCoordinates(NodeList nodeList) {
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);

            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;

                NodeList graphicsList = element.getElementsByTagName("graphics");
                if (graphicsList.getLength() == 0) {
                    return false;
                }

                Element graphics = (Element) graphicsList.item(0);

                NodeList positionList = graphics.getElementsByTagName("position");
                if (positionList.getLength() == 0) {
                    return false;
                }

                Element position = (Element) positionList.item(0);
                if (!position.hasAttribute("x") || !position.hasAttribute("y")) {
                    return false;
                }
            }
        }
        return true;
    }
}
