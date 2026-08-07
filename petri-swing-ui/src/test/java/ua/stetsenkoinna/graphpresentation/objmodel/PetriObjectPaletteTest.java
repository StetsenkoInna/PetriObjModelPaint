package ua.stetsenkoinna.graphpresentation.objmodel;

import org.junit.Test;

import ua.stetsenkoinna.libnet.NetTemplateCatalog;
import ua.stetsenkoinna.petriobj.PetriNet;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class PetriObjectPaletteTest {

    /**
     * The palette is a hand-picked shortlist of small reusable parts, not a mirror of the net
     * library — the library also holds whole worked example models, which belong in the Nets
     * window rather than on a stamping toolbar.
     */
    @Test
    public void offersOnlyTheHandPickedBuildingBlocks() {
        List<PetriObjectTemplate> builtIns = new PetriObjectPalette().builtIns();

        assertFalse("the palette should offer at least one building block", builtIns.isEmpty());
        assertTrue("the palette should be a shortlist, not the whole net library",
                builtIns.size() < NetTemplateCatalog.templates().size());
        assertFalse("whole example models do not belong on the stamping toolbar",
                builtIns.stream().anyMatch(t -> t.methodName().startsWith("CreateNetTest")
                        || t.methodName().equals("CreateNetFriend")
                        || t.methodName().equals("CreateNetThread3")));
    }

    /**
     * Everything offered is one of the two kinds and nothing else — a template that was
     * neither buildable from the library nor readable from a file would be a button that
     * could only ever fail.
     */
    @Test
    public void everyOfferedTemplateIsBuiltInOrSaved() {
        PetriObjectPalette palette = new PetriObjectPalette();

        assertEquals("available() should be exactly the built-ins plus the saved objects",
                palette.builtIns().size() + palette.custom().size(), palette.available().size());
        for (PetriObjectTemplate template : palette.custom()) {
            assertEquals(PetriObjectTemplate.Kind.PROTOTYPE, template.kind());
            assertNotNull("a saved Petri-object must know its file", template.prototypeFile());
        }
    }

    /**
     * The toolbar stamps without asking for arguments, so every template's defaults have to be
     * good enough to actually build its net — otherwise a pinned button would only fail once
     * the user clicked the canvas with it.
     */
    @Test
    public void everyTemplateCanBeBuiltFromItsDefaultArguments() throws Exception {
        for (PetriObjectTemplate template : new PetriObjectPalette().builtIns()) {
            PetriNet net = NetTemplateCatalog.instantiate(template.methodName(), template.arguments());
            assertNotNull(template.methodName() + " built no net from its default arguments", net);
        }
    }

    @Test
    public void namesTheGeneratorTemplateAfterWhatItBuilds() {
        PetriObjectTemplate generator = new PetriObjectPalette().builtIns().stream()
                .filter(template -> template.methodName().equals("CreateNetGenerator"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the net library has no CreateNetGenerator"));

        assertEquals("Generator", generator.displayName());
        assertEquals("G", generator.glyph());
    }

    /**
     * The toolbar shows nothing but the glyph, so two templates sharing one would be
     * indistinguishable there. Deriving glyphs from names collided, which is why they are
     * hand-picked — this keeps them that way as templates are added.
     */
    @Test
    public void givesEveryBuiltInItsOwnGlyph() {
        List<PetriObjectTemplate> builtIns = new PetriObjectPalette().builtIns();

        long distinct = builtIns.stream().map(PetriObjectTemplate::glyph).distinct().count();
        assertEquals("two built-in Petri-objects share a toolbar glyph", builtIns.size(), distinct);
    }

    @Test
    public void pinsTheGeneratorByDefault() {
        List<PetriObjectTemplate> pinned = new PetriObjectPalette().pinned();

        assertTrue("a fresh palette should start with the generator on the toolbar",
                pinned.stream().anyMatch(t -> t.methodName().equals("CreateNetGenerator")));
    }
}
