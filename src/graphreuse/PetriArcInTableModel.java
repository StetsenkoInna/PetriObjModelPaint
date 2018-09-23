/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package graphreuse;

import PetriObj.ArcIn;
import java.util.ArrayList;
import javax.swing.table.AbstractTableModel;
import graphnet.GraphArcIn;
import utils.Utils;

/**
 *
 * @author Ольга
 */
public class PetriArcInTableModel extends AbstractTableModel {

    private final int TIE_PARAMETERS = 3;
    private int row;
    private int column = TIE_PARAMETERS + 1;
    private Object[][] mass;
    private static String[] COLUMN_NAMES = {"Arc", "Number of links", "Information link", "Information link (parameter name)"};
    private ArrayList<GraphArcIn> graphPetriArcInList;
    private static int isInfColumnIndex = 2;
    private static String isInfColumnName = COLUMN_NAMES[isInfColumnIndex];

    public PetriArcInTableModel(){
        
    }
    
    public void setGraphPetriArcInList(ArrayList<GraphArcIn> list) {
        graphPetriArcInList = list;
        row = list.size();
        this.mass = new Object[this.row][this.column];
        for (int i = 0; i < row; i++) {
            ArcIn ti = list.get(i).getArcIn();
            mass[i][0] = ti.getNameP() + " -> " + ti.getNameT();
            mass[i][1] = ti.kIsParam() // modified by Katya 08.12.2016
                ? ti.getKParamName()
                : ti.getQuantity();
            if (ti.getIsInf() == true) {
                mass[i][2] = true;
            } else {
                mass[i][2] = false;
            }
            mass[i][3] = ti.infIsParam() // added by Katya 08.12.2016
                ? ti.getInfParamName()
                : null;
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

    public boolean isCellEditable(int row, int col) {
        if (col == 0) {
            return false;
        }
        return true;
    }

    @Override
    public String getColumnName(int col) {
        return COLUMN_NAMES[col];
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
        this.mass[row][col] = (Object) value;
        fireTableCellUpdated(row, col);
    }

    @Override
    public Class getColumnClass(int c) { // modified by Katya 08.12.2016
        return c == 2
            ? getValueAt(0, c).getClass()
            : String.class;
    }

    public ArrayList<GraphArcIn> createGraphPetriArcInList() { // modified by Katya 08.12.2016
        for (int i = 0; i < graphPetriArcInList.size(); i++) {
            ArcIn ti = graphPetriArcInList.get(i).getArcIn();
            boolean isInfValue = Boolean.valueOf(getValueAt(i, 2).toString());
            String isInfParamName = getValueAt(i, 3) != null
                ? getValueAt(i, 3).toString()
                : null;
            if (isInfParamName != null && !isInfParamName.isEmpty()) {
                ti.setInfParam(isInfParamName);
            } else {
                ti.setInf(isInfValue);
                ti.setInfParam(null);
            }
            String quantityValueStr = getValueAt(i, 1).toString();
            if (Utils.tryParseInt(quantityValueStr)) {
                ti.setQuantity(Integer.valueOf(quantityValueStr));
                ti.setKParam(null);
            } else {
                ti.setKParam(quantityValueStr);
            }
        }
        return graphPetriArcInList;
    }

    public static int getIsInfColumnIndex() {
        return isInfColumnIndex;
    }

    public static String getIsInfColumnName() {
        return isInfColumnName;
    }

    public static void setIsInfColumnName(String isInfColumnName) {
        PetriArcInTableModel.isInfColumnName = isInfColumnName;
    }
}
