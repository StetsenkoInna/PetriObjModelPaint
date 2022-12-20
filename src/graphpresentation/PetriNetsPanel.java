/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package graphpresentation;

import PetriObj.ExceptionInvalidNetStructure;
import PetriObj.PetriP;
import PetriObj.PetriT;
import PetriObj.ArcIn;
import PetriObj.ArcOut;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import graphnet.GraphPetriNet;
import graphnet.GraphPetriPlace;
import graphnet.GraphPetriTransition;
import graphnet.GraphArcIn;
import graphnet.GraphArcOut;
import graphpresentation.undoable_edits.AddArcEdit;
import graphpresentation.undoable_edits.DeleteArcEdit;
import graphpresentation.undoable_edits.DeleteGraphElementsEdit;
import graphpresentation.undoable_edits.PasteElementsEdit;

/**
 * Creates new form PetriNetsPanel
 *
 * @author Ольга
 */
public class PetriNetsPanel extends javax.swing.JPanel {

    /**
     * Creates new form PetriNetsPanel
     */
    private static int id; // нумерація графічних елементів
    private GraphPetriNet graphNet;  //added 4.12.2012
    private boolean isSettingArc;
    private GraphElement current;
    private GraphElement choosen;
    private GraphArc currentArc;
    private GraphArc choosenArc;
    private int savedId;
    public SetArc setArcFrame = new SetArc(this);
    public SetPosition setPositionFrame = new SetPosition(this);
    public SetTransition setTransitionFrame = new SetTransition(this);
    private JTextField nameTextField;
    private final String DEFAULT_NAME = "Untitled";
    private Point prevMouseLocation;
    private Point startDragMouseLocation = null;
    private Point currentDragMouseLocation = null;
    private List<GraphElement> choosenElements = new ArrayList<>();
    private double scale = 1.0;
    private boolean leftMouseButtonPressed = false;

    private List<GraphElement> copiedElements;
    
    private static PetriNetsPanel instance; // TODO: remove and find a better way

    public List<GraphElement> getChoosenElements() {
        return choosenElements;
    }
	
