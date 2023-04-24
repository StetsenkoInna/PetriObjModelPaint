package graphpresentation;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Leonid
 */
public class FileUseTest {
    
    static FileUse fileUse;
    
    static final String fileHeader = "package LibNet;\n"
                        +"import PetriObj.ExceptionInvalidNetStructure;\n"
                        + "import PetriObj.PetriNet;\n"
                        + "import PetriObj.PetriP;\n"
                        + "import PetriObj.PetriT;\n"
                        + "import PetriObj.ArcIn;\n"
                        + "import PetriObj.ArcOut;\n"
                        + "import java.util.ArrayList;\n"
                        + "public class NetLibrary {\n\n";
    
    @BeforeClass
    public static void setUpClass() {
        fileUse = new FileUse();
    }
    
    @Test
    public void testRemoveArgsFromHeader() {
        String code = fileHeader 
                + "public static PetriNet CreateNetUntitled(double k, String h, int u, double p) {}" 
                + "\n}";
        
        String expectedCode = fileHeader 
                + "public static PetriNet CreateNetUntitled() {}" 
                + "\n}";
        
        assertEquals(expectedCode, fileUse.preProcessNetLibraryCode(code));
        
        String code2 = "public static PetriNet CreateNetSimpleWithPARAM(int n) throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay { }";
        String expectedCode2 = "public static PetriNet CreateNetSimpleWithPARAM() throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay { }";
        
        assertEquals(expectedCode2, fileUse.preProcessNetLibraryCode(code2));
        
        // 2 methods in one file
        code = code + code2;
        expectedCode = expectedCode + expectedCode2;
        
        assertEquals(expectedCode, fileUse.preProcessNetLibraryCode(code));
    }

    @Test
    public void testProcessParametrizedPlace() {
        String code = fileHeader 
                + "public static PetriNet CreateNetUntitled (double k, String h, int u, double p) {\n" 
                + "d_P.add(new PetriP(\"placename\",paramstring));\n"
                + "}\n}"; 
        
        String expectedCode = 
                fileHeader 
                + "public static PetriNet CreateNetUntitled() {\n" 
                + "PetriP placename = new PetriP(\"placename\", 0);\n" 
                + "placename.setMarkParam(\"paramstring\");\n" 
                + "d_P.add(placename);\n"
                + "}\n}"; 
        
        assertEquals(expectedCode, fileUse.preProcessNetLibraryCode(code));
    }
    
    @Test
    public void testParametrizedTransitionDelay() {
        String code = fileHeader
                + "public static PetriNet CreateNetUntitled(double k) {\n"
                + "d_T.add(new PetriT(\"T1\",k));\n"
                + "}\n}"; 
        
        String expectedCode = fileHeader
                + "public static PetriNet CreateNetUntitled() {\n"
                + "PetriT T1 = new PetriT(\"T1\",0);\n" 
                + "T1.setParametrParam(\"k\");\n" 
                + "d_T.add(T1);\n"
                + "}\n}"; 
        
        assertEquals(expectedCode, fileUse.preProcessNetLibraryCode(code));
    }
    
}
