/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package graphreuse;

import PetriObj.PetriP;
import java.util.ArrayList;
import javax.swing.table.AbstractTableModel;
import graphnet.GraphPetriPlace;
import utils.Utils;

/**
 *
 * @author Ольга
 */
public class PetriPlaceTableModel extends AbstractTableModel {

    private final int PLACE_PARAMETERS = 2;
    private int row;
    private int column = PLACE_PARAMETERS + 1;
    private Object[][] mass;
    private String[] COLUMN_NAMES = {"Place", "Name", "Markers"};
    private ArrayList<GraphPetriPlace> graphPetriPlaceList;   

    public PetriPlaceTableModel(){

    }
    
    public void setGraphPetriPlaceList(ArrayList<GraphPetriPlace> list) {
        row = list.size();
        graphPetriPlaceList = list;
        this.mass = new Object[this.row][this.column];
        for (int i = 0; i < row; i++) {
            PetriP pp = list.get(i).getPetriPlace();
            mass[i][0] = pp.getName();
            mass[i][1] = pp.getName();
            mass[i][2] = pp.markIsParam() // modified by Katya 08.12.2016
                ? pp.getMarkParamName()
                : pp.getMark();
        }
    }

    @Override
    public int getRowCount() {
        return this.row;
    }

    @Override
    public int getColumnCount() {
        return this.column;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return this.mass[rowIndex][columnIndex];
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

    public ArrayList<GraphPetriPlace> createGraphPetriPlaceList() { // modified by Katya 08.12.2016
        for (int i = 0; i < graphPetriPlaceList.size(); i++) {
            PetriP petriPlace = graphPetriPlaceList.get(i).getPetriPlace();
            petriPlace.setName(getValueAt(i, 1).toString());
            
            String markValueStr = getValueAt(i, 2).toString();
            if (Utils.tryParseInt(markValueStr)) {
                petriPlace.setMark(Integer.valueOf(markValueStr));
                petriPlace.setMarkParam(null);
            } else {
                petriPlace.setMarkParam(markValueStr);
            }
        }
        return graphPetriPlaceList;
    }
}