    public PetriNetsPanel(JTextField textField) {
        instance = this;
        initComponents();
        this.setBackground(Color.WHITE);
      
        nameTextField = textField;
        this.setNullPanel(); // починаємо заново створювати усі списки графічних елементів  //додано 3.12.2012
        setFocusable(true);
  
        addMouseListener(new MouseHandler());
        addMouseMotionListener(new MouseMotionHandler());
        addMouseWheelListener(new MouseWheelHendler());
  
        this.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE||e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    if (choosenArc != null) {
                        removeArc(choosenArc);
                        
                        /* saving this edit for possible undoing */
                        DeleteArcEdit edit = new DeleteArcEdit(instance, choosenArc);
                        PetriNetsFrame.getUndoSupport().postEdit(edit);
                        
                        choosenArc = null;
                        currentArc = null;
                    }
                    if (choosen != null) {
                        try {
                            // TODO: make the following code a separate function
                            List<GraphArcIn> inArcsToBeRemoved = new ArrayList<>();
                            List<GraphArcOut> outArcsToBeRemoved = new ArrayList<>();
                                
                            /* finding arcs that will be deleted along with this element. It's mostly a copy-paste from
                            * PetriGraphNet.removeElement and this functionality probably should be merged,
                            * but copy-pasting was the least invasive method of implementing bulk delete undoing.
                            */ 

                            for (GraphArcIn arc : getGraphNet().getGraphArcInList()) {
                                if (arc.getBeginElement() == choosen 
                                        || arc.getEndElement() == choosen) {
                                    if (!inArcsToBeRemoved.contains(arc)) {
                                        inArcsToBeRemoved.add(arc);
                                    }

                                }
                            }

                            for (GraphArcOut arc : getGraphNet().getGraphArcOutList()) {
                                if (arc.getBeginElement() == choosen 
                                        || arc.getEndElement() == choosen) {
                                    if (!outArcsToBeRemoved.contains(arc)) {
                                        outArcsToBeRemoved.add(arc);
                                    }

                                }
                            }
                            /* found all arcs that will be deleted */   
                            
                            remove(choosen);
                            // TODO: restoring removed arcs too 
                            /* save this action into undo manager so that it can be undone */
                            DeleteGraphElementsEdit edit = 
                                    new DeleteGraphElementsEdit(instance, choosen, 
                                            inArcsToBeRemoved, outArcsToBeRemoved);
                            PetriNetsFrame.getUndoSupport().postEdit(edit);
                            choosen = null;
                            current = null;
                        } catch (ExceptionInvalidNetStructure ex) {
                            Logger.getLogger(PetriNetsPanel.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    if (!choosenElements.isEmpty()) {
                        
                        int result = JOptionPane.showConfirmDialog((Component) null, "Are you sure you want to delete selected elements?",
                                "Delete", JOptionPane.OK_CANCEL_OPTION);
                        if (result == JOptionPane.OK_OPTION) {
                            try {
                                List<GraphArcIn> inArcsToBeRemoved = new ArrayList<>();
                                List<GraphArcOut> outArcsToBeRemoved = new ArrayList<>();                      
                                
                                for (GraphElement graphElement : choosenElements) {
                                    /* finding arcs that will be deleted along with this element. It's mostly a copy-paste from
                                     * PetriGraphNet.removeElement and this functionality probably should be merged,
                                     * but copy-pasting was the least invasive method of implementing bulk delete undoing.
                                    */ 
                                    
                                    for (GraphArcIn arc : getGraphNet().getGraphArcInList()) {
                                        if (arc.getBeginElement() == graphElement 
                                                || arc.getEndElement() == graphElement) {
                                            if (!inArcsToBeRemoved.contains(arc)) {
                                                inArcsToBeRemoved.add(arc);
                                            }

                                        }
                                    }
                                    
                                    for (GraphArcOut arc : getGraphNet().getGraphArcOutList()) {
                                        if (arc.getBeginElement() == graphElement 
                                                || arc.getEndElement() == graphElement) {
                                            if (!outArcsToBeRemoved.contains(arc)) {
                                                outArcsToBeRemoved.add(arc);
                                            }

                                        }
                                    }
                                    /* found all arcs that will be deleted */
                                    
                                    remove(graphElement);
                                    PetriNetsPanel.this.setDefaultColorGraphElements(); //27.07.2018
                                }
                                /* save this action into undo manager so that it can be undone */
                                DeleteGraphElementsEdit edit = 
                                        new DeleteGraphElementsEdit(instance, 
                                                new ArrayList(choosenElements),
                                        inArcsToBeRemoved, outArcsToBeRemoved);
                                
                                PetriNetsFrame.getUndoSupport().postEdit(edit);
                            } catch (ExceptionInvalidNetStructure ex) {
                                Logger.getLogger(PetriNetsPanel.class.getName()).log(Level.SEVERE, null, ex);
                            } finally {
                                choosenElements.clear();
                                PetriNetsPanel.this.setDefaultColorGraphElements();//27.07.2018
                                
                            }
                        }
                    }
                }

               
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_A) {
                    
                    selectAll();
                    repaint();
                }

                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_C) {
                    copiedElements = new ArrayList<>(choosenElements);
                }

                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_V) {
                    pasteAction();
                }
            }
        });

    }
    
    /**
     * A handler for ctrl+V. Clones elements and arcs associated with them and pastes
     * them onto the canvas
     */
    public void pasteAction() {
        if (copiedElements != null && !copiedElements.isEmpty()) {
            GraphPetriNet.GraphNetFragment clonedFragment = 
                    graphNet.bulkCopyNoPasteElements(copiedElements);
            
            addNetFragment(clonedFragment);
            
            copiedElements = new ArrayList<>(clonedFragment.elements);
            
            PetriNetsFrame.getUndoSupport().postEdit(
                    new PasteElementsEdit(this, clonedFragment)
            );    
        }
    }
    
    /**
     * Adds a fragment of a net onto the canvas. Fragments' coordinates are 
     * updated in the process.
     * @param fragment fragment to add
     */
    public void addNetFragment(GraphPetriNet.GraphNetFragment fragment) {
        List<GraphElement> elementsToSpawn = fragment.elements;               

        // de-selecting any selected elements
        for (GraphElement prevElement: choosenElements) {
            prevElement.setColor(Color.BLACK);
        }
        choosenElements.clear();

        for (GraphElement element: elementsToSpawn) {
            Point2D spawnPoint = element.getGraphElementCenter();
            spawnPoint.setLocation(spawnPoint.getX() + 15, spawnPoint.getY() + 15);

            element.setNewCoordinates(spawnPoint);
            
            if (element instanceof GraphPetriPlace) {
                this.getGraphNet().getGraphPetriPlaceList().add((GraphPetriPlace)element);
            } else {
                this.getGraphNet().getGraphPetriTransitionList().add((GraphPetriTransition)element);
            }
            
            choosenElements.add(element);
            element.setColor(Color.GREEN);
        }
        
        for (GraphArcIn arcIn : fragment.inArcs) {
            getGraphNet().getGraphArcInList().add(arcIn);
        }
        
        for (GraphArcOut arcOut : fragment.outArcs) {
            getGraphNet().getGraphArcOutList().add(arcOut);
        }

        // wtf is this
        for (GraphArcOut arcOut : fragment.outArcs) {
            for (GraphArcIn arcIn : fragment.inArcs) {
                int inBeginId = ((GraphPetriPlace) arcIn.getBeginElement()).getId();
                int inEndId = ((GraphPetriTransition) arcIn.getEndElement()).getId();
                int outBeginId = ((GraphPetriTransition) arcOut.getBeginElement()).getId();
                int outEndId = ((GraphPetriPlace) arcOut.getEndElement()).getId();
                if (inBeginId == outEndId && inEndId == outBeginId) {
                    arcIn.twoArcs(arcOut); // two arcs
                }
                arcIn.updateCoordinates();
                arcOut.updateCoordinates();
            }
        }
        
        repaint();
    }

    public void removeArc(GraphArc s) {
        if (s == null) {
            return;
        }
        if (s == currentArc) {
            currentArc = null;
        }

        if (s.getClass().equals(GraphArcOut.class)) {
            graphNet.getGraphArcOutList().remove((GraphArcOut) s); //added by Inna 4.12.2012

        } else {
            graphNet.getGraphArcInList().remove((GraphArcIn) s); //added by Inna 4.12.2012
        }

        repaint();
    }

    @Override
    public void paintComponent(Graphics g) {
        
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.scale(scale, scale);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
        this.requestFocusInWindow(); //added 1.06.2013
        //додано 3.12.2012
        if (graphNet == null) {
            graphNet = new GraphPetriNet();
        }
        
        graphNet.paintGraphPetriNet(g2, g);
       
        if (currentArc != null) {
             currentArc.drawGraphElement(g2);
        }
        if (choosenArc != null) {
             choosenArc.drawGraphElement(g2);
        }
        if (current != null) {
            current.drawGraphElement(g2);
        }
        if (choosen != null) {
             choosen.drawGraphElement(g2);
        } 
        for (GraphElement graphElement : choosenElements) {
         
            graphElement.drawGraphElement(g2);
        }
     
     //  printPointLocation(currentDragMouseLocation,"current");
     //  printPointLocation(startDragMouseLocation,"start");
    //   printArraySize(choosenElements, "");
        if (currentDragMouseLocation != null && startDragMouseLocation != null && leftMouseButtonPressed) {
            g2.setStroke((Stroke) new BasicStroke(1.0f,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_BEVEL,
                    20.0f,
                    new float[]{15.0f, 15.0f},0.0f));
            g2.drawRect(startDragMouseLocation.x, 
                    startDragMouseLocation.y,
                    currentDragMouseLocation.x - startDragMouseLocation.x,
                    currentDragMouseLocation.y - startDragMouseLocation.y);
        }
    }
    
    private void printPointLocation(Point point, String s){
        if(point!=null){
            System.out.println(s+"  "+point.getX());
        } else{
            System.out.println("NULL");
        }
    }
    private void printArraySize(List<GraphElement> list, String s){
        if(list!=null){
            System.out.println(s+"  "+list.size());
        } else{
            System.out.println("NULL");
        }
    }
    

    public GraphElement find(Point2D p) {
        for (GraphPetriPlace pp : graphNet.getGraphPetriPlaceList()) {
            if (pp.isGraphElement(p)) {
                return pp;
            }
        }
        for (GraphPetriTransition pt : graphNet.getGraphPetriTransitionList()) {
            if (pt.isGraphElement(p)) {
                return pt;
            }
        }
        return null;
    }

    public GraphArc findArc(Point2D p) {
        for (GraphArcOut to : graphNet.getGraphArcOutList()) {
            if (to.isEnoughDistance(p)) {
                return to;
            }
        }
        for (GraphArcIn ti : graphNet.getGraphArcInList()) {
            if (ti.isEnoughDistance(p)) {
                return ti;
            }
        }
        return null;
    }

    public void remove(GraphElement s) throws ExceptionInvalidNetStructure {
        if (s == null) {
            return;
        }
        if (s == current) {
            current = null;

        }
        /* if(current!=null)System.out.println("remove : "+current.getName()+"  "+s.getName());
        else System.out.println("remove : current null");*/
        graphNet.delGraphElement(s); //added by Inna 4.12.2012
        
        repaint();
    }

    public class MouseWheelHendler implements MouseWheelListener{

		@Override
		public void mouseWheelMoved(MouseWheelEvent e) {
			if(e.getWheelRotation()==-1 && scale <=0.15)return;
			scale+=(double)e.getWheelRotation()/10;
			repaint();
		}
    	
    }
    
    public void selectAll() { // works when key event is Ctrl+a  
        choosenElements.clear();
        for (GraphPetriPlace p : graphNet.getGraphPetriPlaceList()) {
            choosenElements.add(p);
            p.setColor(Color.GREEN);
            
        }
        for (GraphPetriTransition tr : graphNet.getGraphPetriTransitionList()) {
            choosenElements.add(tr);
            tr.setColor(Color.GREEN);
            
        }
    }

    private void setDefaultColorGraphElements() {
        for (GraphPetriPlace p : graphNet.getGraphPetriPlaceList()) {
            p.setColor(Color.BLACK);
        }
        for (GraphPetriTransition tr : graphNet.getGraphPetriTransitionList()) {
            tr.setColor(Color.BLACK);
        }
    }

    private void setDefaultColorGraphArcs() {
        for (GraphArcIn ti : graphNet.getGraphArcInList()) {
            ti.setColor(Color.BLACK);
        }

        for (GraphArcOut to : graphNet.getGraphArcOutList()) {
            to.setColor(Color.BLACK);
        }
    }

    public void redraw() {
        setDefaultColorGraphElements();
        setDefaultColorGraphArcs();
        repaint();
    }
    

    public class MouseHandler extends MouseAdapter {
        private java.util.Timer timer;
        private boolean isMouseButtonHold = false;

        @Override
        public void mousePressed(MouseEvent ev) {
            startTimer();

            Point scaledCurrentMousePoint = new Point((int) (ev.getX() / scale), (int) (ev.getY() / scale));
            if (SwingUtilities.isLeftMouseButton(ev)) {
                leftMouseButtonPressed = true;
            }
            if (startDragMouseLocation == null) {
                startDragMouseLocation = scaledCurrentMousePoint;
            }
            prevMouseLocation = scaledCurrentMousePoint;
            if (current != null) {
                current.setColor(Color.BLACK); //26.07.2018
                current = null;
                repaint();
            } else {
                current = find(scaledCurrentMousePoint);
                if (current != null) {
                    setDefaultColorGraphElements();
                    current.setColor(Color.BLUE); //26.07.2018
                    choosen = current;

                    if (!isSettingArc && isMouseButtonHold) {
                        current.setNewCoordinates(scaledCurrentMousePoint);
                        setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));

                        for (GraphArcIn ti : graphNet.getGraphArcInList()) {
                            ti.updateCoordinates();
                        }

                        for (GraphArcOut to : graphNet.getGraphArcOutList()) {
                            to.updateCoordinates();
                        }
                    }
                    
                    if( choosenArc!=null){
                        choosenArc.setColor(Color.BLACK);//26.07.2018
                    }
                    choosenArc = null;
                }
                // currentPlacementPoint = e.getPoint();
            }

            if (isSettingArc == true) {
                current = find(scaledCurrentMousePoint);
                if (current != null) {
                    current.setColor(Color.BLUE);
                   
                    if (current.getClass().equals(GraphPetriPlace.class)) {
                        currentArc = new GraphArcIn();
                         currentArc.setColor(Color.BLUE);//26.07.2018
                        graphNet.getGraphArcInList().add((GraphArcIn) currentArc); //3.12.2012
                        currentArc.settingNewArc(current); //set begin element, point and setting LINe(0,0)
                    } else if (current.getClass().equals(GraphPetriTransition.class)) { //26.01.2013
                        currentArc = new GraphArcOut();
                         currentArc.setColor(Color.BLUE);//26.07.2018
                        graphNet.getGraphArcOutList().add((GraphArcOut) currentArc); //3.12.2012
                        currentArc.settingNewArc(current);
                    }
                } else {    //26.01.2013
                    
                    isSettingArc = false;
                }
                // System.out.println("after added tie we have such graph net:");
                // graphNet.print();
            } 
            
            isSettingArc = false;//26.01.2013
            choosenArc = null;
            repaint();
        }
        
       
        @Override
        public void mouseClicked(MouseEvent ev) {
            
            Point scaledCurrentMousePoint = new Point((int) (ev.getX() / scale), (int) (ev.getY() / scale));
            
           if (current == null && currentArc == null) { // previous click was empty
          
          //  PetriNetsPanel.this.printPointLocation(prevMouseLocation, "clear");
               setDefaultColorGraphElements();
               setDefaultColorGraphArcs();
               choosenElements.clear();
               choosen = null;
            }
            if (current != null) {
                current.setColor(Color.BLUE); //26.07.2018
                choosenElements.clear(); // 27.08.2018
            } else {
                current = find(scaledCurrentMousePoint);
                if (current != null) {
                    current.setColor(Color.BLUE);//26.07.2018
                    choosen = current;
                }
                if (current != null && (ev.getClickCount() >= 2 || SwingUtilities.isRightMouseButton(ev))) { //change 2->1??
                    current.setColor(Color.BLUE);//26.07.2018
                    choosen = current;

                    if (choosen.getClass().equals(GraphPetriPlace.class)) {
                        setPositionFrame.setVisible(true);
                        setPositionFrame.setInfo(choosen);

                    } else {
                        setTransitionFrame.setVisible(true);
                        setTransitionFrame.setInfo(choosen);
                    }
                }

                currentArc = findArc(scaledCurrentMousePoint);
                if (currentArc != null && ev.getClickCount() >= 2) {
                    currentArc.setColor(Color.BLUE);
                    choosenArc = currentArc;
                    setArcFrame.setVisible(true);
                    setArcFrame.setInfo(choosenArc);
                }
                if (currentArc != null) {
                    currentArc.setColor(Color.BLUE);
                    choosenArc = currentArc;
                    choosen = null;
                    currentArc = null;
                }
            }
            setDefaultColorGraphElements();
            current = null;

            setCursor(Cursor.getDefaultCursor());
            repaint();
        }

        @Override
        public void mouseReleased(MouseEvent ev) {
            removeTimer();

            Point scaledCurrentMousePoint = new Point((int) (ev.getX() / scale), (int) (ev.getY() / scale));
            if (startDragMouseLocation != null && currentDragMouseLocation != null && leftMouseButtonPressed) {
                for (GraphPetriPlace p : graphNet.getGraphPetriPlaceList()) {
                    if (p.getGraphElementCenter().getX() >= startDragMouseLocation.x
                            && p.getGraphElementCenter().getX() <= currentDragMouseLocation.getX()
                            && p.getGraphElementCenter().getY() >= startDragMouseLocation.y
                            && p.getGraphElementCenter().getY() <= currentDragMouseLocation.getY()) {
                        choosenElements.add(p);
                        p.setColor(Color.GREEN);
                    }
                }
                for (GraphPetriTransition tr : graphNet.getGraphPetriTransitionList()) {
                    if (tr.getGraphElementCenter().getX() >= startDragMouseLocation.x
                            && tr.getGraphElementCenter().getX() <= currentDragMouseLocation.getX()
                            && tr.getGraphElementCenter().getY() >= startDragMouseLocation.y
                            && tr.getGraphElementCenter().getY() <= currentDragMouseLocation.getY()) {
                        choosenElements.add(tr);
                        tr.setColor(Color.GREEN);
                    }
                }
            repaint();
            }
            
            startDragMouseLocation = null;
            currentDragMouseLocation = null;
            current = null;
          //  setDefaultColorGraphElements();// deleted 27.07.2018
            
            setCursor(Cursor.getDefaultCursor());
            if (currentArc != null) {
                currentArc.setColor(Color.BLUE);
                current = find(scaledCurrentMousePoint);
                if (current != null) {
                    current.setColor(Color.BLUE);
                    if (currentArc.finishSettingNewArc(current)) {
                        currentArc.setPetriElements();
                        currentArc.changeBorder();
                        currentArc.updateCoordinates();
                        isSettingArc = false;
                        currentArc.setColor(Color.BLACK);
                        int currBeginId, currEndId;
                        if (currentArc.getClass().equals(GraphArcIn.class)) {
                            currBeginId = ((GraphPetriPlace) currentArc.getBeginElement()).getId();
                            currEndId = ((GraphPetriTransition) currentArc.getEndElement()).getId();
                        } else {
                            currBeginId = ((GraphPetriTransition) currentArc.getBeginElement()).getId();
                            currEndId = ((GraphPetriPlace) currentArc.getEndElement()).getId();
                        }

                        for (GraphArcIn ti : graphNet.getGraphArcInList()) {
                            if (((GraphPetriPlace) ti.getBeginElement()).getId() == currEndId 
                                    && ((GraphPetriTransition) ti.getEndElement()).getId() == currBeginId) {
                                currentArc.twoArcs(ti);
                                currentArc.updateCoordinates();
                            }
                        }
                        for (GraphArcOut to : graphNet.getGraphArcOutList()) {
                            if (((GraphPetriTransition) to.getBeginElement()).getId() == currEndId 
                                    && ((GraphPetriPlace) to.getEndElement()).getId() == currBeginId) {
                                currentArc.twoArcs(to);
                                currentArc.updateCoordinates();
                            }
                        }
                        
                        /* saving the action of adding an arc for possible undoing */ 
                        AddArcEdit edit = new AddArcEdit(instance, currentArc);
                        PetriNetsFrame.getUndoSupport().postEdit(edit);
                        
                        currentArc = null;
                        setDefaultColorGraphArcs();
                    } else {                        //1.02.2013 цей фрагмент дозволяє відслідковувати намагання 
                        removeCurrentArc();// з"єднати позицію з позицією чи перехід з переходом
                        //та знищувати неправильно намальовану дугу
                    }
                    current = null;
                    setDefaultColorGraphElements();
                } else {
                    removeCurrentArc();//1.02.2013;
                }
            }
            currentArc = null;
            setDefaultColorGraphArcs();
            leftMouseButtonPressed = false;
            repaint();
           
        }

        private void startTimer() {
            if(timer == null)
            {
                timer = new java.util.Timer();
            }
            timer.schedule(new TimerTask()
            {
                public void run()
                {
                    isMouseButtonHold = true;
                }
            },500);
        }

        private void removeTimer() {
            if (timer != null) {
                isMouseButtonHold = false;
                timer.cancel();
                timer = null;
            }
        }
    }

    private void removeCurrentArc() { //1.02.2013 цей метод дозволяє знищувати намальовану дугу
        if (currentArc.getClass().equals(GraphArcIn.class)) // 
        {
            graphNet.getGraphArcInList().remove(currentArc);
        } else if (currentArc.getClass().equals(GraphArcOut.class)) {
            graphNet.getGraphArcOutList().remove(currentArc);
        } else ;
        currentArc = null;
        
        repaint();
    }

    private class MouseMotionHandler implements MouseMotionListener {

        @Override
        public void mouseDragged(MouseEvent ev) {
            Point scaledCurrentMousePoint = new Point((int) (ev.getX() / scale), (int) (ev.getY() / scale));
            if (choosen == null && choosenElements.isEmpty()) {
                PetriNetsPanel.this.setDefaultColorGraphElements();
                currentDragMouseLocation = scaledCurrentMousePoint;
            }
            if (current != null && currentArc == null) {  // moving place or transition
                
                current.setColor(Color.BLUE);
                PetriNetsPanel.this.setDefaultColorGraphArcs(); //26.07.2018

                current.setNewCoordinates(scaledCurrentMousePoint);
                for (GraphArcIn ti : graphNet.getGraphArcInList()) {
                    ti.updateCoordinates();
                }
                for (GraphArcOut to : graphNet.getGraphArcOutList()) {
                    to.updateCoordinates();
                }
            }
           
            if (currentArc != null && current != null) { //creating the arc
                currentArc.setColor(Color.BLUE);
                current.setColor(Color.BLUE);
                currentArc.setNewCoordinates(scaledCurrentMousePoint);
            }
          
            if (!choosenElements.isEmpty() && leftMouseButtonPressed) { //moving choosenElements
               
                for(GraphElement e: choosenElements){
                    e.setColor(Color.GREEN);
                }
                
                setCursor(new Cursor(Cursor.MOVE_CURSOR));
                for (GraphElement graphElement : choosenElements) {
                    Point currentLocation = new Point(
                            (int) graphElement.getGraphElementCenter().getX(),
                            (int) graphElement.getGraphElementCenter().getY());

                    Point newLocation = new Point(
                            currentLocation.x
                            + (int) scaledCurrentMousePoint.getX()
                            - prevMouseLocation.x, currentLocation.y
                            + (int) scaledCurrentMousePoint.getY()
                            - prevMouseLocation.y);
                    graphElement.setNewCoordinates(newLocation);
                }
                for (GraphArcIn ti : graphNet.getGraphArcInList()) {
                    ti.updateCoordinates();
                }
                for (GraphArcOut to : graphNet.getGraphArcOutList()) {
                    to.updateCoordinates();
                }
                prevMouseLocation = scaledCurrentMousePoint;
            }
            repaint();
        }

        @Override
        public void mouseMoved(MouseEvent ev) {
            Point scaledCurrentMousePoint = new Point((int) (ev.getX() / scale), (int) (ev.getY() / scale));
            if (current != null && currentArc == null) {
                current.setColor(Color.BLUE);
                PetriNetsPanel.this.setDefaultColorGraphArcs();
                setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
                current.setNewCoordinates(scaledCurrentMousePoint);
                repaint();
            }
        }
    }

    public GraphElement getCurrent() {
        return current;
    }

    public void setCurrent(GraphElement e) {
        current = e;
    }

    public GraphElement getChoosen() {
        return choosen;
    }
    
    public void setChoosen(GraphElement chosen) {
        this.choosen = chosen;
    }

    public void setCurrentGraphArc(GraphArc t) {
        currentArc = t;
    }

    public GraphArc getCurrentGraphArc() {
        return currentArc;
    }

    public GraphArc getChoosenArc() {
        return choosenArc;
    }
    
    public void setChoosenArc(GraphArc arc) {
        this.choosenArc = arc;
    }

    public int getSavedId() {
        return savedId;
    }

    public void saveId() {
        this.savedId = id;
    }

    public static String getPetriTName() {
        return "T" + id;
    }

    public static String getPetriPName() {
        return "P" + id;
    }

    public void setIsSettingArc(boolean b) { //26.01.2013
        if (b) {
            setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
        } else {
            setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        }
        isSettingArc = b;
    }

    public final void setNullPanel() {
        current = null;
        currentArc = null;
        choosen = null;
        choosenArc = null;
       
        id = 0;
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext(); //додано Інна 20.11.2012
        ArcOut.initNext(); //додано Інна 20.11.2012
        GraphPetriPlace.setNullSimpleName();
        GraphPetriTransition.setNullSimpleName();
        graphNet = new GraphPetriNet();
        
        repaint();
    }

    public void addGraphNet(GraphPetriNet net) {

        graphNet = net;
       
        int maxIdPetriNet = 0; //
        for (GraphPetriPlace pp : graphNet.getGraphPetriPlaceList()) {  //відшукуємо найбільший id для позицій
            if (maxIdPetriNet < pp.getId()) {
                maxIdPetriNet = pp.getId();
            }
        }
        for (GraphPetriTransition pt : graphNet.getGraphPetriTransitionList()) { //відшукуємо найбільший id для переходів і позицій 
            if (maxIdPetriNet < pt.getId()) {
                maxIdPetriNet = pt.getId();
            }
        }
        if (maxIdPetriNet > id) // встановлюємо новий id - найбільший
        {
            id = maxIdPetriNet;
        }
        id++;
        
        repaint();
    }

    public void deletePetriNet() {
        graphNet = null;
        repaint();
    }

    public GraphPetriNet getGraphNet() {
        return graphNet;
    }

    public void setGraphNet(GraphPetriNet net) { //коректно працює тільки якщо потім не змінювати граф
        //рекомендується використовувати addGraphNet
        graphNet = net;
        repaint();
    }
