package ua.stetsenkoinna.libnet;

import org.junit.Test;
import ua.stetsenkoinna.petriobj.PetriNet;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Instantiating net library templates with arguments, which is how a Petri-object model
 * gets several objects that differ only in their parameters.
 */
public class NetTemplateCatalogTest {

    @Test
    public void catalogueListsTheLibraryTemplates() {
        List<NetTemplateCatalog.Template> templates = NetTemplateCatalog.templates();

        assertTrue("the library ships templates", templates.size() > 3);
        NetTemplateCatalog.Template smo = NetTemplateCatalog.find("CreateNetSMOwithoutQueue");
        assertNotNull(smo);
        assertEquals(3, smo.parameters().size());
        assertTrue(smo.signature().startsWith("CreateNetSMOwithoutQueue("));
    }

    @Test
    public void argumentsAreConvertedToTheParameterTypes() throws Exception {
        PetriNet net = NetTemplateCatalog.instantiate(
                "CreateNetSMOwithoutQueue", List.of("3", "0.5", "First"));

        assertEquals("SMOwithoutQueueFirst", net.getName());
        assertEquals(3, net.getListP().length);
        assertEquals("the channel count must reach the net", 3, net.getListP()[1].getMark());
    }

    @Test
    public void twoInstancesOfOneTemplateAreIndependentNets() throws Exception {
        PetriNet first = NetTemplateCatalog.instantiate(
                "CreateNetSMOwithoutQueue", List.of("1", "0.5", "First"));
        PetriNet second = NetTemplateCatalog.instantiate(
                "CreateNetSMOwithoutQueue", List.of("2", "0.5", "Second"));

        assertEquals(1, first.getListP()[1].getMark());
        assertEquals(2, second.getListP()[1].getMark());
        assertEquals("each instance numbers its own places from zero",
                0, second.getListP()[0].getNumber());
    }

    @Test
    public void aMalformedArgumentIsReportedWithTheParameterItBelongsTo() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> NetTemplateCatalog.instantiate(
                        "CreateNetSMOwithoutQueue", List.of("many", "0.5", "First")));

        assertTrue(failure.getMessage(), failure.getMessage().contains("int"));
    }

    @Test
    public void anUnknownTemplateIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> NetTemplateCatalog.instantiate("NoSuchNet", List.of()));
    }
}
