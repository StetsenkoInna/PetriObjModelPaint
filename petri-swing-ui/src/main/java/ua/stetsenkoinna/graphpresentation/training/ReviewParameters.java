package ua.stetsenkoinna.graphpresentation.training;

import java.util.*;
import ua.stetsenkoinna.graphnet.*;

/** Model inputs, including symbolic parameters; deliberately excludes transient simulation statistics. */
public final class ReviewParameters {
    private ReviewParameters() { }
    public record Entry(String id, String name, String kind, Map<String, String> values) {
        public String text() {
            StringBuilder text = new StringBuilder(kind + " - " + name + "\n");
            values.forEach((key, value) -> text.append(key).append(": ").append(value).append("\n"));
            return text.toString();
        }
    }
    private static String value(boolean symbolic, String parameter, Object literal) {
        return symbolic ? "Parameter: " + parameter + " (stored value: " + literal + ")" : String.valueOf(literal);
    }
    public static List<Entry> entries(GraphCanvasModel model) {
        List<Entry> result = new ArrayList<>();
        for (var node : model.getNet().getGraphPetriPlaceList()) {
            var p = node.getPetriPlace();
            result.add(new Entry(node.getType() + ":" + node.getId(), node.getName(), "Place",
                    Map.of("Initial tokens", value(p.markIsParam(), p.getMarkParamName(), p.getMark()))));
        }
        for (var node : model.getNet().getGraphPetriTransitionList()) {
            var t = node.getPetriTransition(); var values = new LinkedHashMap<String, String>();
            values.put("Delay", value(t.parametrIsParam(), t.getParameterParamName(), t.getParameter()));
            values.put("Distribution", value(t.distributionIsParam(), t.getDistributionParamName(), t.getDistribution() == null ? "Constant" : t.getDistribution()));
            values.put("Deviation", String.valueOf(t.getParamDeviation()));
            values.put("Priority", value(t.priorityIsParam(), t.getPriorityParamName(), t.getPriority()));
            values.put("Probability", value(t.probabilityIsParam(), t.getProbabilityParamName(), t.getProbability()));
            result.add(new Entry(node.getType() + ":" + node.getId(), node.getName(), "Transition", values));
        }
        int index = 0;
        for (var arc : model.getNet().getGraphArcInList()) {
            var a = arc.getArcIn(); var values = new LinkedHashMap<String, String>();
            values.put("Weight", value(a.kIsParam(), a.getKParamName(), a.getQuantity()));
            values.put("Informational", value(a.infIsParam(), a.getInfParamName(), a.getIsInf()));
            result.add(new Entry("canvas-arc-" + index++, (arc.getBeginElement() == null ? "Unconnected" : arc.getBeginElement().getName()) + " → " + (arc.getEndElement() == null ? "Unconnected" : arc.getEndElement().getName()), "Input arc", values));
        }
        for (var arc : model.getNet().getGraphArcOutList()) {
            var a = arc.getArcOut();
            result.add(new Entry("canvas-arc-" + index++, (arc.getBeginElement() == null ? "Unconnected" : arc.getBeginElement().getName()) + " → " + (arc.getEndElement() == null ? "Unconnected" : arc.getEndElement().getName()), "Output arc",
                    Map.of("Weight", value(a.kIsParam(), a.getKParamName(), a.getQuantity()))));
        }
        for (int i = 0; i < model.getFrames().size(); i++) {
            var f = model.getFrames().get(i);
            result.add(new Entry("canvas-object-" + i, f.getName(), "Object", Map.of("Priority", String.valueOf(f.getPriority()), "Parent", f.getEnclosing() == null ? "Complete model" : f.getEnclosing().getName())));
        }
        return List.copyOf(result);
    }
}
