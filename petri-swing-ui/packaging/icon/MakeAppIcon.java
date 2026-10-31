// Draws the desktop app's icon at every size it ships in, into
// petri-swing-ui/src/main/resources/ua/stetsenkoinna/img/app-icon-<size>.png.
//
// Not part of the build: the PNGs are committed. Rerun it only to change the icon:
//
//   java petri-swing-ui/packaging/icon/MakeAppIcon.java petri-swing-ui/src/main/resources/ua/stetsenkoinna/img
//
// The drawing is in a 64x64 grid: a place with a token, an arc, a transition. Sizes of 24 and
// below get a bolder variant without the arrowhead, which would not survive at that scale.
import java.awt.*; import java.awt.geom.*; import java.awt.image.BufferedImage; import java.io.File; import javax.imageio.ImageIO;
public class MakeAppIcon {
    static final Color BLUE = new Color(0x18, 0x5F, 0xA5);
    public static void main(String[] a) throws Exception {
        for (int n : new int[] {16, 20, 24, 32, 40, 48, 64, 128, 256, 512}) {
            BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.scale(n / 64.0, n / 64.0);
            g.setColor(BLUE);
            g.fill(new RoundRectangle2D.Double(2, 2, 60, 60, 28, 28));
            g.setColor(Color.WHITE);
            boolean small = n <= 24;
            if (small) {
                g.setStroke(new BasicStroke(5.5f));
                g.draw(new Ellipse2D.Double(8, 23, 18, 18));
                g.fill(new Ellipse2D.Double(13.5, 28.5, 7, 7));
                g.setStroke(new BasicStroke(5f));
                g.draw(new Line2D.Double(26, 32, 39, 32));
                g.fill(new Rectangle2D.Double(39, 18, 8, 28));
                g.draw(new Line2D.Double(47, 32, 56, 32));
            } else {
                g.setStroke(new BasicStroke(3.5f));
                g.draw(new Ellipse2D.Double(8, 23, 18, 18));
                g.fill(new Ellipse2D.Double(14, 29, 6, 6));
                g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(26, 32, 37, 32));
                Path2D head = new Path2D.Double();
                head.moveTo(33, 28); head.lineTo(37, 32); head.lineTo(33, 36);
                g.draw(head);
                g.fill(new RoundRectangle2D.Double(39, 20, 5, 24, 2, 2));
                g.setStroke(new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(44, 32, 54, 32));
            }
            g.dispose();
            ImageIO.write(img, "png", new File(a[0], "app-icon-" + n + ".png"));
        }
    }
}
