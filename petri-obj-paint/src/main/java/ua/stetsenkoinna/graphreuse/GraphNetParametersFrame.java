/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ua.stetsenkoinna.graphreuse;

import ua.stetsenkoinna.PetriObj.ExceptionInvalidNetStructure;
import ua.stetsenkoinna.PetriObj.ExceptionInvalidTimeDelay;
import ua.stetsenkoinna.graphpresentation.FileUse;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;

import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphpresentation.PetriNetsFrame;

import java.awt.*;

/**
 *
 * @author Ольга
 */
public class GraphNetParametersFrame extends javax.swing.JFrame {

    /**
     * Creates new form PetriObjectFrame
     */
    public GraphNetParametersFrame() throws ExceptionInvalidNetStructure {
        initComponents();
        this.setLocationRelativeTo(null);
          
        String netName = graphPetriNet.getPetriNet().getName();
        graphNetName = netName.substring(0, netName.length());
        
        placeTableModel = new PetriPlaceTableModel();
        placeTableModel.setGraphPetriPlaceList(graphPetriNet.getGraphPetriPlaceList());
        transitionTableModel = new PetriTransitionTableModel();
        transitionTableModel.setGraphPetriTransitionList(graphPetriNet.getGraphPetriTransitionList());
        tieInTableModel = new PetriArcInTableModel();
        tieInTableModel.setGraphPetriArcInList(graphPetriNet.getGraphArcInList());
        tieOutTableModel = new PetriArcOutTableModel();
        tieOutTableModel.setGraphPetriArcOutList(graphPetriNet.getGraphArcOutList());
        
        petriPlaceTable = new JTable(placeTableModel);
        petriPlaceTable.setRowHeight(ROW_HEIGHT);
        petriPlaceScrollPane.setViewportView(petriPlaceTable);
        petriTransitionTable = new JTable(transitionTableModel);
        petriTransitionTable.setRowHeight(ROW_HEIGHT);
        
        petriTransitionTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN); // added by Katya 08.12.2016
        petriTransitionTable.getColumnModel().getColumn(0).setMinWidth(66);
        petriTransitionTable.getColumnModel().getColumn(0).setMaxWidth(66);
        petriTransitionTable.getColumnModel().getColumn(1).setMinWidth(48);
        petriTransitionTable.getColumnModel().getColumn(1).setMaxWidth(48);
        petriTransitionTable.getColumnModel().getColumn(2).setMinWidth(104);
        petriTransitionTable.getColumnModel().getColumn(2).setMaxWidth(104);
        petriTransitionTable.getColumnModel().getColumn(3).setMinWidth(136);
        petriTransitionTable.getColumnModel().getColumn(3).setMaxWidth(136);
        petriTransitionTable.getColumnModel().getColumn(4).setMinWidth(178);
        petriTransitionTable.getColumnModel().getColumn(4).setMaxWidth(178);
        petriTransitionTable.getColumnModel().getColumn(5).setMinWidth(104);
        petriTransitionTable.getColumnModel().getColumn(5).setMaxWidth(104);
        petriTransitionTable.getColumnModel().getColumn(6).setMinWidth(150);
        petriTransitionTable.getColumnModel().getColumn(6).setMaxWidth(150);
        
        petriTransitionScrollPane.setViewportView(petriTransitionTable);
        tieInTable = new JTable(tieInTableModel);
        tieInTable.setRowHeight(ROW_HEIGHT);
        tieInScrollPane.setViewportView(tieInTable);
        tieOutTable = new JTable(tieOutTableModel);
        tieOutTable.setRowHeight(ROW_HEIGHT);
        tieOutScrollPane.setViewportView(tieOutTable);
        
