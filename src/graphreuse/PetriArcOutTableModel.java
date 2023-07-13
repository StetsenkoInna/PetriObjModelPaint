/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package graphreuse;

import PetriObj.ArcOut;
import java.util.ArrayList;
import javax.swing.table.AbstractTableModel;
import graphnet.GraphArcOut;
import utils.Utils;

/**
 *
 * @author User
 */
public class PetriArcOutTableModel extends AbstractTableModel {

    private final int TIE_PARAMETERS = 1;
    private int row;
    private int column = TIE_PARAMETERS + 1;
    private Object[][] mass;
    private static String[] COLUMN_NAMES = {"Arc", "Number of links"};
    private ArrayList<GraphArcOut> graphPetriArcOutList;

    public PetriArcOutTableModel(){
        
    }
    
    public void setGraphPetriArcOutList(ArrayList<GraphArcOut> list) {
        graphPetriArcOutList = list;
        row = list.size();
        this.mass = new Object[this.row][this.column];
        for (int i = 0; i < row; i++) {
            ArcOut to = list.get(i).getArcOut();
            mass[i][0] = to.getNameT() + " -> " + to.getNameP();
            mass[i][1] = to.kIsParam() // modified by Katya 08.12.2016
                ? to.getKParamName()
                : to.getQuantity();
        }
    }

    @Override
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
        return String.class;
    }

    public ArrayList<GraphArcOut> createGraphPetriArcOutList() { // modified by Katya 08.12.2016
        for (int i = 0; i < graphPetriArcOutList.size(); i++) {
            ArcOut to = graphPetriArcOutList.get(i).getArcOut();
            String quantityValueStr = getValueAt(i, 1).toString();
            if (Utils.tryParseInt(quantityValueStr)) {
                to.setQuantity(Integer.valueOf(quantityValueStr));
                to.setKParam(null);
            } else {
                to.setKParam(quantityValueStr);
            }
        }
        return graphPetriArcOutList;
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
}
