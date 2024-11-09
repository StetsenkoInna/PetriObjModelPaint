package graphpresentation;

import PetriObj.ExceptionInvalidNetStructure;
import PetriObj.PetriNet;
import PetriObj.PetriP;
import PetriObj.PetriT;
import PetriObj.ArcIn;
import PetriObj.ArcOut;
import PetriObj.ExceptionInvalidTimeDelay;

import java.awt.FileDialog;

import PetriObj.PetriMainElement;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.awt.Point;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;

import graphnet.GraphArcIn;
import graphnet.GraphArcOut;
import graphnet.GraphPetriPlace;
import graphnet.GraphPetriTransition;
import graphnet.GraphPetriNet;
import java.awt.Component;

import java.awt.geom.Point2D;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Objects;

import utils.Utils;

/**
 *
 * @author Olya &  Inna
 */
public class FileUse {

    private static final String PATTERN = ".pns";
    
    public static String openFile(PetriNetsPanel panel, JFrame frame) throws ExceptionInvalidNetStructure {
        String pnetName = "";
        FileDialog fdlg;
        fdlg = new FileDialog(frame, "Open a file ",
                FileDialog.LOAD);
        fdlg.setVisible(true);
        FileInputStream fis = null;
        ObjectInputStream ois = null;
        try {
            // System.out.println("Opening file '" + fdlg.getDirectory() + fdlg.getFile() + "'");
            fis = new FileInputStream(fdlg.getDirectory() + fdlg.getFile());
            ois = new ObjectInputStream(fis);
            GraphPetriNet net = ((GraphPetriNet) ois.readObject()).clone();
            
            // if there are transitions where b != 0, find them and
            // ask the user if they want to remove exit times from buffers
            GraphPetriTransition[] tWithNon0Buffers =  net.getGraphPetriTransitionList().stream()
                    .filter(
                            transition -> transition.getPetriTransition().getBuffer() != 0)
                    
                    .toArray(GraphPetriTransition[]::new);
            if (tWithNon0Buffers.length != 0) {
                // display dialog
                int result = JOptionPane.showConfirmDialog((Component) null, "There are transitions in this net with non-empty buffers. Do you want to clear them?",
                                "Buffers reset", JOptionPane.OK_CANCEL_OPTION);
                if (result == JOptionPane.OK_OPTION) {
                    for (GraphPetriTransition trans : tWithNon0Buffers) {
                        // removing all saved exit times 
                        trans.getPetriTransition().getTimeOut().clear();
                        trans.getPetriTransition().getTimeOut().add(Double.MAX_VALUE);
                        trans.getPetriTransition().setBuffer(0);
                    }
                }
            }
            
            panel.addGraphNet(net);
            pnetName = net.getPetriNet().getName();
            ois.close();
            panel.repaint();

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
                return null;
            }
            try {
                ois.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            } catch (NullPointerException e) {
                return null;
            }

        }
        return pnetName.substring(0, pnetName.length());
    }

    public static void newWorksheet(PetriNetsPanel panel) {
        panel.setNullPanel();
    }

    public static void saveGraphNetAs(PetriNetsPanel panel, JFrame frame) throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        FileDialog fdlg;
        fdlg = new FileDialog(frame,
                "Save Graph Petri net as...",
                FileDialog.SAVE);
        fdlg.setVisible(true);
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        try {
            fdlg.setFilenameFilter(null);
            fos = new FileOutputStream(fdlg.getDirectory() + fdlg.getFile() + PATTERN);
            oos = new ObjectOutputStream(fos);
            panel.getGraphNet().createPetriNet(fdlg.getFile());
            oos.writeObject(panel.getGraphNet());
            oos.close();
        } catch (IOException ex) {
            Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                fos.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
            try {
                oos.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public static void saveGraphNetAs(GraphPetriNet net, JFrame frame) throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        FileDialog fdlg;
        fdlg = new FileDialog(frame,
                "Save Graph Petri net as...",
                FileDialog.SAVE);
        fdlg.setVisible(true);
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        try {
            fdlg.setFilenameFilter(null);
            //System.out.println("Saving GraphNet as '" + fdlg.getDirectory() + fdlg.getFile() + "'");
            net.createPetriNet(fdlg.getFile());
            fos = new FileOutputStream(fdlg.getDirectory() + fdlg.getFile() + PATTERN);
            oos = new ObjectOutputStream(fos);
            oos.writeObject(net);
            oos.close();
        } catch (IOException ex) {
            Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                fos.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
            try {
                oos.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public static void savePetriNetAs(PetriNetsPanel panel, JFrame frame) throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        FileDialog fdlg;
        fdlg = new FileDialog(frame,
                "Save Petri net as...",
                FileDialog.SAVE);
        fdlg.setVisible(true);
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        try {
            fdlg.setFilenameFilter(null);
            // System.out.println("Saving PetriNet as '" + fdlg.getDirectory() + fdlg.getFile() + "'");
            panel.getGraphNet().createPetriNet(fdlg.getFile());
            fos = new FileOutputStream(fdlg.getDirectory() + fdlg.getFile() + PATTERN);
            oos = new ObjectOutputStream(fos);
            oos.writeObject(panel.getGraphNet().getPetriNet());
            oos.close();
        } catch (IOException ex) {
            Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                fos.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
            try {
                oos.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public static boolean saveGraphNet(GraphPetriNet pnet, String name) throws ExceptionInvalidNetStructure {  // saving graph in the same folder as project
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        if (name.equalsIgnoreCase("")) {
            name = "Untitled";
        }

        try {
            pnet.createPetriNet(name);
            File file = new File(name + ".pns");
            // System.out.println("Saving path = " + file.getAbsolutePath());
            fos = new FileOutputStream(file);
            oos = new ObjectOutputStream(fos);
            oos.writeObject(pnet);
            oos.close();
        } catch (IOException ex) {
            Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ExceptionInvalidTimeDelay ex) {
            Logger.getLogger(FileUse.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                fos.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
            try {
                oos.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return true;
    }

    public static String openMethod(PetriNetsPanel panel, String methodFullName, JFrame frame) throws ExceptionInvalidNetStructure { // added by Katya 16.10.2016
//        String methodName = methodFullName.substring(0, methodFullName.indexOf("(")); // modified by Katya 22.11.2016 (till the "try" block)
//        String paramsString = methodFullName.substring(methodFullName.indexOf("(") + 1);
//        paramsString = paramsString.substring(0, paramsString.length() - 1);
        String pnetName = "";
//        FileInputStream fis = null;
//        try {
//            String libraryText = "";
//            Path path = FileSystems.getDefault().getPath(System.getProperty("user.dir"),"src","LibNet", "NetLibrary.java"); //added by Inna 29.09.2018
//            String pathNetLibrary = path.toString();
//            fis = new FileInputStream(pathNetLibrary); // modified by Katya 23.10.2016, by Inna 29.09.2018
//
//            int content;
//            while ((content = fis.read()) != -1) {
//		libraryText += (char) content;
//            }
//            String methodBeginning = "public static PetriNet " + methodName + "("; // modified by Katya 20.11.2016
//            String methodEnding = "return d_Net;";
//            String methodText = "";
//
//            Pattern pattern = Pattern.compile(Pattern.quote(methodBeginning) + Pattern.quote(paramsString) + Pattern.quote(")") + "([[^}]^\\r]*)" + Pattern.quote(methodEnding)); // modified by Katya 22.11.2016
//            Matcher matcher = pattern.matcher(libraryText);
//            if(matcher.find()){
//                 methodText = methodBeginning + paramsString + ")" + matcher.group(1) + methodEnding + "}"; // modified by Katya 22.11.2016
//            } else {
//                System.out.println("Method not found  FileNotFoundException");
//                throw new FileNotFoundException();
//            }
//            PetriNetsFrame petriNetsFrame = (PetriNetsFrame)frame;
//            JScrollPane pane = petriNetsFrame.GetPetriNetPanelScrollPane();
//            Point paneCenter = new Point(pane.getLocation().x+pane.getBounds().width/2, pane.getLocation().y+pane.getBounds().height/2);
//            // TODO
//            GraphPetriNet net = generateGraphNetBySimpleNet(panel ,convertMethodToPetriNet(methodText), paneCenter);
//            // System.out.println("num of p: "+net.getGraphPetriPlaceList().size());
//            panel.addGraphNet(net);
//            pnetName = net.getPetriNet().getName();
//            panel.repaint();
//        } catch (FileNotFoundException e) {
//            System.out.println("Method not found");
//
//        } catch (IOException ex) {
//            Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
//        } catch (ExceptionInvalidTimeDelay ex) {
//            Logger.getLogger(FileUse.class.getName()).log(Level.SEVERE, null, ex);
//        } finally {
//            try {
//                if (fis != null) {
//                    fis.close();
//                }
//            } catch (IOException ex) {
//                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
//            }
//        }
        return pnetName.substring(0, pnetName.length());
        // return netName;
    }
    
    /**
     * Process the code of a method (including header and { }) to replace
     * numeric arguments with string parameter names
     * @param code original method code
     * @return processed code
     */
    /*private String preProcessMethod(String code) {
        return code;
    }*/

    private static String generateArgumentsString(PetriNet net) { // added by Katya 08.12.2016
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
                str.append("double ").append(T.getParametrParamName()).append(", ");
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

    public static void saveNetAsMethod(GraphPetriNet pnet, JTextArea area) throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        PetriNet net;
        if (pnet.getPetriNet() == null) {
            pnet.createPetriNet("Untitled");
        }
        net = pnet.getPetriNet();
        area.setText("\n");
        area.append(
                "public static PetriNet CreateNet" + net.getName() + "(" + generateArgumentsString(net) + ") throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {\n" // modified by Katya 08.12.2016
                + "\t" + "ArrayList<PetriP> d_P = new ArrayList<>();\n"
                + "\t" + "ArrayList<PetriT> d_T = new ArrayList<>();\n"
                + "\t" + "ArrayList<ArcIn> d_In = new ArrayList<>();\n"
                + "\t" + "ArrayList<ArcOut> d_Out = new ArrayList<>();\n");

        for (PetriP P : net.getListP()) {
            String markStr = P.markIsParam() // added by Katya 08.12.2016
                ? P.getMarkParamName()
                : Integer.toString(P.getMark());
            area.append("\t" + "d_P.add(new PetriP(" + "\"" + P.getName() + "\"," + markStr + "));\n");
        }

        int j = 0;
        for (PetriT T : net.getListT()) {
            String parametrStr = T.parametrIsParam() // added by Katya 08.12.2016
                ? T.getParametrParamName()
                : Double.toString(T.getParametr());
            area.append("\t" + "d_T.add(new PetriT(" + "\"" + T.getName() + "\"," + parametrStr + "));\n");
            if (T.getDistribution() != null || T.distributionIsParam()) {
                String distributionStr = T.distributionIsParam() // added by Katya 08.12.2016
                    ? T.getDistributionParamName()
                    : T.getDistribution();
                area.append("\t" + "d_T.get(" + j + ").setDistribution(\"" + distributionStr + "\", d_T.get(" + j + ").getTimeServ());\n");
                area.append("\t" + "d_T.get(" + j + ").setParamDeviation(" + T.getParamDeviation() + ");\n");
            }
            if (T.getPriority() != 0 || T.priorityIsParam()) {
                String priorityStr = T.priorityIsParam() // added by Katya 08.12.2016
                    ? T.getPriorityParamName()
                    : Integer.toString(T.getPriority());
                area.append("\t" + "d_T.get(" + j + ").setPriority(" + priorityStr + ");\n");
            }
            if (T.getProbability() != 1.0 || T.probabilityIsParam()) {
                String probabilityStr = T.probabilityIsParam() // added by Katya 08.12.2016
                    ? T.getProbabilityParamName()
                    : Double.toString(T.getProbability());
                area.append("\t" + "d_T.get(" + j + ").setProbability(" + probabilityStr + ");\n");
            }
            j++;
        }

        j = 0;
        for (ArcIn In : net.getArcIn()) {
            String quantityStr = In.kIsParam() // added by Katya 08.12.2016
                ? In.getKParamName()
                : Integer.toString(In.getQuantity());
            area.append("\t" + "d_In.add(new ArcIn(" + "d_P.get(" + In.getNumP() + ")," + "d_T.get(" + In.getNumT() + ")," + quantityStr + "));\n");

            if (In.infIsParam()) { // modified by Katya 08.12.2016
                area.append("\t" + "d_In.get(" + j + ").setInf(" + In.getInfParamName() + ");\n");
            } else if (In.getIsInf()) {
                area.append("\t" + "d_In.get(" + j + ").setInf(true);\n");
            }
            j++;
        }

        for (ArcOut Out : net.getArcOut()) {
            String quantityStr = Out.kIsParam() // added by Katya 08.12.2016
                ? Out.getKParamName()
                : Integer.toString(Out.getQuantity());
            area.append("\t" + "d_Out.add(new ArcOut(" + "d_T.get(" + Out.getNumT() + ")," + "d_P.get(" + Out.getNumP() + ")," + quantityStr + "));\n");
        }

        area.append(
                "\t" + "PetriNet d_Net = new PetriNet(\"" + net.getName() + "\",d_P,d_T,d_In,d_Out);\n");

      //  area.append("\n\t" + "return d_Net;\n"); // modified by Katya 05.12.2016
         area.append(
                "\t" + "PetriP.initNext();\n"
                + "\t" + "PetriT.initNext();\n"
                + "\t" + "ArcIn.initNext();\n"
                + "\t" + "ArcOut.initNext();\n"
                + "\n\t" + "return d_Net;\n");


        area.append("}");
    }

    public static void saveMethodInNetLibrary(JTextArea area) {  //added by Inna 20.05.2013
        try {

            Path path = FileSystems.getDefault().getPath(
                    System.getProperty("user.dir"),"src","LibNet", "NetLibrary.java"); //added by Inna 29.09.2018
            String pathNetLibrary = path.toString(); //added by Inna 29.09.2018

            RandomAccessFile f = new RandomAccessFile(pathNetLibrary, "rw");
            // System.out.println("The path of Library of nets is\t"+path.toString());

            long n = f.length();
            if (n == 0) {
                f.writeBytes("package LibNet;\n"
                        +"import PetriObj.ExceptionInvalidNetStructure;\n"
                        + "import PetriObj.PetriNet;\n"
                        + "import PetriObj.PetriP;\n"
                        + "import PetriObj.PetriT;\n"
                        + "import PetriObj.ArcIn;\n"
                        + "import PetriObj.ArcOut;\n"
                        + "import java.util.ArrayList;\n"
                        + "public class NetLibrary {\n\n"
                        + "}");
                n = f.length();
            }

            n -= 1;
            f.seek(n);

            String c = f.readLine();
            while (c != null && !c.contains("}") && n > 0) {
                //   System.out.println("n= "+n+ ",   line= "+c);
                n -= 1;
                f.seek(n);
                c = f.readLine();
            }

            if (n > 0) {
                f.seek(n - 1);
                String s  = area.getText() + "\n" + c;
                f.write(s.getBytes());

                JOptionPane.showMessageDialog(area, "Method was successfully added. See in class NetLibrary.");
            } else {
                JOptionPane.showMessageDialog(area, "symbol '}' doesn't find in file NetLibrary.java");
            }
            f.close();
        } catch (IOException ex) {
            Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
}
