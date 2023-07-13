/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package graphpresentation;

import PetriObj.ExceptionInvalidNetStructure;
import PetriObj.ExceptionInvalidTimeDelay;
import PetriObj.PetriP;
import PetriObj.PetriSim;
import PetriObj.PetriT;
import graphreuse.GraphNetParametersFrame;
import graphpresentation.undoable_edits.AddGraphElementEdit;

import java.awt.*;
import java.awt.event.InputEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import javax.swing.*;

import graphnet.GraphPetriNet;
import graphnet.GraphPetriPlace;
import graphnet.GraphPetriTransition;
import graphpresentation.actions.AnimateEventAction;
import graphpresentation.actions.PlayPauseAction;
import graphpresentation.actions.RewindAction;
import graphpresentation.actions.RunNetAction;
import graphpresentation.actions.RunOneEventAction;
import graphpresentation.actions.StopSimulationAction;

import java.awt.Dialog.ModalityType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ObjectInputStream;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEditSupport;

/**
 *
 * @author Ольга
 */

public class PetriNetsFrame extends javax.swing.JFrame {

    public Timer timer; //timer thats starts repainting while net simulates
    private final MethodNameDialogPanel dialogPanel = new MethodNameDialogPanel();
    private JDialog dialog;
    class MethodNameDialogPanel extends JPanel { // Added by Katya 23.10.2016,
        // modified by Katya 22.11.2016

        private JComboBox<String> combo;
        private final JButton okButton = new JButton("OK");
        private Boolean secondListenerAdded = false; // added by Katya 05.12.2016

        public MethodNameDialogPanel() { // modified by Katya 27.11.2016
            okButton.addActionListener((ActionEvent e) -> {
                okButtonAction();
            });
            combo = new JComboBox<>(); // modified by Katya 27.11.2016
            add(combo);
            add(okButton);
        }

        public void addOkButtonClickHandler(ActionListener listener) { // added by Katya 05.12.2016
            if (!secondListenerAdded) {
                okButton.addActionListener(listener);
                secondListenerAdded = true;
            }
        }

        public void setComboOptions(ArrayList<String> methodNames) { // added by Katya
            combo.setModel(new DefaultComboBoxModel<>(methodNames.toArray(new String[methodNames.size()])));															// 27.11.2016
        }

        public String getFieldText() {
            return combo.getSelectedItem().toString();
        }

        private void okButtonAction() {
            Window win = SwingUtilities.getWindowAncestor(this);
            if (win != null) {
                win.dispose();
            }
        }
    }
    
    /* ACTIONS */
    private final AnimationControls animationContols = new AnimationControls(this);
    private final RunNetAction runNetAction = animationContols.runNetAction;
    public final RewindAction rewindAction = animationContols.rewindAction;
    public final StopSimulationAction stopSimulationAction = animationContols.stopSimulationAction;
    public final PlayPauseAction playPauseAction = animationContols.playPauseAction;
    public final RunOneEventAction runOneEventAction = animationContols.runOneEventAction;
    public final AnimateEventAction animateEventAction = animationContols.animateEventAction; 

    private void UpdateNetLibraryMethodsCombobox() { // added by Katya
        // 27.11.2016
        ArrayList<String> methodNamesList = new ArrayList<>();
        FileInputStream fis = null;
        try {
            String libraryText = "";
            Path path = FileSystems.getDefault().getPath(
                    System.getProperty("user.dir"),"src","LibNet", "NetLibrary.java"); //added by Inna 29.09.2018
            String pathNetLibrary = path.toString();
            fis = new FileInputStream(pathNetLibrary);  // edit by Inna 29.09.2018
            int content;
            while ((content = fis.read()) != -1) {
                libraryText += (char) content;
            }
            Pattern pattern = Pattern.compile(Pattern
                    .quote("public static PetriNet CreateNet")
                    + "(\\w+\\([^\\)]*\\))"
                    + Pattern.quote(" throws"));
            Matcher matcher = pattern.matcher(libraryText);
            while (matcher.find()) {
                methodNamesList.add("CreateNet" + matcher.group(1));
            }
        } catch (FileNotFoundException e) {
            System.out.println("Method not found");
        } catch (IOException ex) {
            Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE,
                    null, ex);
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(
                        Level.SEVERE, null, ex);
            }
        }
        Collections.sort(methodNamesList, String.CASE_INSENSITIVE_ORDER);
        leftMenuListModel.clear();
        for (String name : methodNamesList) {
            leftMenuListModel.addElement(name);
        }
        dialogPanel.setComboOptions(methodNamesList);
    }

    /**
     * Creates new form PetriNetsFrame
     */
    public PetriNetsFrame() {
        initComponents();
        this.UpdateNetLibraryMethodsCombobox();
        timer = new Timer(250, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                getPetriNetsPanel().repaint();
            }
        });

    /*    consistBtn = createPtrnButton("Consistency", "Shared data access");  // should be added later in special bar
        consistBtn.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ptrnButtonActionPerformed(evt, "Consistency");
            }
        });
        poolBtn = createPtrnButton("pool", "Thread pool");
        poolBtn.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ptrnButtonActionPerformed(evt, "pool");
            }
        });
        newThreadBtn = createPtrnButton("newThread", "Thread's creating, starting, ending");
        newThreadBtn.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ptrnButtonActionPerformed(evt, "newThread");
            }
        });
        lockBtn = createPtrnButton("Lock", "Thread's locking");
        lockBtn.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ptrnButtonActionPerformed(evt, "Lock");
            }
        });
        guardBtn = createPtrnButton("guardedBlock", "Guarded block(wait/notify)");
        guardBtn.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ptrnButtonActionPerformed(evt, "guardedBlock");
            }
        });
        petriNetsFrameToolBar.add(newThreadBtn);
        petriNetsFrameToolBar.add(lockBtn);
        petriNetsFrameToolBar.add(guardBtn);
        petriNetsFrameToolBar.add(consistBtn);
        petriNetsFrameToolBar.add(poolBtn);*/

        newPlaceButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        newPlaceButton.setVerticalTextPosition(javax.swing.SwingConstants.CENTER);
        newPlaceButton.setText("Place");
        newPlaceButton.setBorder(null);
        newPlaceButton.setMargin(new Insets(0, 0, 0, 0));
//        newPlaceButton.setContentAreaFilled(false);
//        newPlaceButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/utils/pl.png")));

        newArcButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        newArcButton.setVerticalTextPosition(javax.swing.SwingConstants.CENTER);
        newArcButton.setText("Arc");
        newArcButton.setBorder(null);
        newArcButton.setMargin(new Insets(0, 0, 0, 0));
//        newArcButton.setContentAreaFilled(false);
//        newArcButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/utils/Arc.png")));

        newTransitionButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        newTransitionButton.setVerticalTextPosition(javax.swing.SwingConstants.CENTER);
        newTransitionButton.setText("Transition");

        newTransitionButton.setBorder(null);
        newTransitionButton.setMargin(new Insets(0, 0, 0, 0));
