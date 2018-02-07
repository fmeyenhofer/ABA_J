import ij.ImagePlus;
import ij.gui.PointRoi;
import img.AraImgPlus;
import net.imagej.ImgPlus;

import net.imglib2.img.display.imagej.ImageJFunctions;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;


/**
 * TODO: try to attach the point selection to the current image (do not open a new one)
 *
 * @author Felix Meyenhofer
 */
@SuppressWarnings("unused")
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > 3. Analysis > Gene Expression")
public class GeneExpressionAtlas implements Command {

    @Parameter(label = "section image")
    private ImgPlus section;


    @Parameter
    private UIService ui;

    @Parameter
    private LogService log;


    // http://mouse.brain-map.org/agea?seed=P56,6700,4100,5600&map1=P56,6700,4100,5600,[0.7,1]
    private static String baseUrl = "http://mouse.brain-map.org/agea?seed=P56";
    private static String argUrl = "&map1=P56";
    private static String intensityInterval = ",[0.7,1]";

    private ImagePlus imp;
    private AraImgPlus araSection;


    @Override
    public void run() {

        if (section instanceof AraImgPlus) {
            araSection = (AraImgPlus) section;

//            ApplicationFrame appFrame = ui.getUI(section.getName()).getApplicationFrame();
//            JDesktopPane jdp = new JDesktopPane();
//            JInternalFrame frame = jdp.getSelectedFrame();
//            JPanel panel = (JPanel) frame.getContentPane().getComponent(0);
//            panel.addMouseListener(

            imp = ImageJFunctions.wrap(section.getImg(), "Point selection");
            imp.setTitle("Select a point");
            imp.show();
            imp.getWindow().getCanvas().addMouseListener(new MouseListener() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    Point position = imp.getCanvas().getMousePosition();
                    openBrowser(new double[]{position.getX(), position.getY()});
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    // nothing
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    // nothing
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    // nothing
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    // nothing
                }
            });

        } else {
            ui.showDialog("<html><p>The image appears not to be aligned to the template.</p>" +
                    "<p>You need to run Plugins > Allen Brain Atlas > 2. Alignment > ...</p>");
        }
    }

    private String coordinate2string(double[] c) {
        StringBuilder buffer = new StringBuilder();
        for (double value : c) {
            buffer.append(",").append(Math.round(value));
        }

        return buffer.toString();
    }

    private void openBrowser(double[] coord){
        PointRoi point = new PointRoi(coord[0], coord[1]);
        imp.setRoi(point);
        imp.show();

        double[] templateCoord = araSection.getTemplateCoordinate(coord);

        try {
            String coordString = coordinate2string(templateCoord);
            String uris = baseUrl + coordString + argUrl + coordString +  intensityInterval;
            System.out.println(uris);
            URL url = new URL(uris);

            Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
            if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(url.toURI());
            } else {
                ui.showDialog("Unable to open '" + url.toURI().toString() + "' in browser");
            }
        } catch (URISyntaxException e) {
            log.error("URI exception");
            e.printStackTrace();
        } catch (IOException e) {
            log.error("IO exception");
            e.printStackTrace();
        }
    }
}
