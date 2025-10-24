/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ua.stetsenkoinna.graphreuse;

import ua.stetsenkoinna.PetriObj.PetriT;

import java.util.ArrayList;

import javax.swing.table.AbstractTableModel;

import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.utils.SafeParsingUtils;

/**
 *
 * @author Ольга
 */
public class PetriTransitionTableModel extends AbstractTableModel {

    private final int TRANSITION_PARAMETERS = 7;
    private int row;
    private int column = TRANSITION_PARAMETERS + 1;
    private Object[][] mass;
    private String[] COLUMN_NAMES = {"Transition", "Name", "Time delay value", "Time delay distribution", "Distribution (parameter name)",
        "Transition priority", "Priority (parameter name)", "Activation probability"};
    private ArrayList<GraphPetriTransition> graphPetriTransitionList;
    private static String[] DISTRIBUTION_OPTIONS = {"null", "exp", "unif", "norm"};
    private int distributionColumnIndex = 3;
    private int priorityColumnIndex = 5;

    public PetriTransitionTableModel() {
        
    }

    public void setGraphPetriTransitionList(ArrayList<GraphPetriTransition> list) {
        row = list.size();
        graphPetriTransitionList = list;
        this.mass = new Object[this.row][this.column];
        for (int i = 0; i < row; i++) {
            PetriT pt = list.get(i).getPetriTransition();
            mass[i][0] = pt.getName();
            mass[i][1] = pt.getName();
            mass[i][2] = pt.parametrIsParam() // modified by Katya 08.12.2016
                ? pt.getParametrParamName()
                : pt.getParametr();
            mass[i][3] = pt.getDistribution();
            mass[i][4] = pt.distributionIsParam() // added by Katya 08.12.2016
                ? pt.getDistributionParamName()
                : null;
            mass[i][5] = pt.getPriority();
            mass[i][6] = pt.priorityIsParam() // added by Katya 08.12.2016
                ? pt.getPriorityParamName()
                : null;
            mass[i][7] = pt.probabilityIsParam() // modified by Katya 08.12.2016
                ? pt.getProbabilityParamName()
                : pt.getProbability();
        }
    }

    @Override
    public int getRowCount() {
        return row;
    }

    @Override
    public int getColumnCount() {
        return column;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return this.mass[rowIndex][columnIndex];
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        return col != 0;
    }

    @Override
    public String getColumnName(int col) {
        return COLUMN_NAMES[col];
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
        this.mass[row][col] = value;
        fireTableCellUpdated(row, col);
    }

    @Override
    public Class getColumnClass(int c) { // modified by Katya 08.12.2016
        return (c == 3 || c == 5)
            ? getValueAt(0, c).getClass()
            : String.class;
    }

    public ArrayList<GraphPetriTransition> createGraphPetriTransitionList() { // modified by Katya 08.12.2016
        for (int i = 0; i < graphPetriTransitionList.size(); i++) {
            PetriT pt = graphPetriTransitionList.get(i).getPetriTransition();
            pt.setName(getValueAt(i, 1).toString());
            
            double parametrValue = 0;
            String parametrValueStr = getValueAt(i, 2).toString();
            if (SafeParsingUtils.tryParseDouble(parametrValueStr)) {
                parametrValue = Double.parseDouble(parametrValueStr);
                pt.setParametr(parametrValue);
                pt.setParametrParam(null);
            } else {
                pt.setParametrParam(parametrValueStr);
            }
            
            String distributionValue = getValueAt(i, 3) != null
                ? getValueAt(i, 3).toString()
                : null;
            String distributionParamName = getValueAt(i, 4) != null
                ? getValueAt(i, 4).toString()
                : null;
            if (distributionParamName != null && !distributionParamName.isEmpty()) {
                pt.setDistributionParam(distributionParamName);
            } else {
                pt.setDistribution(distributionValue, parametrValue);
                pt.setDistributionParam(null);
            }
            
            int priorityValue = Integer.parseInt(getValueAt(i, 5).toString());
            String priorityParamName = getValueAt(i, 6) != null
                ? getValueAt(i, 6).toString()
                : null;
            if (priorityParamName != null && !priorityParamName.isEmpty()) {
                pt.setPriorityParam(priorityParamName);
            } else {
                pt.setPriority(priorityValue);
                pt.setPriorityParam(null);
            }
            
            String probabilityValueStr = getValueAt(i, 7).toString();
            if (SafeParsingUtils.tryParseDouble(probabilityValueStr)) {
                pt.setProbability(Double.parseDouble(probabilityValueStr));
                pt.setProbabilityParam(null);
            } else {
                pt.setProbabilityParam(probabilityValueStr);
            }
        }
        return graphPetriTransitionList;
    }

    public int getDistributionColumnIndex() {
        return distributionColumnIndex;
    }

    public void setDistributionColumnIndex(int distributionColumnIndex) {
        this.distributionColumnIndex = distributionColumnIndex;
    }

    public int getPriorityColumnIndex() {
        return priorityColumnIndex;
    }

    public void setPriorityColumnIndex(int priorityColumnIndex) {
        this.priorityColumnIndex = priorityColumnIndex;
    }

    public static String[] getDISTRIBUTION_OPTIONS() {
        return DISTRIBUTION_OPTIONS;
    }

    public static void setDISTRIBUTION_OPTIONS(String[] DISTRIBUTION_OPTIONS) {
        PetriTransitionTableModel.DISTRIBUTION_OPTIONS = DISTRIBUTION_OPTIONS;
    }
}