//        newTransitionButton.setContentAreaFilled(false);
//        newTransitionButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/utils/trans.png")));
//                try {
//                   
//                    //Image img = ImageIO.read(new File(new File(".").getCanonicalPath() + "\\src\\utils\\pl.PNG"));
//                    //Image img1 = ImageIO.read(new File(new File(".").getCanonicalPath() + "\\src\\utils\\arc.PNG"));
//                   // Image img2 = ImageIO.read(new File(new File(".").getCanonicalPath() + "\\src\\utils\\trans.PNG"));
//                   // newPlaceButton.setIcon(new ImageIcon(img));
//                   // newTransitionButton.setIcon(new ImageIcon(img2));
//                    //newArcButton.setIcon(new ImageIcon(img1));
//                } catch (IOException ex) {
//                    Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
//                }

        petriNetsPanel = new PetriNetsPanel(netNameTextField);
        petriNetPanelScrollPane.setViewportView(petriNetsPanel);

        this.setLocation(50, 50);

        this.setExtendedState(JFrame.MAXIMIZED_BOTH / 2);
        this.setTitle("Discrete Event Simulation System ");
        this.setSize(1000, 700);
        
        undoSupport.addUndoableEditListener((event) -> {
            undoManager.addEdit(event.getEdit());
            undoMenuItem.setEnabled(undoManager.canUndo());
            redoMenuItem.setEnabled(undoManager.canRedo());
        });
    }
    
    private JButton createPtrnButton(String title, String tooltip) {

        javax.swing.JButton btn = new javax.swing.JButton();
        btn.setFont(new java.awt.Font("Arial", 0, 14)); // NOI18N
        btn.setToolTipText(tooltip);
        btn.setBorder(javax.swing.BorderFactory.createEmptyBorder(1,
                10, 1, 10));
        btn.setFocusable(false);
        btn.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btn.setVerticalTextPosition(javax.swing.SwingConstants.CENTER);
        btn.setBorder(null);
        btn.setMargin(new Insets(0, 0, 0, 0));
        btn.setContentAreaFilled(false);
        btn.setIcon(new javax.swing.ImageIcon(getClass().getResource("/utils/" + title + ".png")));

        return btn;
    }

    private void ptrnButtonActionPerformed(java.awt.event.ActionEvent evt, String fileName) {
        FileInputStream fis = null;
        ObjectInputStream ois = null;
        try {
            //System.out.println(new File(".").getCanonicalPath() + "\\src\\main\\resources\\" + fileName + ".pns");
            fis = new FileInputStream(new File(".").getCanonicalPath() + "\\src\\utils\\" + fileName + ".pns");

            ois = new ObjectInputStream(fis);
            GraphPetriNet net = ((GraphPetriNet) ois.readObject()).clone();  //
            getPetriNetsPanel().addGraphNet(net); //
            ois.close();

            getPetriNetsPanel().repaint();

        } catch (FileNotFoundException e) {
            System.out.println("Such file was not found");
        } catch (ClassNotFoundException | IOException ex) {
            Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
        } catch (CloneNotSupportedException ex) {
            Logger.getLogger(FileUse.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                fis.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            } catch (NullPointerException e) {

            }
            try {
                ois.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            } catch (NullPointerException e) {

            }
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed"
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jTabbedPane1 = new javax.swing.JTabbedPane();
        petriNetDesign = new javax.swing.JPanel();
        modelingParametersPanel = new javax.swing.JPanel();
        netNameLabel = new javax.swing.JLabel();
        netNameTextField = new javax.swing.JTextField();
        timeStartLabel = new javax.swing.JLabel();
        timeStartField = new javax.swing.JTextField();
        timeModelingLabel = new javax.swing.JLabel();
        timeModelingTextField = new javax.swing.JTextField();
        speedLabel = new javax.swing.JLabel();
        speedSlider = new javax.swing.JSlider();
        playPauseAnimationButton = new javax.swing.JButton();
        stopAnimationButton = new javax.swing.JButton();
        skipBackwardAnimationButton = new javax.swing.JButton();
        skipForwardAnimationButton = new javax.swing.JButton();
        runOneEventButton = new javax.swing.JButton();
        petriNetsFrameToolBar = new javax.swing.JToolBar();
        newPlaceButton = new javax.swing.JButton();
        newTransitionButton = new javax.swing.JButton();
        newArcButton = new javax.swing.JButton();
        petriNetsFrameSplitPane = new javax.swing.JSplitPane();
        petriNetPanelScrollPane = new javax.swing.JScrollPane();
        modelingResultsPanel = new javax.swing.JPanel();
        modelingResultsSplitPane = new javax.swing.JSplitPane();
        protokolScrollPane = new javax.swing.JScrollPane();
        protocolTextArea = new javax.swing.JTextArea();
        statisticsScrollPane = new javax.swing.JScrollPane();
        statisticsTextArea = new javax.swing.JTextArea();
        leftNenuPanel = new javax.swing.JPanel();
        scrollPane = new javax.swing.JScrollPane();
        leftMenuList = new javax.swing.JList<String>();
        petriNetDesign1 = new javax.swing.JPanel();
        petriNetsFrameToolBar1 = new javax.swing.JToolBar();
        newPlaceButton1 = new javax.swing.JButton();
        newTransitionButton1 = new javax.swing.JButton();
        newArcButton1 = new javax.swing.JButton();
        runPetriNetButton1 = new javax.swing.JButton();
        runEventButton1 = new javax.swing.JButton();
        petriNetsFrameSplitPane1 = new javax.swing.JSplitPane();
        petriNetPanelScrollPane1 = new javax.swing.JScrollPane();
        modelingResultsPanel1 = new javax.swing.JPanel();
        modelingResultsSplitPane1 = new javax.swing.JSplitPane();
        protokolScrollPane1 = new javax.swing.JScrollPane();
        protokolTextArea1 = new javax.swing.JTextArea();
        statisticsScrollPane1 = new javax.swing.JScrollPane();
        statisticsTextArea1 = new javax.swing.JTextArea();
        modelingParametersPanel1 = new javax.swing.JPanel();
        netNameLabel1 = new javax.swing.JLabel();
        netNameTextField1 = new javax.swing.JTextField();
        timeStartLabel1 = new javax.swing.JLabel();
        timeStartField1 = new javax.swing.JTextField();
        timeModelingLabel1 = new javax.swing.JLabel();
        timeModelingTextField1 = new javax.swing.JTextField();
        jPanel1 = new javax.swing.JPanel();
        petriNetDesign2 = new javax.swing.JPanel();
        petriNetsFrameToolBar2 = new javax.swing.JToolBar();
        newPlaceButton2 = new javax.swing.JButton();
        newTransitionButton2 = new javax.swing.JButton();
        newArcButton2 = new javax.swing.JButton();
        runPetriNetButton2 = new javax.swing.JButton();
        runEventButton2 = new javax.swing.JButton();
        petriNetsFrameSplitPane2 = new javax.swing.JSplitPane();
        petriNetPanelScrollPane2 = new javax.swing.JScrollPane();
        modelingResultsPanel2 = new javax.swing.JPanel();
        modelingResultsSplitPane2 = new javax.swing.JSplitPane();
        protokolScrollPane2 = new javax.swing.JScrollPane();
        protokolTextArea2 = new javax.swing.JTextArea();
        statisticsScrollPane2 = new javax.swing.JScrollPane();
        statisticsTextArea2 = new javax.swing.JTextArea();
        modelingParametersPanel2 = new javax.swing.JPanel();
        netNameLabel2 = new javax.swing.JLabel();
        netNameTextField2 = new javax.swing.JTextField();
        timeStartLabel2 = new javax.swing.JLabel();
        timeStartField2 = new javax.swing.JTextField();
        timeModelingLabel2 = new javax.swing.JLabel();
        timeModelingTextField2 = new javax.swing.JTextField();
        petriNetsFrameMenuBar = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        openMenuItem = new javax.swing.JMenuItem();
        newMenuItem = new javax.swing.JMenuItem();
        openMethodMenuItem = new javax.swing.JMenuItem();
        editMenu = new javax.swing.JMenu();
        editNetParameters = new javax.swing.JMenuItem();
        centerLocationOfGraphNet = new javax.swing.JMenuItem();
        undoMenuItem = new javax.swing.JMenuItem();
        redoMenuItem = new javax.swing.JMenuItem();
        save = new javax.swing.JMenu();
        SaveGraphNet = new javax.swing.JMenuItem();
        jMenuItem2 = new javax.swing.JMenuItem();
        SavePetriNetAs = new javax.swing.JMenuItem();
        SaveNetAsMethod = new javax.swing.JMenuItem();
        SaveMethodInNetLibrary = new javax.swing.JMenuItem();
        Animate = new javax.swing.JMenu();
        itemAnimateNet = new javax.swing.JMenuItem();
        itemAnimateEvent = new javax.swing.JMenuItem();
        runMenu = new javax.swing.JMenu();
        javax.swing.JMenuItem itemRunNet = new javax.swing.JMenuItem();
        itemRunEvent = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jTabbedPane1.setBackground(new java.awt.Color(255, 255, 255));
        jTabbedPane1.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        jTabbedPane1.setFont(new java.awt.Font("Arial", 1, 12)); // NOI18N

        netNameLabel.setFont(new java.awt.Font("Arial", 0, 11)); // NOI18N
        netNameLabel.setText("Net name");
        netNameLabel.setMinimumSize(new java.awt.Dimension(0, 0));

        netNameTextField.setFont(new java.awt.Font("Arial", 0, 14)); // NOI18N
        netNameTextField.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        netNameTextField.setText("Untitled");
        netNameTextField.setCaretPosition(1);
        netNameTextField.setMinimumSize(new java.awt.Dimension(0, 0));
        netNameTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                netNameTextFieldActionPerformed(evt);
            }
        });

        timeStartLabel.setBackground(new java.awt.Color(192, 192, 192));
        timeStartLabel.setFont(new java.awt.Font("Arial", 0, 11)); // NOI18N
        timeStartLabel.setText("Time start");

        timeStartField.setFont(new java.awt.Font("Arial", 0, 14)); // NOI18N
        timeStartField.setHorizontalAlignment(javax.swing.JTextField.LEFT);
        timeStartField.setText("0");
        timeStartField.setMinimumSize(new java.awt.Dimension(0, 0));
        timeStartField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timeStartFieldActionPerformed(evt);
            }
        });

        timeModelingLabel.setBackground(new java.awt.Color(247, 247, 247));
        timeModelingLabel.setFont(new java.awt.Font("Arial", 0, 11)); // NOI18N
        timeModelingLabel.setText("Time modeling");

        timeModelingTextField.setFont(new java.awt.Font("Arial", 0, 14)); // NOI18N
        timeModelingTextField.setHorizontalAlignment(javax.swing.JTextField.LEFT);
        timeModelingTextField.setText("1000");
        timeModelingTextField.setCaretPosition(1);
        timeModelingTextField.setMinimumSize(new java.awt.Dimension(0, 0));

        speedLabel.setFont(new java.awt.Font("Arial", 0, 11)); // NOI18N
        speedLabel.setText("Animation speed");

        speedSlider.setMaximum(1000);
        speedSlider.setValue(1000);
        speedSlider.setInverted(true);
        speedSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                speedSliderStateChanged(evt);
            }
        });

        playPauseAnimationButton.setAction(playPauseAction);
        playPauseAnimationButton.setMargin(new java.awt.Insets(14, 25, 14, 25));
        playPauseAnimationButton.setMaximumSize(new java.awt.Dimension(50, 50));
        playPauseAnimationButton.setMinimumSize(new java.awt.Dimension(50, 50));
        playPauseAnimationButton.setPreferredSize(new java.awt.Dimension(50, 50));

        stopAnimationButton.setAction(stopSimulationAction);
        stopAnimationButton.setText("⏹");

        skipBackwardAnimationButton.setAction(rewindAction);
        skipBackwardAnimationButton.setText("⏮");

        skipForwardAnimationButton.setAction(runNetAction);
        skipForwardAnimationButton.setText("⏭");

        runOneEventButton.setAction(runOneEventAction);
        runOneEventButton.setText("⏩");

        javax.swing.GroupLayout modelingParametersPanelLayout = new javax.swing.GroupLayout(modelingParametersPanel);
        modelingParametersPanel.setLayout(modelingParametersPanelLayout);
        modelingParametersPanelLayout.setHorizontalGroup(
            modelingParametersPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(modelingParametersPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(netNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 148, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(netNameTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 148, Short.MAX_VALUE)
                .addGap(10, 10, 10)
                .addComponent(timeStartLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 194, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(timeStartField, javax.swing.GroupLayout.DEFAULT_SIZE, 148, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(timeModelingLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 217, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(timeModelingTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 147, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(speedLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 92, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(speedSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 163, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(skipBackwardAnimationButton, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(playPauseAnimationButton, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(stopAnimationButton, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(runOneEventButton, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(skipForwardAnimationButton, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        modelingParametersPanelLayout.setVerticalGroup(
            modelingParametersPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(modelingParametersPanelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(modelingParametersPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, modelingParametersPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE, false)
                        .addComponent(netNameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(timeStartLabel)
                        .addComponent(timeStartField, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(timeModelingLabel)
                        .addComponent(timeModelingTextField, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(netNameTextField, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(speedLabel))
                    .addComponent(speedSlider, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
            .addComponent(playPauseAnimationButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(stopAnimationButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(runOneEventButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(skipForwardAnimationButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(skipBackwardAnimationButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        timeStartLabel.getAccessibleContext().setAccessibleName("Time");

        petriNetsFrameToolBar.setBorder(null);
        petriNetsFrameToolBar.setFloatable(false);
        petriNetsFrameToolBar.setRollover(true);
        petriNetsFrameToolBar.setFont(new java.awt.Font("Arial", 0, 12)); // NOI18N
        petriNetsFrameToolBar.setMargin(new java.awt.Insets(0, 10, 0, 10));

        newPlaceButton.setFont(new java.awt.Font("Arial", 0, 14)); // NOI18N
        newPlaceButton.setText("Place");
        newPlaceButton.setToolTipText("");
        newPlaceButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 10, 1, 10));
        newPlaceButton.setFocusable(false);
        newPlaceButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        newPlaceButton.setMaximumSize(new java.awt.Dimension(103, 19));
        newPlaceButton.setMinimumSize(new java.awt.Dimension(103, 19));
        newPlaceButton.setPreferredSize(new java.awt.Dimension(101, 19));
        newPlaceButton.setRequestFocusEnabled(false);
        newPlaceButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        newPlaceButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newPlaceButtonActionPerformed(evt);
            }
        });
        petriNetsFrameToolBar.add(newPlaceButton);

        newTransitionButton.setFont(new java.awt.Font("Arial", 0, 14)); // NOI18N
        newTransitionButton.setText("Transition");
        newTransitionButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 10, 1, 10));
        newTransitionButton.setFocusable(false);
        newTransitionButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        newTransitionButton.setMaximumSize(new java.awt.Dimension(103, 19));
        newTransitionButton.setMinimumSize(new java.awt.Dimension(103, 19));
        newTransitionButton.setPreferredSize(new java.awt.Dimension(101, 19));
        newTransitionButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        newTransitionButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newTransitionButtonActionPerformed(evt);
            }
        });
        petriNetsFrameToolBar.add(newTransitionButton);

        newArcButton.setFont(new java.awt.Font("Arial", 0, 14)); // NOI18N
        newArcButton.setText("Arc");
        newArcButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 10, 1, 10));
        newArcButton.setFocusable(false);
        newArcButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        newArcButton.setMaximumSize(new java.awt.Dimension(103, 19));
        newArcButton.setMinimumSize(new java.awt.Dimension(103, 19));
        newArcButton.setPreferredSize(new java.awt.Dimension(101, 19));
        newArcButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        newArcButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newArcButtonActionPerformed(evt);
            }
        });
        petriNetsFrameToolBar.add(newArcButton);

        petriNetsFrameSplitPane.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        petriNetsFrameSplitPane.setDividerLocation(650);
        petriNetsFrameSplitPane.setDividerSize(3);
        petriNetsFrameSplitPane.setToolTipText("Результати обчислення статистики");
        petriNetsFrameSplitPane.setAutoscrolls(true);
        petriNetsFrameSplitPane.setMinimumSize(new java.awt.Dimension(405, 202));

        petriNetPanelScrollPane.setBorder(null);
        petriNetPanelScrollPane.setForeground(new java.awt.Color(255, 255, 255));
        petriNetPanelScrollPane.setAutoscrolls(true);
        petriNetPanelScrollPane.setMaximumSize(new java.awt.Dimension(2147483647, 2147483647));
        petriNetPanelScrollPane.setMinimumSize(new java.awt.Dimension(200, 200));
        petriNetPanelScrollPane.setPreferredSize(new java.awt.Dimension(800, 1));
        petriNetPanelScrollPane.setWheelScrollingEnabled(false);
        petriNetsFrameSplitPane.setLeftComponent(petriNetPanelScrollPane);
        petriNetPanelScrollPane.getAccessibleContext().setAccessibleDescription("");

        modelingResultsPanel.setBackground(new java.awt.Color(229, 229, 229));
        modelingResultsPanel.setForeground(new java.awt.Color(255, 255, 255));
        modelingResultsPanel.setAutoscrolls(true);
        modelingResultsPanel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        modelingResultsPanel.setMaximumSize(new java.awt.Dimension(2147483647, 2147483647));
        modelingResultsPanel.setRequestFocusEnabled(false);

        modelingResultsSplitPane.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        modelingResultsSplitPane.setDividerSize(1);
        modelingResultsSplitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        modelingResultsSplitPane.setPreferredSize(new java.awt.Dimension(100, 35));

        protokolScrollPane.setBorder(null);
        protokolScrollPane.setAutoscrolls(true);
        protokolScrollPane.setMinimumSize(new java.awt.Dimension(21, 220));

        protocolTextArea.setFont(new java.awt.Font("Tahoma", 0, 10)); // NOI18N
        protocolTextArea.setText("-------------- Events protokol ---------------");
        protocolTextArea.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255)));
        protocolTextArea.setMinimumSize(new java.awt.Dimension(100, 400));
        protocolTextArea.setName(""); // NOI18N
        protokolScrollPane.setViewportView(protocolTextArea);

        modelingResultsSplitPane.setLeftComponent(protokolScrollPane);

        statisticsScrollPane.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

        statisticsTextArea.setFont(new java.awt.Font("Tahoma", 0, 10)); // NOI18N
        statisticsTextArea.setText("--------------- STATISTICS ----------------");
        statisticsTextArea.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255)));
        statisticsTextArea.setName(""); // NOI18N
        statisticsScrollPane.setViewportView(statisticsTextArea);
        statisticsTextArea.getAccessibleContext().setAccessibleName("");

        modelingResultsSplitPane.setRightComponent(statisticsScrollPane);

        javax.swing.GroupLayout modelingResultsPanelLayout = new javax.swing.GroupLayout(modelingResultsPanel);
        modelingResultsPanel.setLayout(modelingResultsPanelLayout);
        modelingResultsPanelLayout.setHorizontalGroup(
            modelingResultsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(modelingResultsSplitPane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 789, Short.MAX_VALUE)
        );
        modelingResultsPanelLayout.setVerticalGroup(
            modelingResultsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(modelingResultsSplitPane, javax.swing.GroupLayout.DEFAULT_SIZE, 639, Short.MAX_VALUE)
        );

        petriNetsFrameSplitPane.setRightComponent(modelingResultsPanel);

        leftNenuPanel.setAlignmentX(0.0F);
        leftNenuPanel.setAlignmentY(0.0F);
        leftNenuPanel.setPreferredSize(new java.awt.Dimension(757, 592));

        scrollPane.setAutoscrolls(true);

        leftMenuList.setModel(leftMenuListModel);
        leftMenuList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        leftMenuList.setAlignmentX(0.0F);
        leftMenuList.setAlignmentY(0.0F);
        leftMenuList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                leftMenuListMouseClicked(evt);
            }
        });
        scrollPane.setViewportView(leftMenuList);

        javax.swing.GroupLayout leftNenuPanelLayout = new javax.swing.GroupLayout(leftNenuPanel);
        leftNenuPanel.setLayout(leftNenuPanelLayout);
        leftNenuPanelLayout.setHorizontalGroup(
            leftNenuPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 170, Short.MAX_VALUE)
            .addGroup(leftNenuPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(scrollPane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 170, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        leftNenuPanelLayout.setVerticalGroup(
            leftNenuPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 477, Short.MAX_VALUE)
            .addGroup(leftNenuPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, leftNenuPanelLayout.createSequentialGroup()
                    .addGap(0, 0, 0)
                    .addComponent(scrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 477, Short.MAX_VALUE)))
        );

        javax.swing.GroupLayout petriNetDesignLayout = new javax.swing.GroupLayout(petriNetDesign);
        petriNetDesign.setLayout(petriNetDesignLayout);
        petriNetDesignLayout.setHorizontalGroup(
            petriNetDesignLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(petriNetsFrameToolBar, javax.swing.GroupLayout.PREFERRED_SIZE, 827, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addComponent(modelingParametersPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(petriNetDesignLayout.createSequentialGroup()
                .addGap(183, 183, 183)
                .addComponent(petriNetsFrameSplitPane, javax.swing.GroupLayout.DEFAULT_SIZE, 1443, Short.MAX_VALUE)
                .addContainerGap())
            .addGroup(petriNetDesignLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(petriNetDesignLayout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(leftNenuPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 170, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap(1456, Short.MAX_VALUE)))
        );
        petriNetDesignLayout.setVerticalGroup(
            petriNetDesignLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(petriNetDesignLayout.createSequentialGroup()
                .addComponent(petriNetsFrameToolBar, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(petriNetsFrameSplitPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(modelingParametersPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addGroup(petriNetDesignLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(petriNetDesignLayout.createSequentialGroup()
                    .addGap(38, 38, 38)
                    .addComponent(leftNenuPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 477, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap(229, Short.MAX_VALUE)))
        );

        petriNetsFrameToolBar.getAccessibleContext().setAccessibleName("");
        petriNetsFrameToolBar.getAccessibleContext().setAccessibleDescription("");

        jTabbedPane1.addTab("Net designer", petriNetDesign);

        petriNetsFrameToolBar1.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255)));
        petriNetsFrameToolBar1.setFloatable(false);
        petriNetsFrameToolBar1.setRollover(true);
        petriNetsFrameToolBar1.setFont(new java.awt.Font("Arial", 0, 12)); // NOI18N
        petriNetsFrameToolBar1.setMargin(new java.awt.Insets(0, 10, 0, 10));

        newPlaceButton1.setFont(new java.awt.Font("Arial", 0, 14)); // NOI18N
        newPlaceButton1.setText("Petri-object");
        newPlaceButton1.setToolTipText("");
        newPlaceButton1.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 10, 1, 10));
        newPlaceButton1.setFocusable(false);
        newPlaceButton1.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        newPlaceButton1.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        newPlaceButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newPlaceButton1ActionPerformed(evt);
            }
        });
        petriNetsFrameToolBar1.add(newPlaceButton1);

        newTransitionButton1.setFont(new java.awt.Font("Arial", 0, 14)); // NOI18N
        newTransitionButton1.setText("Petri-object class");
        newTransitionButton1.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 10, 1, 10));
        newTransitionButton1.setFocusable(false);
        newTransitionButton1.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        newTransitionButton1.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        newTransitionButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newTransitionButton1ActionPerformed(evt);
            }
        });
        petriNetsFrameToolBar1.add(newTransitionButton1);

        newArcButton1.setFont(new java.awt.Font("Arial", 0, 14)); // NOI18N
        newArcButton1.setText("Arc");
        newArcButton1.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 10, 1, 10));
        newArcButton1.setFocusable(false);
        newArcButton1.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        newArcButton1.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        newArcButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newArcButton1ActionPerformed(evt);
            }
        });
        petriNetsFrameToolBar1.add(newArcButton1);

        runPetriNetButton1.setFont(new java.awt.Font("Arial", 0, 14)); // NOI18N
        runPetriNetButton1.setText("Run model");
        runPetriNetButton1.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 10, 1, 10));
        runPetriNetButton1.setFocusable(false);
        runPetriNetButton1.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        runPetriNetButton1.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        runPetriNetButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runPetriNetButton1ActionPerformed(evt);
            }
        });
        petriNetsFrameToolBar1.add(runPetriNetButton1);

        runEventButton1.setFont(new java.awt.Font("Arial", 0, 14)); // NOI18N
        runEventButton1.setText("Run event");
        runEventButton1.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 10, 1, 10));
        runEventButton1.setFocusable(false);
        runEventButton1.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        runEventButton1.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        runEventButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runEventButton1ActionPerformed(evt);
            }
        });
        petriNetsFrameToolBar1.add(runEventButton1);

        petriNetsFrameSplitPane1.setDividerSize(3);
        petriNetsFrameSplitPane1.setToolTipText("Результати обчислення статистики");
        petriNetsFrameSplitPane1.setAutoscrolls(true);
        petriNetsFrameSplitPane1.setMinimumSize(new java.awt.Dimension(405, 202));

        petriNetPanelScrollPane1.setBorder(new javax.swing.border.MatteBorder(null));
        petriNetPanelScrollPane1.setForeground(new java.awt.Color(255, 255, 255));
        petriNetPanelScrollPane1.setAutoscrolls(true);
        petriNetPanelScrollPane1.setMaximumSize(new java.awt.Dimension(2147483647, 2147483647));
        petriNetPanelScrollPane1.setMinimumSize(new java.awt.Dimension(200, 200));
        petriNetPanelScrollPane1.setPreferredSize(new java.awt.Dimension(1, 1));
        petriNetPanelScrollPane1.setWheelScrollingEnabled(false);
        petriNetsFrameSplitPane1.setLeftComponent(petriNetPanelScrollPane1);

        modelingResultsPanel1.setBackground(new java.awt.Color(229, 229, 229));
        modelingResultsPanel1.setBorder(new javax.swing.border.MatteBorder(null));
        modelingResultsPanel1.setForeground(new java.awt.Color(255, 255, 255));
        modelingResultsPanel1.setAutoscrolls(true);
        modelingResultsPanel1.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        modelingResultsPanel1.setMaximumSize(new java.awt.Dimension(2147483647, 2147483647));
        modelingResultsPanel1.setRequestFocusEnabled(false);

        modelingResultsSplitPane1.setDividerSize(1);
        modelingResultsSplitPane1.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        protokolScrollPane1.setAutoscrolls(true);

        protokolTextArea1.setFont(new java.awt.Font("Tahoma", 0, 10)); // NOI18N
        protokolTextArea1.setText("-------------- Events protokol ---------------");
        protokolTextArea1.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255)));
        protokolTextArea1.setMinimumSize(new java.awt.Dimension(100, 100));
        protokolScrollPane1.setViewportView(protokolTextArea1);

        modelingResultsSplitPane1.setLeftComponent(protokolScrollPane1);

        statisticsTextArea1.setFont(new java.awt.Font("Tahoma", 0, 10)); // NOI18N
        statisticsTextArea1.setText("--------------- STATISTICS ----------------");
        statisticsTextArea1.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255)));
        statisticsTextArea1.setName(""); // NOI18N
        statisticsScrollPane1.setViewportView(statisticsTextArea1);

        modelingResultsSplitPane1.setRightComponent(statisticsScrollPane1);

        modelingParametersPanel1.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255)));

        netNameLabel1.setFont(new java.awt.Font("Arial", 0, 11)); // NOI18N
        netNameLabel1.setText("Model name");
        netNameLabel1.setMinimumSize(new java.awt.Dimension(0, 0));

        netNameTextField1.setFont(new java.awt.Font("Arial", 0, 14)); // NOI18N
        netNameTextField1.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        netNameTextField1.setText("Untitled");
        netNameTextField1.setCaretPosition(1);
        netNameTextField1.setMinimumSize(new java.awt.Dimension(0, 0));
        netNameTextField1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                netNameTextField1ActionPerformed(evt);
            }
        });

        timeStartLabel1.setBackground(new java.awt.Color(192, 192, 192));
        timeStartLabel1.setFont(new java.awt.Font("Arial", 0, 11)); // NOI18N
        timeStartLabel1.setText("Time start");

        timeStartField1.setFont(new java.awt.Font("Arial", 0, 14)); // NOI18N
        timeStartField1.setHorizontalAlignment(javax.swing.JTextField.LEFT);
        timeStartField1.setText("0");
        timeStartField1.setMinimumSize(new java.awt.Dimension(0, 0));
        timeStartField1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timeStartField1ActionPerformed(evt);
            }
        });

        timeModelingLabel1.setBackground(new java.awt.Color(247, 247, 247));
        timeModelingLabel1.setFont(new java.awt.Font("Arial", 0, 11)); // NOI18N
        timeModelingLabel1.setText("Time modeling");

        timeModelingTextField1.setFont(new java.awt.Font("Arial", 0, 14)); // NOI18N
        timeModelingTextField1.setHorizontalAlignment(javax.swing.JTextField.LEFT);
        timeModelingTextField1.setText("1000");
        timeModelingTextField1.setCaretPosition(1);
        timeModelingTextField1.setMinimumSize(new java.awt.Dimension(0, 0));

        javax.swing.GroupLayout modelingParametersPanel1Layout = new javax.swing.GroupLayout(modelingParametersPanel1);
        modelingParametersPanel1.setLayout(modelingParametersPanel1Layout);
        modelingParametersPanel1Layout.setHorizontalGroup(
            modelingParametersPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(modelingParametersPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(netNameLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, 293, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(netNameTextField1, javax.swing.GroupLayout.DEFAULT_SIZE, 317, Short.MAX_VALUE)
                .addGap(10, 10, 10)
                .addComponent(timeStartLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(timeStartField1, javax.swing.GroupLayout.DEFAULT_SIZE, 318, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(timeModelingLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(timeModelingTextField1, javax.swing.GroupLayout.DEFAULT_SIZE, 318, Short.MAX_VALUE)
                .addContainerGap())
        );
        modelingParametersPanel1Layout.setVerticalGroup(
            modelingParametersPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(modelingParametersPanel1Layout.createSequentialGroup()
                .addGap(3, 3, 3)
                .addGroup(modelingParametersPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE, false)
                    .addComponent(netNameLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(timeStartLabel1)
                    .addComponent(timeStartField1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(timeModelingLabel1)
                    .addComponent(timeModelingTextField1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(netNameTextField1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout modelingResultsPanel1Layout = new javax.swing.GroupLayout(modelingResultsPanel1);
        modelingResultsPanel1.setLayout(modelingResultsPanel1Layout);
        modelingResultsPanel1Layout.setHorizontalGroup(
            modelingResultsPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(modelingResultsPanel1Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(modelingResultsPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(modelingResultsSplitPane1)
                    .addComponent(modelingParametersPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(1, 1, 1))
        );
        modelingResultsPanel1Layout.setVerticalGroup(
            modelingResultsPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(modelingResultsPanel1Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(modelingParametersPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, 31, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(modelingResultsSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 660, Short.MAX_VALUE)
                .addGap(2, 2, 2))
        );

        petriNetsFrameSplitPane1.setRightComponent(modelingResultsPanel1);

        javax.swing.GroupLayout petriNetDesign1Layout = new javax.swing.GroupLayout(petriNetDesign1);
        petriNetDesign1.setLayout(petriNetDesign1Layout);
        petriNetDesign1Layout.setHorizontalGroup(
            petriNetDesign1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(petriNetDesign1Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(petriNetDesign1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(petriNetsFrameSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 1638, Short.MAX_VALUE)
                    .addComponent(petriNetsFrameToolBar1, javax.swing.GroupLayout.DEFAULT_SIZE, 1638, Short.MAX_VALUE))
                .addGap(0, 0, 0))
        );
        petriNetDesign1Layout.setVerticalGroup(
            petriNetDesign1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(petriNetDesign1Layout.createSequentialGroup()
                .addComponent(petriNetsFrameToolBar1, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(petriNetsFrameSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(1, 1, 1))
        );

        jTabbedPane1.addTab("Model designer", petriNetDesign1);

        petriNetsFrameToolBar2.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255)));
        petriNetsFrameToolBar2.setFloatable(false);
        petriNetsFrameToolBar2.setRollover(true);
        petriNetsFrameToolBar2.setFont(new java.awt.Font("Arial", 0, 12)); // NOI18N
        petriNetsFrameToolBar2.setMargin(new java.awt.Insets(0, 10, 0, 10));

        newPlaceButton2.setFont(new java.awt.Font("Arial", 0, 14)); // NOI18N
        newPlaceButton2.setText("Petri-object");
        newPlaceButton2.setToolTipText("");
        newPlaceButton2.setFocusable(false);
        newPlaceButton2.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        newPlaceButton2.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        newPlaceButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newPlaceButton2ActionPerformed(evt);
            }
        });
        petriNetsFrameToolBar2.add(newPlaceButton2);

        newTransitionButton2.setFont(new java.awt.Font("Arial", 0, 14)); // NOI18N
        newTransitionButton2.setText("Petri-object class");
        newTransitionButton2.setFocusable(false);
        newTransitionButton2.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        newTransitionButton2.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        newTransitionButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newTransitionButton2ActionPerformed(evt);
            }
        });
        petriNetsFrameToolBar2.add(newTransitionButton2);

        newArcButton2.setFont(new java.awt.Font("Arial", 0, 14)); // NOI18N
        newArcButton2.setText("Tie");
        newArcButton2.setFocusable(false);
        newArcButton2.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        newArcButton2.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        newArcButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newArcButton2ActionPerformed(evt);
            }
        });
        petriNetsFrameToolBar2.add(newArcButton2);

        runPetriNetButton2.setFont(new java.awt.Font("Arial", 0, 14)); // NOI18N
        runPetriNetButton2.setText("Run model");
        runPetriNetButton2.setFocusable(false);
        runPetriNetButton2.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        runPetriNetButton2.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        runPetriNetButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runPetriNetButton2ActionPerformed(evt);
            }
        });
        petriNetsFrameToolBar2.add(runPetriNetButton2);

        runEventButton2.setFont(new java.awt.Font("Arial", 0, 14)); // NOI18N
        runEventButton2.setText("Run event");
        runEventButton2.setFocusable(false);
        runEventButton2.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        runEventButton2.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        runEventButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runEventButton2ActionPerformed(evt);
            }
        });
        petriNetsFrameToolBar2.add(runEventButton2);

        petriNetsFrameSplitPane2.setDividerSize(3);
        petriNetsFrameSplitPane2.setToolTipText("Результати обчислення статистики");
        petriNetsFrameSplitPane2.setAutoscrolls(true);
        petriNetsFrameSplitPane2.setMinimumSize(new java.awt.Dimension(405, 202));

        petriNetPanelScrollPane2.setBorder(new javax.swing.border.MatteBorder(null));
        petriNetPanelScrollPane2.setForeground(new java.awt.Color(255, 255, 255));
        petriNetPanelScrollPane2.setAutoscrolls(true);
        petriNetPanelScrollPane2.setMaximumSize(new java.awt.Dimension(2147483647, 2147483647));
        petriNetPanelScrollPane2.setMinimumSize(new java.awt.Dimension(200, 200));
        petriNetPanelScrollPane2.setPreferredSize(new java.awt.Dimension(1, 1));
        petriNetPanelScrollPane2.setWheelScrollingEnabled(false);
        petriNetsFrameSplitPane2.setLeftComponent(petriNetPanelScrollPane2);

        modelingResultsPanel2.setBackground(new java.awt.Color(229, 229, 229));
        modelingResultsPanel2.setBorder(new javax.swing.border.MatteBorder(null));
        modelingResultsPanel2.setForeground(new java.awt.Color(255, 255, 255));
        modelingResultsPanel2.setAutoscrolls(true);
        modelingResultsPanel2.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        modelingResultsPanel2.setMaximumSize(new java.awt.Dimension(2147483647, 2147483647));
        modelingResultsPanel2.setRequestFocusEnabled(false);

        modelingResultsSplitPane2.setDividerSize(1);
        modelingResultsSplitPane2.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        protokolScrollPane2.setAutoscrolls(true);

        protokolTextArea2.setFont(new java.awt.Font("Tahoma", 0, 10)); // NOI18N
        protokolTextArea2.setText("-------------- Events protokol ---------------");
        protokolTextArea2.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255)));
        protokolTextArea2.setMinimumSize(new java.awt.Dimension(100, 100));
        protokolScrollPane2.setViewportView(protokolTextArea2);

        modelingResultsSplitPane2.setLeftComponent(protokolScrollPane2);

        statisticsTextArea2.setFont(new java.awt.Font("Tahoma", 0, 10)); // NOI18N
        statisticsTextArea2.setText("--------------- STATISTICS ----------------");
        statisticsTextArea2.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255)));
        statisticsTextArea2.setName(""); // NOI18N
        statisticsScrollPane2.setViewportView(statisticsTextArea2);

        modelingResultsSplitPane2.setRightComponent(statisticsScrollPane2);

        modelingParametersPanel2.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255)));

        netNameLabel2.setFont(new java.awt.Font("Arial", 0, 11)); // NOI18N
        netNameLabel2.setText("Model name");
        netNameLabel2.setMinimumSize(new java.awt.Dimension(0, 0));

        netNameTextField2.setFont(new java.awt.Font("Arial", 0, 14)); // NOI18N
        netNameTextField2.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        netNameTextField2.setText("Untitled");
        netNameTextField2.setCaretPosition(1);
        netNameTextField2.setMinimumSize(new java.awt.Dimension(0, 0));
        netNameTextField2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                netNameTextField2ActionPerformed(evt);
            }
        });

        timeStartLabel2.setBackground(new java.awt.Color(192, 192, 192));
        timeStartLabel2.setFont(new java.awt.Font("Arial", 0, 11)); // NOI18N
        timeStartLabel2.setText("Time start");

        timeStartField2.setFont(new java.awt.Font("Arial", 0, 14)); // NOI18N
        timeStartField2.setHorizontalAlignment(javax.swing.JTextField.LEFT);
        timeStartField2.setText("0");
        timeStartField2.setMinimumSize(new java.awt.Dimension(0, 0));
        timeStartField2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timeStartField2ActionPerformed(evt);
            }
        });

        timeModelingLabel2.setBackground(new java.awt.Color(247, 247, 247));
        timeModelingLabel2.setFont(new java.awt.Font("Arial", 0, 11)); // NOI18N
        timeModelingLabel2.setText("Time modeling");

        timeModelingTextField2.setFont(new java.awt.Font("Arial", 0, 14)); // NOI18N
        timeModelingTextField2.setHorizontalAlignment(javax.swing.JTextField.LEFT);
        timeModelingTextField2.setText("1000");
        timeModelingTextField2.setCaretPosition(1);
        timeModelingTextField2.setMinimumSize(new java.awt.Dimension(0, 0));

        javax.swing.GroupLayout modelingParametersPanel2Layout = new javax.swing.GroupLayout(modelingParametersPanel2);
        modelingParametersPanel2.setLayout(modelingParametersPanel2Layout);
        modelingParametersPanel2Layout.setHorizontalGroup(
            modelingParametersPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(modelingParametersPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(netNameLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, 213, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(netNameTextField2, javax.swing.GroupLayout.DEFAULT_SIZE, 232, Short.MAX_VALUE)
                .addGap(10, 10, 10)
                .addComponent(timeStartLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, 213, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(timeStartField2, javax.swing.GroupLayout.DEFAULT_SIZE, 230, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(timeModelingLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, 226, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(timeModelingTextField2, javax.swing.GroupLayout.DEFAULT_SIZE, 230, Short.MAX_VALUE)
                .addContainerGap())
        );
        modelingParametersPanel2Layout.setVerticalGroup(
            modelingParametersPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(modelingParametersPanel2Layout.createSequentialGroup()
                .addGap(3, 3, 3)
                .addGroup(modelingParametersPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE, false)
                    .addComponent(netNameLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(timeStartLabel2)
                    .addComponent(timeStartField2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(timeModelingLabel2)
                    .addComponent(timeModelingTextField2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(netNameTextField2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout modelingResultsPanel2Layout = new javax.swing.GroupLayout(modelingResultsPanel2);
        modelingResultsPanel2.setLayout(modelingResultsPanel2Layout);
        modelingResultsPanel2Layout.setHorizontalGroup(
            modelingResultsPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(modelingResultsPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(modelingResultsPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(modelingResultsSplitPane2)
                    .addComponent(modelingParametersPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        modelingResultsPanel2Layout.setVerticalGroup(
            modelingResultsPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(modelingResultsPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(modelingParametersPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, 31, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(modelingResultsSplitPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 630, Short.MAX_VALUE)
                .addContainerGap())
        );

        petriNetsFrameSplitPane2.setRightComponent(modelingResultsPanel2);

        javax.swing.GroupLayout petriNetDesign2Layout = new javax.swing.GroupLayout(petriNetDesign2);
        petriNetDesign2.setLayout(petriNetDesign2Layout);
        petriNetDesign2Layout.setHorizontalGroup(
            petriNetDesign2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(petriNetDesign2Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(petriNetDesign2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(petriNetsFrameSplitPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 1638, Short.MAX_VALUE)
                    .addComponent(petriNetsFrameToolBar2, javax.swing.GroupLayout.DEFAULT_SIZE, 1638, Short.MAX_VALUE))
                .addGap(0, 0, 0))
        );
        petriNetDesign2Layout.setVerticalGroup(
            petriNetDesign2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(petriNetDesign2Layout.createSequentialGroup()
                .addComponent(petriNetsFrameToolBar2, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(4, 4, 4)
                .addComponent(petriNetsFrameSplitPane2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(1, 1, 1))
        );

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(petriNetDesign2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(petriNetDesign2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        jTabbedPane1.addTab("Experiment designer", jPanel1);

        petriNetsFrameMenuBar.setBackground(new java.awt.Color(186, 213, 241));
        petriNetsFrameMenuBar.setForeground(new java.awt.Color(98, 147, 167));

        fileMenu.setText("File");
        fileMenu.setMargin(new java.awt.Insets(0, 10, 0, 10));

        openMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        openMenuItem.setText("Open");
        openMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(openMenuItem);

        newMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        newMenuItem.setText("New");
        newMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(newMenuItem);

        openMethodMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_M, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        openMethodMenuItem.setText("Open a method file");
        openMethodMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openMethodMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(openMethodMenuItem);

        petriNetsFrameMenuBar.add(fileMenu);

        editMenu.setText("Edit");
        editMenu.setMargin(new java.awt.Insets(0, 10, 0, 10));

        editNetParameters.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_E, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        editNetParameters.setText("Edit net parameters");
        editNetParameters.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editNetParametersActionPerformed(evt);
            }
        });
        editMenu.add(editNetParameters);

        centerLocationOfGraphNet.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_L, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        centerLocationOfGraphNet.setText("Locate net in center");
        centerLocationOfGraphNet.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                centerLocationOfGraphNetActionPerformed(evt);
            }
        });
        editMenu.add(centerLocationOfGraphNet);

        undoMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        undoMenuItem.setText("Undo");
        undoMenuItem.setEnabled(undoManager.canUndo());
        undoMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                undoMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(undoMenuItem);

        redoMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, java.awt.event.InputEvent.SHIFT_DOWN_MASK | java.awt.event.InputEvent.CTRL_DOWN_MASK));
        redoMenuItem.setText("Redo");
        redoMenuItem.setEnabled(undoManager.canRedo());
        redoMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                redoMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(redoMenuItem);

        petriNetsFrameMenuBar.add(editMenu);

        save.setText("Save");
        save.setMargin(new java.awt.Insets(0, 10, 0, 10));

        SaveGraphNet.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        SaveGraphNet.setText("Save Graph net");
        SaveGraphNet.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SaveGraphNetActionPerformed(evt);
            }
        });
        save.add(SaveGraphNet);

        jMenuItem2.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.SHIFT_DOWN_MASK | java.awt.event.InputEvent.CTRL_DOWN_MASK));
        jMenuItem2.setText("Save Graph net as");
        jMenuItem2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem2ActionPerformed(evt);
            }
        });
        save.add(jMenuItem2);

        SavePetriNetAs.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        SavePetriNetAs.setText("Save  Petri net as");
        SavePetriNetAs.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SavePetriNetAsActionPerformed(evt);
            }
        });
        save.add(SavePetriNetAs);

        SaveNetAsMethod.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_M, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        SaveNetAsMethod.setText("Save net as method");
        SaveNetAsMethod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SaveNetAsMethodActionPerformed(evt);
            }
        });
        save.add(SaveNetAsMethod);

        SaveMethodInNetLibrary.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_M, java.awt.event.InputEvent.SHIFT_DOWN_MASK | java.awt.event.InputEvent.CTRL_DOWN_MASK));
        SaveMethodInNetLibrary.setText("Save method in NetLibrary");
        SaveMethodInNetLibrary.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SaveMethodInNetLibraryActionPerformed(evt);
            }
        });
        save.add(SaveMethodInNetLibrary);

        petriNetsFrameMenuBar.add(save);

        Animate.setAction(animateEventAction);
        Animate.setText("Animate");
        Animate.setMargin(new java.awt.Insets(0, 10, 0, 10));

        itemAnimateNet.setAction(playPauseAction);
        itemAnimateNet.setText("Animate Petri net");
        Animate.add(itemAnimateNet);

        itemAnimateEvent.setAction(animateEventAction);
        itemAnimateEvent.setText("Animate event");
        Animate.add(itemAnimateEvent);

        petriNetsFrameMenuBar.add(Animate);

        runMenu.setAction(runNetAction);
        runMenu.setText("Run");

        itemRunNet.setAction(runNetAction);
        itemRunNet.setText("run");
        runMenu.add(itemRunNet);

        itemRunEvent.setAction(runOneEventAction);
        itemRunEvent.setText("runEvent");
        itemRunEvent.setToolTipText("");
        runMenu.add(itemRunEvent);

        petriNetsFrameMenuBar.add(runMenu);

        setJMenuBar(petriNetsFrameMenuBar);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1, javax.swing.GroupLayout.Alignment.TRAILING)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1, javax.swing.GroupLayout.Alignment.TRAILING)
        );

        jTabbedPane1.getAccessibleContext().setAccessibleName("Net designer");

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void timeStartField2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeStartField2ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_timeStartField2ActionPerformed

    private void netNameTextField2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_netNameTextField2ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_netNameTextField2ActionPerformed

    private void runEventButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runEventButton2ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_runEventButton2ActionPerformed

    private void runPetriNetButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runPetriNetButton2ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_runPetriNetButton2ActionPerformed

    private void newArcButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newArcButton2ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_newArcButton2ActionPerformed

    private void newTransitionButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newTransitionButton2ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_newTransitionButton2ActionPerformed

    private void newPlaceButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newPlaceButton2ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_newPlaceButton2ActionPerformed

    private void newArcButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newArcButtonActionPerformed
        getPetriNetsPanel().setIsSettingArc(true);
    }//GEN-LAST:event_newArcButtonActionPerformed

    private void newTransitionButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newTransitionButtonActionPerformed
        GraphPetriTransition pt = new GraphPetriTransition(new PetriT(
                GraphPetriTransition.setSimpleName(), 0.0),
                PetriNetsPanel.getIdElement());// by Inna 18.01.2013, changed 1.10.2018
        
        AddGraphElementEdit edit = new AddGraphElementEdit(getPetriNetsPanel(), pt);
        edit.doFirstTime();
        undoSupport.postEdit(edit);
    }//GEN-LAST:event_newTransitionButtonActionPerformed

    private void newPlaceButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newPlaceButtonActionPerformed
        GraphPetriPlace pp = new GraphPetriPlace(new PetriP(
                GraphPetriPlace.setSimpleName(), 0),
                PetriNetsPanel.getIdElement()); // by Inna 18.01.2013, changed 1.10.2018
        /* getPetriNetsPanel().getGraphNet().getGraphPetriPlaceList().add(pp);
        getPetriNetsPanel().setCurrent(pp);*/
        AddGraphElementEdit edit = new AddGraphElementEdit(getPetriNetsPanel(), pp); 
        edit.doFirstTime();
        undoSupport.postEdit(edit);
        
    }//GEN-LAST:event_newPlaceButtonActionPerformed

    private void leftMenuListMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_leftMenuListMouseClicked
        if (evt.getClickCount() == 2) {
            try {
                timeStartField.setText(String.valueOf(0));
                protocolTextArea.setText("---------Events protocol----------");
                statisticsTextArea.setText("---------STATISTICS---------");
                //Move current content in center
                Point center = new Point(
                        petriNetPanelScrollPane.getLocation().x
                        + petriNetPanelScrollPane.getBounds().width / 2,
                        petriNetPanelScrollPane.getLocation().y
                        + petriNetPanelScrollPane.getBounds().height / 2);
                getPetriNetsPanel().getGraphNet().changeLocation(center);

                String methodFullName = (String) leftMenuList.getSelectedValue();
                String pnetName = fileUse.openMethod(getPetriNetsPanel(),
                        methodFullName, PetriNetsFrame.this);
                if (pnetName != null) {
                    netNameTextField.setText(pnetName);
                }
            } catch (ExceptionInvalidNetStructure ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(
                        Level.SEVERE, null, ex);
            }
        }// TODO add your handling code here:
    }//GEN-LAST:event_leftMenuListMouseClicked

    private void itemResetNetActionPerformed(java.awt.event.ActionEvent evt) {
        GraphPetriNet graphPetriNetBackup = GraphPetriNetBackupHolder.getInstance().get();
        if (graphPetriNetBackup != null) {
            getPetriNetsPanel().setGraphNet(graphPetriNetBackup);

            GraphPetriNetBackupHolder.getInstance()
                    .save(new GraphPetriNet(getPetriNetsPanel().getGraphNet()));

            getPetriNetsPanel().requestFocusInWindow();
            getPetriNetsPanel().redraw();
        }
    }


    private void undoMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_undoMenuItemActionPerformed
        if (undoManager.canUndo()) {
            undoManager.undo();
        }
        undoMenuItem.setEnabled(undoManager.canUndo());
        redoMenuItem.setEnabled(undoManager.canRedo());
    }//GEN-LAST:event_undoMenuItemActionPerformed

    private void redoMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_redoMenuItemActionPerformed
        if (undoManager.canRedo()) {
            undoManager.redo();
        }
        undoMenuItem.setEnabled(undoManager.canUndo());
        redoMenuItem.setEnabled(undoManager.canRedo());
    }//GEN-LAST:event_redoMenuItemActionPerformed

    private void timeStartField1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeStartField1ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_timeStartField1ActionPerformed

    private void netNameTextField1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_netNameTextField1ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_netNameTextField1ActionPerformed

    private void runEventButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runEventButton1ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_runEventButton1ActionPerformed

    private void runPetriNetButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runPetriNetButton1ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_runPetriNetButton1ActionPerformed

    private void newArcButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newArcButton1ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_newArcButton1ActionPerformed

    private void newTransitionButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newTransitionButton1ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_newTransitionButton1ActionPerformed

    private void newPlaceButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newPlaceButton1ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_newPlaceButton1ActionPerformed

    private void speedSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_speedSliderStateChanged
        timer.setDelay(speedSlider.getValue() / 3);
    }//GEN-LAST:event_speedSliderStateChanged

    private void timeStartFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeStartFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_timeStartFieldActionPerformed

    private void netNameTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_netNameTextFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_netNameTextFieldActionPerformed

    private void openMenuItemActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_openMenuItemActionPerformed
        try {
            fileUse.newWorksheet(getPetriNetsPanel());
            timeStartField.setText(String.valueOf(0));

            netNameTextField.setText("Untitled");
            protocolTextArea.setText("---------Events protocol----------");
            statisticsTextArea.setText("---------STATISTICS---------");
            String pnetName = fileUse.openFile(getPetriNetsPanel(), this);
            if (pnetName != null) {
                netNameTextField.setText(pnetName);
            }
        } catch (ExceptionInvalidNetStructure ex) {
            Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE,
                    null, ex);
        }
    }// GEN-LAST:event_openMenuItemActionPerformed

    private void newMenuItemActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_newMenuItemActionPerformed
        fileUse.newWorksheet(getPetriNetsPanel());
        timeStartField.setText(String.valueOf(0));

        netNameTextField.setText("Untitled");
    }// GEN-LAST:event_newMenuItemActionPerformed

    private void SaveNetAsMethodActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_SaveNetAsMethodActionPerformed
        try {
            getPetriNetsPanel().getGraphNet().createPetriNet(
                    netNameTextField.getText()); // added by Inna
            fileUse.saveNetAsMethod(getPetriNetsPanel().getGraphNet(),
                    statisticsTextArea);
        } catch (ExceptionInvalidNetStructure | ExceptionInvalidTimeDelay ex) {
            Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE,
                    null, ex);
        }

    }// GEN-LAST:event_SaveNetAsMethodActionPerformed

    private void SaveGraphNetActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_SaveGraphNetActionPerformed
        GraphPetriNet net = getPetriNetsPanel().getGraphNet();
        if (net != null) {
            try {
                if (!fileUse.saveGraphNet(net, netNameTextField.getText()
                        .trim())) {
                    System.out.println("Graph net was not saved");
                }
            } catch (ExceptionInvalidNetStructure ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(
                        Level.SEVERE, null, ex);
            }
        }

    }// GEN-LAST:event_SaveGraphNetActionPerformed

    private void SavePetriNetAsActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_SavePetriNetAsActionPerformed
        try {
            fileUse.savePetriNetAs(getPetriNetsPanel(), this);
        } catch (ExceptionInvalidNetStructure | ExceptionInvalidTimeDelay ex) {
            Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE,
                    null, ex);
        }
    }// GEN-LAST:event_SavePetriNetAsActionPerformed

    private void jMenuItem2ActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_jMenuItem2ActionPerformed
        try {
            fileUse.saveGraphNetAs(getPetriNetsPanel(), this);
        } catch (ExceptionInvalidNetStructure | ExceptionInvalidTimeDelay ex) {
            Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE,
                    null, ex);
        }
    }// GEN-LAST:event_jMenuItem2ActionPerformed

    private void SaveMethodInNetLibraryActionPerformed(
            java.awt.event.ActionEvent evt) {// GEN-FIRST:event_SaveMethodInNetLibraryActionPerformed
        if (statisticsTextArea.getText().contains("{")) {
            fileUse.saveMethodInNetLibrary(statisticsTextArea);
            this.UpdateNetLibraryMethodsCombobox();
        }

    }// GEN-LAST:event_SaveMethodInNetLibraryActionPerformed

    private void editNetParametersActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_editNetParametersActionPerformed
        try {
            if (getPetriNetsPanel().getGraphNet() != null) { // adde by Inna 19.02.16
                GraphNetParametersFrame reUseFrame = new GraphNetParametersFrame(
                        this);
                reUseFrame.setVisible(true);
            } else {
                GraphNetParametersFrame reUseFrame = new GraphNetParametersFrame();
                reUseFrame.setVisible(true);
            }
        } catch (ExceptionInvalidNetStructure ex) {
            Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE,
                    null, ex);
        }
    }// GEN-LAST:event_editNetParametersActionPerformed

    private boolean isCorrectNet() throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
       // System.out.println(petriNetsPanel.getGraphNet().getGraphPetriPlaceList().size());
        if (getPetriNetsPanel().getGraphNet() == null) {
            errorFrame.setErrorMessage(" Graph image of Petri Net does not exist yet. Paint it or read it from file.");
            errorFrame.setVisible(true);
            return false;
        }
        if (getPetriNetsPanel().getGraphNet().isCorrectInArcs() != true) {
                errorFrame.setErrorMessage(" Transition has no input places.");
                errorFrame.setVisible(true);
                return false;
        }
        if (getPetriNetsPanel().getGraphNet().isCorrectOutArcs() != true) {
                    errorFrame.setErrorMessage(" Transition has no output places.");
                    errorFrame.setVisible(true);
                    return false;



        }
        getPetriNetsPanel().getGraphNet().createPetriNet(
                        netNameTextField.getText()); // creating Petri net
        if (getPetriNetsPanel().getGraphNet().getPetriNet() == null) {
                        errorFrame.setErrorMessage(" Petri Net does not exist yet. Paint it or read it from file. ");
                        errorFrame.setVisible(true);
                        return false;
        }
        if (getPetriNetsPanel().getGraphNet().hasParameters() == true) { // addedn by Katya 08.12.2016
                errorFrame.setErrorMessage(" Petri Net has parameters. Provide specific values for them first.");
                errorFrame.setVisible(true);
                return false;
        }
        return true;
    }

    public void runNet() {
        protocolTextArea.setText("---------Events protocol----------");
        protocolTextArea.setText("---------STATISTICS---------");
        try {
            if(isCorrectNet()){
                getPetriNetsPanel().getGraphNet().createPetriNet(
                    netNameTextField.getText()); // modified by Katya 08.12.2016
                PetriSim petriSim = new PetriSim(
                        getPetriNetsPanel().getGraphNet().getPetriNet());

                petriSim.setSimulationTime(Double.parseDouble(
                        timeModelingTextField.getText()));
                petriSim.setTimeCurr(Double.valueOf(
                        timeStartField.getText()));

                ArrayList<PetriSim> list = new ArrayList<>();
                list.add(petriSim);
                RunPetriObjModel m = new RunPetriObjModel(list,
                        protocolTextArea); // Петрі-об"єктна модель, що складається з одного Петрі-об"єкта
                m.setSimulationTime(Double.parseDouble(timeModelingTextField.getText()));
                m.setCurrentTime(Double.valueOf(timeStartField.getText()));
                m.go(Double.valueOf(timeModelingTextField.getText()));
                getPetriNetsPanel().getGraphNet().printStatistics(
                        statisticsTextArea);
                // перетворення у потрібний формат ...
                Double d = m.getCurrentTime(); // added

                Double dd = 100.0 * (m.getCurrentTime() - d.intValue()); // десяткова частина

                //timeStartField.setText(String.valueOf(d.intValue()
                //		+ "." + dd.intValue())); // added by Inna
                // 3.06.2013
                getPetriNetsPanel().repaint(); // додано 19.11.2012,
                // можливо не потрібно?
            }
            //  }
            // }

            // }
        } catch (ExceptionInvalidNetStructure ex) {
            Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ExceptionInvalidTimeDelay ex) {
            Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
        }
    }



    public void animateNet() {
        protocolTextArea.setText("---------Events protocol----------");
        protocolTextArea.setText("---------STATISTICS---------");
        try {
            if(isCorrectNet()){
                getPetriNetsPanel().getGraphNet().createPetriNet(
                    netNameTextField.getText()); // modified by Katya 08.12.2016
                AnimRunPetriSim petriSim = new AnimRunPetriSim(
                        getPetriNetsPanel().getGraphNet().getPetriNet(),
                        this.protocolTextArea, getPetriNetsPanel(),
                        speedSlider, null); // parentmodel = null is ok since petri objects are recreated in constructor anyway

                petriSim.setSimulationTime(Double.parseDouble(
                        timeModelingTextField.getText()));
                petriSim.setTimeCurr(Double.valueOf(
                        timeStartField.getText()));

                ArrayList<PetriSim> list = new ArrayList<PetriSim>();
                list.add(petriSim);
                
                 
                
                AnimRunPetriObjModel model = new AnimRunPetriObjModel(list,
                        protocolTextArea, getPetriNetsPanel(),
                        speedSlider); // Петрі-об"єктна модель, що складається з одного Петріз-об"єкта
                
                animationModel = model;
                
                model.setSimulationTime(Double.parseDouble(timeModelingTextField.getText()));
                model.setCurrentTime(Double.valueOf(timeStartField.getText()));
                model.go(Double.valueOf(timeModelingTextField.getText()));
                getPetriNetsPanel().getGraphNet().printStatistics(
                        statisticsTextArea);
                // перетворення у потрібний формат ...
                Double d = model.getCurrentTime(); // added

                Double dd = 100.0 * (model.getCurrentTime() - d.intValue()); // десяткова частина

                //timeStartField.setText(String.valueOf(d.intValue()
                //		+ "." + dd.intValue())); // added by Inna
                // 3.06.2013
                getPetriNetsPanel().repaint(); // додано 19.11.2012,
                // можливо не потрібно?
            }
            //  }
            // }

            // }
        } catch (ExceptionInvalidNetStructure ex) {
            Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ExceptionInvalidTimeDelay ex) {
            Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void itemAnimateEventActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_itemRunEventActionPerformed
        
    }// GEN-LAST:event_itemRunEventActionPerformed

    public void runEvent() {
        if (getPetriNetsPanel().getGraphNet() == null) {
            errorFrame.setErrorMessage(" Graph image of Petri Net does not exist yet. Paint it or read it from file.");
            errorFrame.setVisible(true);
            return;
        } else {
            try {
                getPetriNetsPanel().getGraphNet().createPetriNet(
                        netNameTextField.getText()); // створення мережі Петріта запис її в GraphNet
                if (getPetriNetsPanel().getGraphNet().getPetriNet() == null) {
                    errorFrame.setErrorMessage(" Petri Net does not exist yet. Paint it or read it from file. ");
                    errorFrame.setVisible(true);
                    return;
                } else {
                    PetriSim petriSim = new PetriSim(
                            getPetriNetsPanel().getGraphNet().getPetriNet());

                    petriSim.setSimulationTime(
                            Double.parseDouble(timeModelingTextField.getText()));

                    petriSim.setTimeCurr(
                            Double.valueOf(timeStartField.getText()));

                    // System.out.println("in the begining we have such state of net places:");
                    petriSim.printMark();
                    petriSim.step();
                    // System.out.println("at the result we have such state of net places:");
                    petriSim.printMark(protocolTextArea);

                    Double d = new Double(petriSim.getCurrentTime()); // added by
                    Double dd = new Double(100.0 * (petriSim.getCurrentTime() - d.intValue()));
                    //timeStartField.setText(String.valueOf(d.intValue() + "."
                    //		+ dd.intValue() // перетворення у цілий формат, але
                    // тоді здається що час
                    // дискретний....

                    getPetriNetsPanel().repaint(); // додано 19.11.2012, можливо не потрібно?
                }
            } catch (ExceptionInvalidNetStructure | ExceptionInvalidTimeDelay ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(
                        Level.SEVERE, null, ex);
            }
        }
        getPetriNetsPanel().getGraphNet().printStatistics(statisticsTextArea);

    }

    void animateEvent() {
        if (getPetriNetsPanel().getGraphNet() == null) {
            errorFrame.setErrorMessage(" Petri Net does not exist yet. Paint it or read it from file.");
            errorFrame.setVisible(true);
            return;
        } else {
            try {
                getPetriNetsPanel().getGraphNet().createPetriNet(
                        netNameTextField.getText()); // створення мережі Петріта запис її в GraphNet
                if (getPetriNetsPanel().getGraphNet().getPetriNet() == null) {
                    errorFrame.setErrorMessage(" Petri Net does not exist yet. Paint it or read it from file. ");
                    errorFrame.setVisible(true);
                    return;
                } else {
                    AnimRunPetriSim object = new AnimRunPetriSim(
                            getPetriNetsPanel().getGraphNet().getPetriNet(),
                            protocolTextArea, getPetriNetsPanel(),
                            speedSlider, null);
                    
                    animationPetriObject = object;

                    object.setSimulationTime(
                            Double.parseDouble(timeModelingTextField.getText()));

                    object.setTimeCurr(
                            Double.valueOf(timeStartField.getText()));

                    // System.out.println("in the begining we have such state of net places:");
                    object.printMark();
                    object.step();
                    // System.out.println("at the result we have such state of net places:");
                    object.printMark(protocolTextArea);

                    Double d = new Double(object.getCurrentTime()); // added by
                    Double dd = new Double(100.0 * (object.getCurrentTime() - d.intValue()));
                    //timeStartField.setText(String.valueOf(d.intValue() + "."
                    //		+ dd.intValue() // перетворення у цілий формат, але
                    // тоді здається що час
                    // дискретний....
                    //)); // added by Inna 3.06.2013
                    getPetriNetsPanel().repaint(); // додано 19.11.2012, можливо не потрібно?
                }
            } catch (ExceptionInvalidNetStructure | ExceptionInvalidTimeDelay ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(
                        Level.SEVERE, null, ex);
            }
        }
        getPetriNetsPanel().getGraphNet().printStatistics(statisticsTextArea);

    }

    private void centerLocationOfGraphNetActionPerformed(
            java.awt.event.ActionEvent evt) {// GEN-FIRST:event_centerLocationOfGraphNetActionPerformed
        // added by Inna 21.02.2016
        JPanel panel = this.getPetriNetsPanel();
        JScrollPane pane = petriNetPanelScrollPane;
        System.out
                .println(pane.getLocation().x + "  " + pane.getBounds().width);
        Point center = new Point(pane.getLocation().x + pane.getBounds().width
                / 2, pane.getLocation().y + pane.getBounds().height / 2);
        this.getPetriNetsPanel().getGraphNet().changeLocation(center);

        panel.repaint();
        // TODO add your handling code here:
    }// GEN-LAST:event_centerLocationOfGraphNetActionPerformed

    private void openMethodMenuItemActionPerformed(
            java.awt.event.ActionEvent evt) {// GEN-FIRST:event_openMethodMenuItemActionPerformed
        //!Не! очищаємо поле, тепер мережа додається до попередньої
        //fileUse.newWorksheet(petriNetsPanel);
        timeStartField.setText(String.valueOf(0));

        //netNameTextField.setText("Untitled");
        protocolTextArea.setText("---------Events protocol----------");
        statisticsTextArea.setText("---------STATISTICS---------");

        UpdateNetLibraryMethodsCombobox(); // added by Katya 27.11.2016

        if (dialog == null) {
            dialog = new JDialog(this, "Method to open",
                    ModalityType.APPLICATION_MODAL);
            dialog.getContentPane().add(dialogPanel);
            dialog.pack();
            dialog.setLocationRelativeTo(null);
        }
        JFrame that = this;
        dialogPanel.addOkButtonClickHandler((ActionEvent arg) -> { // modified by Katya 05.12.2016 
            try {
                //Move current content in center
                Point center = new Point(
                        petriNetPanelScrollPane.getLocation().x
                        + petriNetPanelScrollPane.getBounds().width / 2,
                        petriNetPanelScrollPane.getLocation().y
                        + petriNetPanelScrollPane.getBounds().height / 2);
                this.getPetriNetsPanel().getGraphNet().changeLocation(center);

                String methodFullName = dialogPanel.getFieldText();
                String pnetName = fileUse.openMethod(getPetriNetsPanel(),
                        methodFullName, that);
                if (pnetName != null) {
                    netNameTextField.setText(pnetName);
                }
            } catch (ExceptionInvalidNetStructure ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(
                        Level.SEVERE, null, ex);
            }
        });
        dialog.setVisible(true);
    }// GEN-LAST:event_openMethodMenuItemActionPerformed

    public String getNameNet() {
        return netNameTextField.getText();
    }

    public PetriNetsPanel getPetriNetsPanel() {
        return petriNetsPanel;
    }

    public JScrollPane GetPetriNetPanelScrollPane() {
        return petriNetPanelScrollPane;
    }

    public void disableInput() {
        save.setEnabled(false);
        editMenu.setEnabled(false);
        fileMenu.setEnabled(false);
        Animate.setEnabled(false);
        newArcButton.setEnabled(false);
       /* consistBtn.setEnabled(false);
        poolBtn.setEnabled(false);
        newThreadBtn.setEnabled(false);;
        lockBtn.setEnabled(false);
        guardBtn.setEnabled(false);*/
        newPlaceButton.setEnabled(false);
        newTransitionButton.setEnabled(false);
        protocolTextArea.setEnabled(false);
        statisticsTextArea.setEnabled(false);
        timeModelingTextField.setEnabled(false);
        timeStartField.setEnabled(false);
        netNameTextField.setEnabled(false);
        leftMenuList.setEnabled(false);
    }

    public void enableInput() {
        save.setEnabled(true);
        editMenu.setEnabled(true);
        fileMenu.setEnabled(true);
        Animate.setEnabled(true);
        newArcButton.setEnabled(true);
     /*   consistBtn.setEnabled(true);
        poolBtn.setEnabled(true);
        newThreadBtn.setEnabled(true);;
        lockBtn.setEnabled(true);
        guardBtn.setEnabled(true);*/
        newPlaceButton.setEnabled(true);
        newTransitionButton.setEnabled(true);
        protocolTextArea.setEnabled(true);
        statisticsTextArea.setEnabled(true);
        timeModelingTextField.setEnabled(true);
        timeStartField.setEnabled(true);
        netNameTextField.setEnabled(true);
        leftMenuList.setEnabled(true);
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {

        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager
                    .getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(PetriNetsFrame.class.getName())
                    .log(java.util.logging.Level.SEVERE, null, ex);
        }
		/* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                new PetriNetsFrame().setVisible(true);
            }
        });

    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenu Animate;
    private javax.swing.JMenuItem SaveGraphNet;
    private javax.swing.JMenuItem SaveMethodInNetLibrary;
    private javax.swing.JMenuItem SaveNetAsMethod;
    private javax.swing.JMenuItem SavePetriNetAs;
    private javax.swing.JMenuItem centerLocationOfGraphNet;
    private javax.swing.JMenu editMenu;
    private javax.swing.JMenuItem editNetParameters;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JMenuItem itemAnimateEvent;
    private javax.swing.JMenuItem itemAnimateNet;
    private javax.swing.JMenuItem itemRunEvent;
    private javax.swing.JMenuItem jMenuItem2;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JList<String> leftMenuList;
    private javax.swing.JPanel leftNenuPanel;
    private javax.swing.JPanel modelingParametersPanel;
    private javax.swing.JPanel modelingParametersPanel1;
    private javax.swing.JPanel modelingParametersPanel2;
    private javax.swing.JPanel modelingResultsPanel;
    private javax.swing.JPanel modelingResultsPanel1;
    private javax.swing.JPanel modelingResultsPanel2;
    private javax.swing.JSplitPane modelingResultsSplitPane;
    private javax.swing.JSplitPane modelingResultsSplitPane1;
    private javax.swing.JSplitPane modelingResultsSplitPane2;
    private javax.swing.JLabel netNameLabel;
    private javax.swing.JLabel netNameLabel1;
    private javax.swing.JLabel netNameLabel2;
    private javax.swing.JTextField netNameTextField;
    private javax.swing.JTextField netNameTextField1;
    private javax.swing.JTextField netNameTextField2;
    private javax.swing.JButton newArcButton;
    private javax.swing.JButton newArcButton1;
    private javax.swing.JButton newArcButton2;
    private javax.swing.JMenuItem newMenuItem;
    private javax.swing.JButton newPlaceButton;
    private javax.swing.JButton newPlaceButton1;
    private javax.swing.JButton newPlaceButton2;
    private javax.swing.JButton newTransitionButton;
    private javax.swing.JButton newTransitionButton1;
    private javax.swing.JButton newTransitionButton2;
    private javax.swing.JMenuItem openMenuItem;
    private javax.swing.JMenuItem openMethodMenuItem;
    private javax.swing.JPanel petriNetDesign;
    private javax.swing.JPanel petriNetDesign1;
    private javax.swing.JPanel petriNetDesign2;
    private javax.swing.JScrollPane petriNetPanelScrollPane;
    private javax.swing.JScrollPane petriNetPanelScrollPane1;
    private javax.swing.JScrollPane petriNetPanelScrollPane2;
    private javax.swing.JMenuBar petriNetsFrameMenuBar;
    private javax.swing.JSplitPane petriNetsFrameSplitPane;
    private javax.swing.JSplitPane petriNetsFrameSplitPane1;
    private javax.swing.JSplitPane petriNetsFrameSplitPane2;
    private javax.swing.JToolBar petriNetsFrameToolBar;
    private javax.swing.JToolBar petriNetsFrameToolBar1;
    private javax.swing.JToolBar petriNetsFrameToolBar2;
    private javax.swing.JButton playPauseAnimationButton;
    private javax.swing.JTextArea protocolTextArea;
    private javax.swing.JScrollPane protokolScrollPane;
    private javax.swing.JScrollPane protokolScrollPane1;
    private javax.swing.JScrollPane protokolScrollPane2;
    private javax.swing.JTextArea protokolTextArea1;
    private javax.swing.JTextArea protokolTextArea2;
    private javax.swing.JMenuItem redoMenuItem;
    private javax.swing.JButton runEventButton1;
    private javax.swing.JButton runEventButton2;
    private javax.swing.JMenu runMenu;
    private javax.swing.JButton runOneEventButton;
    private javax.swing.JButton runPetriNetButton1;
    private javax.swing.JButton runPetriNetButton2;
    private javax.swing.JMenu save;
    private javax.swing.JScrollPane scrollPane;
    private javax.swing.JButton skipBackwardAnimationButton;
    private javax.swing.JButton skipForwardAnimationButton;
    private javax.swing.JLabel speedLabel;
    private javax.swing.JSlider speedSlider;
    private javax.swing.JScrollPane statisticsScrollPane;
    private javax.swing.JScrollPane statisticsScrollPane1;
    private javax.swing.JScrollPane statisticsScrollPane2;
    private javax.swing.JTextArea statisticsTextArea;
    private javax.swing.JTextArea statisticsTextArea1;
    private javax.swing.JTextArea statisticsTextArea2;
    private javax.swing.JButton stopAnimationButton;
    private javax.swing.JLabel timeModelingLabel;
    private javax.swing.JLabel timeModelingLabel1;
    private javax.swing.JLabel timeModelingLabel2;
    private javax.swing.JTextField timeModelingTextField;
    private javax.swing.JTextField timeModelingTextField1;
    private javax.swing.JTextField timeModelingTextField2;
    private javax.swing.JTextField timeStartField;
    private javax.swing.JTextField timeStartField1;
    private javax.swing.JTextField timeStartField2;
    private javax.swing.JLabel timeStartLabel;
    private javax.swing.JLabel timeStartLabel1;
    private javax.swing.JLabel timeStartLabel2;
    private javax.swing.JMenuItem undoMenuItem;
    // End of variables declaration//GEN-END:variables
    private static PetriNetsPanel petriNetsPanel;
    private FileUse fileUse = new FileUse();
    private ErrorFrame errorFrame = new ErrorFrame();
    private DefaultListModel<String> leftMenuListModel = new DefaultListModel<>();
    /*private javax.swing.JButton consistBtn;
    private javax.swing.JButton poolBtn;
    private javax.swing.JButton newThreadBtn;
    private javax.swing.JButton lockBtn;
    private javax.swing.JButton guardBtn;*/
    
    private static final UndoManager undoManager = new UndoManager();
    private static final UndoableEditSupport undoSupport = new UndoableEditSupport();
    
    public static UndoableEditSupport getUndoSupport() {
        return undoSupport;
    }
    
    /**
     * A petri-object model that is used for displaying animation
     * and can be paused an unpaused
     */
    public AnimRunPetriObjModel animationModel;
    
    /**
     *  A petri-object that is used for displaying animation
     * and can be paused an unpaused, if there's no parent model
     */
    public AnimRunPetriSim animationPetriObject;
    
    /**
     * The thread on which animation happens. Is stored here so that it
     * can be interrupted if stop button is pressed
     */
    public Thread animationThread;
}