        TableColumn distributionColumn = petriTransitionTable.getColumnModel().getColumn(transitionTableModel.getDistributionColumnIndex());
        JComboBox<String> distributionComboBox = new JComboBox<>();
        distributionComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(PetriTransitionTableModel.getDISTRIBUTION_OPTIONS()));
        distributionColumn.setCellEditor(new DefaultCellEditor(distributionComboBox));
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        distributionColumn.setCellRenderer(renderer);
        
        TableColumn priorityColumn = petriTransitionTable.getColumnModel().getColumn(transitionTableModel.getPriorityColumnIndex());
        priorityColumn.setCellEditor(new SpinnerEditor());
        
        this.setSize(new Dimension(944, 546)); // added by Katya 08.12.2016
    }
    
    public GraphNetParametersFrame(PetriNetsFrame frame) throws ExceptionInvalidNetStructure {
        try {
            initComponents();
            this.setLocationRelativeTo(null);
            
            graphPetriNet = frame.getPetriNetsPanel().getGraphNet();
            graphPetriNet.createPetriNet(frame.getNameNet());
            
            placeTableModel = new PetriPlaceTableModel();
            placeTableModel.setGraphPetriPlaceList(graphPetriNet.getGraphPetriPlaceList());
            transitionTableModel = new PetriTransitionTableModel();
            transitionTableModel.setGraphPetriTransitionList(graphPetriNet.getGraphPetriTransitionList());
            tieInTableModel = new PetriArcInTableModel();
            tieInTableModel.setGraphPetriArcInList(graphPetriNet.getGraphArcInList());
            tieOutTableModel = new PetriArcOutTableModel();
            tieOutTableModel.setGraphPetriArcOutList(graphPetriNet.getGraphArcOutList());
            
            petriPlaceTable = new JTable(placeTableModel);
            petriPlaceTable.setRowHeight(ROW_HEIGHT);
            petriPlaceScrollPane.setViewportView(petriPlaceTable);
            petriTransitionTable = new JTable(transitionTableModel);
            petriTransitionTable.setRowHeight(ROW_HEIGHT);
            
            petriTransitionTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN); // added by Katya 08.12.2016
            petriTransitionTable.getColumnModel().getColumn(0).setMinWidth(66);
            petriTransitionTable.getColumnModel().getColumn(0).setMaxWidth(66);
            petriTransitionTable.getColumnModel().getColumn(1).setMinWidth(48);
            petriTransitionTable.getColumnModel().getColumn(1).setMaxWidth(48);
            petriTransitionTable.getColumnModel().getColumn(2).setMinWidth(104);
            petriTransitionTable.getColumnModel().getColumn(2).setMaxWidth(104);
            petriTransitionTable.getColumnModel().getColumn(3).setMinWidth(136);
            petriTransitionTable.getColumnModel().getColumn(3).setMaxWidth(136);
            petriTransitionTable.getColumnModel().getColumn(4).setMinWidth(178);
            petriTransitionTable.getColumnModel().getColumn(4).setMaxWidth(178);
            petriTransitionTable.getColumnModel().getColumn(5).setMinWidth(104);
            petriTransitionTable.getColumnModel().getColumn(5).setMaxWidth(104);
            petriTransitionTable.getColumnModel().getColumn(6).setMinWidth(150);
            petriTransitionTable.getColumnModel().getColumn(6).setMaxWidth(150);
            
            petriTransitionScrollPane.setViewportView(petriTransitionTable);
            tieInTable = new JTable(tieInTableModel);
            tieInTable.setRowHeight(ROW_HEIGHT);
            tieInScrollPane.setViewportView(tieInTable);
            tieOutTable = new JTable(tieOutTableModel);
            tieOutTable.setRowHeight(ROW_HEIGHT);
            tieOutScrollPane.setViewportView(tieOutTable);
            
            TableColumn distributionColumn = petriTransitionTable.getColumnModel().getColumn(transitionTableModel.getDistributionColumnIndex());
            JComboBox<String> distributionComboBox = new JComboBox<>();
            distributionComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(PetriTransitionTableModel.getDISTRIBUTION_OPTIONS()));
            distributionColumn.setCellEditor(new DefaultCellEditor(distributionComboBox));
            DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
            distributionColumn.setCellRenderer(renderer);
            
            TableColumn priorityColumn = petriTransitionTable.getColumnModel().getColumn(transitionTableModel.getPriorityColumnIndex());
            priorityColumn.setCellEditor(new SpinnerEditor());
            
            this.setSize(new Dimension(944, 546)); // added by Katya 08.12.2016
        } catch (ExceptionInvalidTimeDelay ex) {
            Logger.getLogger(GraphNetParametersFrame.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        saveAsButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        petriPlaceScrollPane = new javax.swing.JScrollPane();
        petriTransitionScrollPane = new javax.swing.JScrollPane();
        tieInScrollPane = new javax.swing.JScrollPane();
        tieOutScrollPane = new javax.swing.JScrollPane();
        saveButton = new javax.swing.JButton();
		
		setTitle("Edit net parameters");
		
        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        saveAsButton.setText("Save as");
        saveAsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveAsButtonActionPerformed(evt);
            }
        });

        cancelButton.setText("Close");
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        saveButton.setText("Save");
        saveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(tieOutScrollPane, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 427, Short.MAX_VALUE)
                    .addComponent(tieInScrollPane, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 427, Short.MAX_VALUE)
                    .addComponent(petriTransitionScrollPane, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 427, Short.MAX_VALUE)
                    .addComponent(petriPlaceScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 427, Short.MAX_VALUE))
                .addContainerGap())
            .addGroup(layout.createSequentialGroup()
                .addGap(324, 324, 324)
                .addComponent(saveAsButton)
                .addGap(20, 20, 20)
                .addComponent(saveButton)
                .addGap(20, 20, 20)
                .addComponent(cancelButton))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(petriPlaceScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(petriTransitionScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(tieInScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tieOutScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(saveAsButton)
                    .addComponent(cancelButton)
                    .addComponent(saveButton))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void saveAsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveAsButtonActionPerformed
        try {
            stopEditing();
            fileUse = new FileUse();
            graphPetriNet.setGraphPetriPlaceList(placeTableModel.createGraphPetriPlaceList());
            graphPetriNet.setGraphPetriTransitionList(transitionTableModel.createGraphPetriTransitionList());
            graphPetriNet.setGraphArcInList(tieInTableModel.createGraphPetriArcInList());
            graphPetriNet.setGraphArcOutList(tieOutTableModel.createGraphPetriArcOutList());
            fileUse.saveGraphNetAs(graphPetriNet, this);
        } catch (ExceptionInvalidNetStructure ex) {
            Logger.getLogger(GraphNetParametersFrame.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ExceptionInvalidTimeDelay ex) {
            Logger.getLogger(GraphNetParametersFrame.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_saveAsButtonActionPerformed

    private void saveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveButtonActionPerformed
        try {
            stopEditing();
            fileUse = new FileUse();
            graphPetriNet.setGraphPetriPlaceList(placeTableModel.createGraphPetriPlaceList());
            graphPetriNet.setGraphPetriTransitionList(transitionTableModel.createGraphPetriTransitionList());
            graphPetriNet.setGraphArcInList(tieInTableModel.createGraphPetriArcInList());
            graphPetriNet.setGraphArcOutList(tieOutTableModel.createGraphPetriArcOutList());
            fileUse.saveGraphNet(graphPetriNet, graphPetriNet.getPetriNet().getName());
        } catch (ExceptionInvalidNetStructure ex) {
            Logger.getLogger(GraphNetParametersFrame.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_saveButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        this.dispose();
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void stopEditing(){
          JTable[] tables={petriPlaceTable, petriTransitionTable, tieInTable, tieOutTable};
        for (JTable t:tables){
            if (t.isEditing()){
                t.getCellEditor().stopCellEditing();
            }
        }
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JScrollPane petriPlaceScrollPane;
    private javax.swing.JScrollPane petriTransitionScrollPane;
    private javax.swing.JButton saveAsButton;
    private javax.swing.JButton saveButton;
    private javax.swing.JScrollPane tieInScrollPane;
    private javax.swing.JScrollPane tieOutScrollPane;
    // End of variables declaration//GEN-END:variables
    private GraphPetriNet graphPetriNet;
    private JTable petriPlaceTable;
    private JTable petriTransitionTable;
    private JTable tieInTable;
    private JTable tieOutTable;
    private PetriPlaceTableModel placeTableModel;
    private PetriTransitionTableModel transitionTableModel;
    private PetriArcInTableModel tieInTableModel;
    private PetriArcOutTableModel tieOutTableModel;
    private FileUse fileUse;
    private String graphNetName;
    private final int ROW_HEIGHT=22; 
    private final String COPY_NAME="_copy";
}
