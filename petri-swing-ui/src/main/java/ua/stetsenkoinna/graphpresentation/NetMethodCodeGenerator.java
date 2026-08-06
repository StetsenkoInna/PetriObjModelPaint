package ua.stetsenkoinna.graphpresentation;

import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

/**
 * Generates the Java source of a {@code CreateNet…} factory method for a given
 * {@link PetriNet}. The produced text is later compiled by NetLibraryManager, so the
 * exact formatting (tabs, newlines, statement order) is part of the contract and must
 * not change.
 *
 * <p>Extracted from {@code FileUse}, where the same generation was duplicated across
 * three {@code saveNetAsMethod} overloads.
 */
final class NetMethodCodeGenerator {

    private NetMethodCodeGenerator() {}

    /**
     * @return the full method source, including the leading newline that the callers
     *         previously produced via {@code area.setText("\n")} / {@code s = "\n"}.
     */
    static String generate(PetriNet net) {
        StringBuilder s = new StringBuilder("\n");
        s.append("public static PetriNet CreateNet").append(net.getName()).append("(")
                .append(generateArgumentsString(net))
                .append(") throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {\n")
                .append("\t").append("ArrayList<PetriP> d_P = new ArrayList<>();\n")
                .append("\t").append("ArrayList<PetriT> d_T = new ArrayList<>();\n")
                .append("\t").append("ArrayList<ArcIn> d_In = new ArrayList<>();\n")
                .append("\t").append("ArrayList<ArcOut> d_Out = new ArrayList<>();\n");

        for (PetriP P : net.getListP()) {
            String markStr = P.markIsParam()
                ? P.getMarkParamName()
                : Integer.toString(P.getMark());
            s.append("\t").append("d_P.add(new PetriP(").append("\"").append(P.getName()).append("\",").append(markStr).append("));\n");
        }

        int j = 0;
        for (PetriT T : net.getListT()) {
            String parametrStr = T.parametrIsParam()
                ? T.getParameterParamName()
                : Double.toString(T.getParameter());
            s.append("\t").append("d_T.add(new PetriT(").append("\"").append(T.getName()).append("\",").append(parametrStr).append("));\n");
            if (T.getDistribution() != null || T.distributionIsParam()) {
                String distributionStr = T.distributionIsParam()
                    ? T.getDistributionParamName()
                    : T.getDistribution();
                s.append("\t").append("d_T.get(").append(j).append(").setDistribution(\"").append(distributionStr).append("\", d_T.get(").append(j).append(").getTimeServ());\n");
                s.append("\t").append("d_T.get(").append(j).append(").setParamDeviation(").append(T.getParamDeviation()).append(");\n");
            }
            if (T.getPriority() != 0 || T.priorityIsParam()) {
                String priorityStr = T.priorityIsParam()
                    ? T.getPriorityParamName()
                    : Integer.toString(T.getPriority());
                s.append("\t").append("d_T.get(").append(j).append(").setPriority(").append(priorityStr).append(");\n");
            }
            if (T.getProbability() != 1.0 || T.probabilityIsParam()) {
                String probabilityStr = T.probabilityIsParam()
                    ? T.getProbabilityParamName()
                    : Double.toString(T.getProbability());
                s.append("\t").append("d_T.get(").append(j).append(").setProbability(").append(probabilityStr).append(");\n");
            }
            j++;
        }

        j = 0;
        for (ArcIn In : net.getArcIn()) {
            String quantityStr = In.kIsParam()
                ? In.getKParamName()
                : Integer.toString(In.getQuantity());
            s.append("\t").append("d_In.add(new ArcIn(").append("d_P.get(").append(In.getNumP()).append("),").append("d_T.get(").append(In.getNumT()).append("),").append(quantityStr).append("));\n");

            if (In.infIsParam()) {
                s.append("\t").append("d_In.get(").append(j).append(").setInf(").append(In.getInfParamName()).append(");\n");
            } else if (In.getIsInf()) {
                s.append("\t").append("d_In.get(").append(j).append(").setInf(true);\n");
            }
            j++;
        }

        for (ArcOut Out : net.getArcOut()) {
            String quantityStr = Out.kIsParam()
                ? Out.getKParamName()
                : Integer.toString(Out.getQuantity());
            s.append("\t").append("d_Out.add(new ArcOut(").append("d_T.get(").append(Out.getNumT()).append("),").append("d_P.get(").append(Out.getNumP()).append("),").append(quantityStr).append("));\n");
        }

        s.append("\t").append("PetriNet d_Net = new PetriNet(\"").append(net.getName()).append("\",d_P,d_T,d_In,d_Out);\n");

        s.append("\t").append("PetriP.initNext();\n")
                .append("\t").append("PetriT.initNext();\n")
                .append("\t").append("ArcIn.initNext();\n")
                .append("\t").append("ArcOut.initNext();\n")
                .append("\n\t").append("return d_Net;\n");

        s.append("}");
        return s.toString();
    }

    private static String generateArgumentsString(PetriNet net) {
        StringBuilder str = new StringBuilder();
        for (PetriP petriPlace : net.getListP()) {
            if (petriPlace.markIsParam()) {
                str.append("int ").append(petriPlace.getMarkParamName()).append(", ");
            }
        }

        for (ArcIn In : net.getArcIn()) {
            if (In.kIsParam()) {
                str.append("int ").append(In.getKParamName()).append(", ");
            }
            if (In.infIsParam()) {
                str.append("boolean ").append(In.getInfParamName()).append(", ");
            }
        }
        for (ArcOut Out : net.getArcOut()) {
            if (Out.kIsParam()) {
                str.append("int ").append(Out.getKParamName()).append(", ");
            }
        }
        for (PetriT T : net.getListT()) {
            if (T.parametrIsParam()) {
                str.append("double ").append(T.getParameterParamName()).append(", ");
            }
            if (T.distributionIsParam()) {
                str.append("String ").append(T.getDistributionParamName()).append(", ");
            }
            if (T.priorityIsParam()) {
                str.append("int ").append(T.getPriorityParamName()).append(", ");
            }
            if (T.probabilityIsParam()) {
                str.append("double ").append(T.getProbabilityParamName()).append(", ");
            }
        }
        if (str.length() > 2) {
            str = new StringBuilder(str.substring(0, str.length() - 2));
        }
        return str.toString();
    }
}
