package ua.stetsenkoinna.graphnet;

import ua.stetsenkoinna.PetriObj.PetriT;
import ua.stetsenkoinna.graphpresentation.GraphTransition;
import java.awt.Graphics2D;
import java.io.Serializable;

/**
 *
 * @author Инна
 */
public class GraphPetriTransition extends GraphTransition implements Serializable {

    private static int simpleInd = 0;

    private final int id; // UI element ID
    private final PetriT transition;

    public GraphPetriTransition(PetriT T, int i) {
        transition = T;
        id = i;
    }

    public PetriT getPetriTransition() {
        return transition;
    }

    @Override
    public void drawGraphElement(Graphics2D g2) {
        if (transition.getPriority() < 10 && transition.getPriority() >= 0) {
            setWidth(GraphTransition.getDefaultWidth() + transition.getPriority());
        } else if (transition.getPriority() >= 10) {
            setWidth(15);
        }
        super.drawGraphElement(g2);
        int font = 10;

        g2.drawString(
                transition.getName(),
                (float) this.getGraphElement().getCenterX() - (float) (transition.getName().length() * font) / 2,
                (float) this.getGraphElement().getCenterY() - (float) GraphPetriTransition.getHeight() / 2 - (float) GraphPetriTransition.getHeight() / 5
        );

        String parametrString = transition.parametrIsParam()
                ? transition.getParameterParamName()
                : Double.toString(transition.getParameter());

        String distributionString = transition.distributionIsParam()
                ? transition.getDistributionParamName()
                : transition.getDistribution();

        if (transition.getDistribution() != null || transition.distributionIsParam()) {
            g2.drawString(
                    "d=" + parametrString + "(" + distributionString + ")",
                    (float) this.getGraphElement().getCenterX() - (float) (Double.toString(transition.getParameter()).length() * font) / 2,
                    (float) this.getGraphElement().getCenterY() + (float) GraphPetriTransition.getHeight() / 2 + 20
            );
        } else {
            g2.drawString("d=" + parametrString,
                      (float) this.getGraphElement().getCenterX() - (float) (Double.toString(transition.getParameter()).length() * font) / 2,
                      (float) this.getGraphElement().getCenterY() + (float) GraphPetriTransition.getHeight() / 2 + 20);
        }
        g2.drawString("r=" + transition.getProbability(),
                (float) this.getGraphElement().getCenterX() - (float) (Double.toString(transition.getBuffer()).length() * font) / 2,
                (float) this.getGraphElement().getCenterY() + (float) GraphPetriTransition.getHeight() / 2 + 40);
    }

    @Override
    public String getType() { // added by Katya 23.10.2016
        return "GraphPetriTransition";
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public String getName() {
        return this.getPetriTransition().getName();
    }

    @Override
    public int getNumber() {
        return this.getPetriTransition().getNumber();
    }

    public static String setSimpleName() {
        simpleInd++;
        return "T" + simpleInd;
    }

    public static void setNullSimpleName() {
        simpleInd = 0;

    }
}
