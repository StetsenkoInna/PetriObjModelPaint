package ua.stetsenkoinna.graphreuse;

import ua.stetsenkoinna.PetriObj.ArcIn;
import java.util.ArrayList;
import javax.swing.table.AbstractTableModel;
import ua.stetsenkoinna.graphnet.GraphArcIn;
import ua.stetsenkoinna.utils.SafeParsingUtils;

/**
 *
 * @author Ольга
 */
public class PetriArcInTableModel extends AbstractTableModel {

    private static final int isInfColumnIndex = 2;
    private static final String[] COLUMN_NAMES = {"Arc", "Number of links", "Information link", "Information link (parameter name)"};

    private static String isInfColumnName = COLUMN_NAMES[isInfColumnIndex];

    private final int TIE_PARAMETERS = 3;
    private final int column = TIE_PARAMETERS + 1;

    private int row;
    private Object[][] mass;
    private ArrayList<GraphArcIn> graphPetriArcInList;

    public PetriArcInTableModel(){
        // todo
    }
    
    public void setGraphPetriArcInList(ArrayList<GraphArcIn> list) {
        graphPetriArcInList = list;
        row = list.size();
        this.mass = new Object[this.row][this.column];
        for (int i = 0; i < row; i++) {
            ArcIn ti = list.get(i).getArcIn();
            mass[i][0] = ti.getNameP() + " -> " + ti.getNameT();
            mass[i][1] = ti.kIsParam()
                ? ti.getKParamName()
                : ti.getQuantity();
            if (ti.getIsInf()) {
                mass[i][2] = true;
            } else {
                mass[i][2] = false;
            }
            mass[i][3] = ti.infIsParam()
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
        return col != 0;
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
    public Class getColumnClass(int c) {
        return c == 2
            ? getValueAt(0, c).getClass()
            : String.class;
    }

    public ArrayList<GraphArcIn> createGraphPetriArcInList() {
        for (int i = 0; i < graphPetriArcInList.size(); i++) {
            ArcIn ti = graphPetriArcInList.get(i).getArcIn();
            boolean isInfValue = Boolean.parseBoolean(getValueAt(i, 2).toString());
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
            if (SafeParsingUtils.tryParseInt(quantityValueStr)) {
                ti.setQuantity(Integer.parseInt(quantityValueStr));
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