/*
    public List<GraphPetriNet> getGraphNetList() {  //11.01.13
        return graphNetList;
    }

    public GraphPetriNet getLastGraphNetList() {  //11.01.13
        return graphNetList.get(graphNetList.size() - 1);
    }
*/
    public static int getIdElement() {  //edited by Inna 1.10.2018
        return id++;
    }
  
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setBackground(new java.awt.Color(229, 229, 229));
        setPreferredSize(new java.awt.Dimension(20000, 20000));
        setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
    
    public void animateIn(PetriT tr) {    //Саша 05.17
        
        ArrayList<GraphArcIn> list = new ArrayList<>();
        for (GraphArcIn t : graphNet.getGraphArcInList()) {
            if (t.getArcIn().getNumT() == tr.getNumber()) {
                list.add(t);
            }
        }
        animArcIn(list,100,3,new Color(255, 77, 77));
        animArcIn(list,100,5);
        animArcIn(list,100,7);
        animArcIn(list,100,5);
        animArcIn(list,100,3);
        animArcIn(list,100,1, Color.BLACK);
    }
    
    public void animateT(PetriT tr) {   //Саша 05.17
        ArrayList<GraphPetriTransition> list = new ArrayList<>();
        for (GraphPetriTransition t : graphNet.getGraphPetriTransitionList()) {
            if (t.getPetriTransition().getNumber() == tr.getNumber()) {
                list.add(t);
            }
        }
        animTransitions(list,100,7,new Color(255, 77, 77));
        animTransitions(list,100,10);
        animTransitions(list,100,12);
        animTransitions(list,100,10);
        animTransitions(list,100,7);
        animTransitions(list,100,5,Color.BLACK);

    }
    public void animateP(ArrayList<Integer> inP) {  //Саша 05.17
        ArrayList<GraphPetriPlace> list = new ArrayList<>();
        for (GraphPetriPlace p : graphNet.getGraphPetriPlaceList()) {
            for (Integer inp : inP) {
                if (p.getPetriPlace().getNumber() == inp) {
                    list.add(p);
                }
            }
        }
        animPlaces(list,100, 5, new Color(255, 77, 77));
        animPlaces(list,100, 7);
        animPlaces(list,100, 10);
        animPlaces(list,100, 7);
        animPlaces(list,100, 5);
        animPlaces(list,100, 2, Color.BLACK);
    }
    
    public void animateOut(PetriT eventMin) {   //Саша 05.17
        ArrayList<GraphArcOut> list = new ArrayList<>();
        for (GraphArcOut t : graphNet.getGraphArcOutList()) {
            if (t.getArcOut().getNumT() == eventMin.getNumber()) {
                list.add(t);
            }
        }
        animArcOut(list, 50, 3,new Color(255, 77, 77));
        animArcOut(list, 50, 5);
        animArcOut(list, 50, 7);
        animArcOut(list, 50, 5);
        animArcOut(list, 50, 3);
        animArcOut(list, 50, 1, Color.BLACK);
    }
    
    private void animArcIn(ArrayList<GraphArcIn> list, long sleepDelay, int lineWidth, Color color) {
        try {
            for (GraphArcIn a : list) {
                a.setLineWidth(lineWidth);
                a.setColor(color);
                this.repaint();
            }
            Thread.sleep(sleepDelay);
        } catch (InterruptedException ex) {
            Logger.getLogger(PetriNetsPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    private void animArcIn(ArrayList<GraphArcIn> list, long sleepDelay, int lineWidth) {
        try {
            for (GraphArcIn a : list) {
                a.setLineWidth(lineWidth);
                this.repaint();
            }
            Thread.sleep(sleepDelay);
        } catch (InterruptedException ex) {
            Logger.getLogger(PetriNetsPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private void animArcOut(ArrayList<GraphArcOut> list, long sleepDelay, int lineWidth, Color color) {
        try {
            for (GraphArcOut a : list) {
                a.setLineWidth(lineWidth);
                a.setColor(color);
                this.repaint();
            }
            Thread.sleep(sleepDelay);
        } catch (InterruptedException ex) {
            Logger.getLogger(PetriNetsPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    private void animArcOut(ArrayList<GraphArcOut> list, long sleepDelay, int lineWidth) {
        try {
            for (GraphArcOut a : list) {
                a.setLineWidth(lineWidth);
                this.repaint();
            }
            Thread.sleep(sleepDelay);
        } catch (InterruptedException ex) {
            Logger.getLogger(PetriNetsPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private void animPlaces(ArrayList<GraphPetriPlace> list, long sleepDelay, int lineWidth, Color color) {
        try {
            for (GraphPetriPlace p : list) {
                p.setLineWidth(lineWidth);
                p.setColor(color);
                this.repaint();
            }
            Thread.sleep(sleepDelay);
        } catch (InterruptedException ex) {
            Logger.getLogger(PetriNetsPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private void animPlaces(ArrayList<GraphPetriPlace> list, long sleepDelay, int lineWidth) {
        try {
            for (GraphPetriPlace p : list) {
                p.setLineWidth(lineWidth);
                this.repaint();
            }
            Thread.sleep(sleepDelay);
        } catch (InterruptedException ex) {
            Logger.getLogger(PetriNetsPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
     private void animTransitions(ArrayList<GraphPetriTransition> list, long sleepDelay, int lineWidth, Color color) {
        try {
            for (GraphPetriTransition tr : list) {
                tr.setLineWidth(lineWidth);
                tr.setColor(color);
                this.repaint();
            }
            Thread.sleep(sleepDelay);
        } catch (InterruptedException ex) {
            Logger.getLogger(PetriNetsPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
     private void animTransitions(ArrayList<GraphPetriTransition> list, long sleepInterval, int lineWidth) {
        try {
            for (GraphPetriTransition tr : list) {
                tr.setLineWidth(lineWidth);
                this.repaint();
            }
            Thread.sleep(sleepInterval);
        } catch (InterruptedException ex) {
            Logger.getLogger(PetriNetsPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
     
    
}
