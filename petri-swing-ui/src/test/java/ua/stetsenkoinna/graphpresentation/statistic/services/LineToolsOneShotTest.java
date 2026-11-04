package ua.stetsenkoinna.graphpresentation.statistic.services;

import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.Line;
import org.junit.Test;
import ua.stetsenkoinna.api.dto.ChartConfigDto;
import ua.stetsenkoinna.graphpresentation.statistic.dto.drawings.ChartDrawingConfig;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The vertical and horizontal line tools of the Statistic monitor place one line per pick.
 *
 * <p>They used to stay armed: after the first click drew its line, every later click on the chart
 * drew another, so a user who wanted one line kept getting a second, a third and so on until they
 * noticed the pressed toolbar button and released it by hand.
 */
public class LineToolsOneShotTest {

    private static ChartConfigDto config() {
        return new ChartConfigDto("t", "x", "y", true);
    }

    @Test
    public void releasingTurnsBothLineToolsOffAndReportsThatSomethingChanged() {
        ChartConfigDto dto = config();
        dto.setDrawVerticalLineEnabled(true);
        dto.setDrawHorizontalLineEnabled(true);

        assertTrue(dto.releaseLineTools());

        assertFalse(dto.getDrawVerticalLineEnabled());
        assertFalse(dto.getDrawHorizontalLineEnabled());
    }

    @Test
    public void releasingWhenOnlyOneToolIsOnStillReportsAChange() {
        ChartConfigDto dto = config();
        dto.setDrawHorizontalLineEnabled(true);
        assertTrue(dto.releaseLineTools());
        assertFalse(dto.getDrawHorizontalLineEnabled());
    }

    @Test
    public void releasingWithNothingArmedReportsNoChange() {
        assertFalse(config().releaseLineTools());
    }

    @Test
    public void releasingLeavesTheOtherToolsAlone() {
        ChartConfigDto dto = config();
        dto.setDrawVerticalLineEnabled(true);
        dto.setDragEnabled(true);
        dto.setCreateLabelEnabled(true);
        dto.setClearLineEnabled(true);

        dto.releaseLineTools();

        assertTrue(dto.getDragEnabled());
        assertTrue(dto.getCreateLabelEnabled());
        assertTrue(dto.getClearLineEnabled());
    }

    /** A service with just enough wired to take a click, without starting the JavaFX toolkit. */
    private static class Rig {
        final LineChartBuilderService service = new LineChartBuilderService();
        final AnchorPane root = new AnchorPane();
        final ChartConfigDto dto = config();
        final Line verticalGuide = new Line();
        final Line horizontalGuide = new Line();
        final AtomicInteger released = new AtomicInteger();

        Rig() throws Exception {
            set(service, "rootPane", root);
            set(service, "chartConfigDto", dto);
            ChartDrawingConfig drawing = (ChartDrawingConfig) get(service, "chartDrawing");
            drawing.setVerticalTooltipLine(verticalGuide);
            drawing.setHorizontalTooltipLine(horizontalGuide);
            verticalGuide.setVisible(true);
            horizontalGuide.setVisible(true);
            service.setOnLineToolsReleased(released::incrementAndGet);
        }

        ChartDrawingConfig drawing() throws Exception {
            return (ChartDrawingConfig) get(service, "chartDrawing");
        }

        private static void set(Object target, String name, Object value) throws Exception {
            Field field = LineChartBuilderService.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        }

        private static Object get(Object target, String name) throws Exception {
            Field field = LineChartBuilderService.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        }
    }

    @Test
    public void aVerticalClickPlacesOneLineAndLetsTheToolGo() throws Exception {
        Rig rig = new Rig();
        rig.dto.setDrawVerticalLineEnabled(true);

        rig.service.placeLinesAt(120, 3);

        assertEquals(1, rig.drawing().getVerticalLines().size());
        assertEquals(0, rig.drawing().getHorizontalLines().size());
        assertEquals(120, rig.drawing().getVerticalLines().get(0).getX(), 0.0);
        assertFalse("the tool must not stay armed", rig.dto.getDrawVerticalLineEnabled());
        assertEquals("the toolbar is told once", 1, rig.released.get());
    }

    @Test
    public void aHorizontalClickPlacesOneLineAndLetsTheToolGo() throws Exception {
        Rig rig = new Rig();
        rig.dto.setDrawHorizontalLineEnabled(true);

        rig.service.placeLinesAt(5, 42);

        assertEquals(0, rig.drawing().getVerticalLines().size());
        assertEquals(1, rig.drawing().getHorizontalLines().size());
        assertEquals(42, rig.drawing().getHorizontalLines().get(0).getY(), 0.0);
        assertFalse(rig.dto.getDrawHorizontalLineEnabled());
        assertEquals(1, rig.released.get());
    }

    @Test
    public void bothToolsArmedPlaceOneOfEachAndBothLetGo() throws Exception {
        Rig rig = new Rig();
        rig.dto.setDrawVerticalLineEnabled(true);
        rig.dto.setDrawHorizontalLineEnabled(true);

        rig.service.placeLinesAt(7, 9);

        assertEquals(1, rig.drawing().getVerticalLines().size());
        assertEquals(1, rig.drawing().getHorizontalLines().size());
        assertFalse(rig.dto.getDrawVerticalLineEnabled());
        assertFalse(rig.dto.getDrawHorizontalLineEnabled());
    }

    @Test
    public void theGuideFollowingTheMouseGoesWithTheTool() throws Exception {
        Rig rig = new Rig();
        rig.dto.setDrawVerticalLineEnabled(true);

        rig.service.placeLinesAt(1, 1);

        assertFalse(rig.verticalGuide.isVisible());
        assertFalse(rig.horizontalGuide.isVisible());
    }

    @Test
    public void armingTheToolAgainPlacesAnotherLine() throws Exception {
        Rig rig = new Rig();
        rig.dto.setDrawVerticalLineEnabled(true);
        rig.service.placeLinesAt(10, 0);

        rig.dto.setDrawVerticalLineEnabled(true);
        rig.service.placeLinesAt(20, 0);

        assertEquals(2, rig.drawing().getVerticalLines().size());
        assertEquals(2, rig.released.get());
    }
}
