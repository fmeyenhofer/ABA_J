import ij.ImagePlus;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import org.scijava.command.Command;
import org.scijava.display.Display;
import org.scijava.display.DisplayService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;


@Plugin(type = Command.class, menuPath = "Plugins > Display Service Test")
public class DisplayPlugin implements Command {

    @Parameter
    ImgPlus imgp;

    @Parameter
    DisplayService display;

    @Override
    public void run() {
        Display disp = display.getDisplay(imgp.getName());
        disp.close();

        Frame frame = grabFrame(imgp.getName());
        frame.dispose();
    }

    private Frame grabFrame(String title) {
        for (Frame frame : JFrame.getFrames()) {
            if (frame.getTitle().equals(title)) {
                return frame;
            }
        }
        return null;
    }


    public static void main(String[] args) throws IOException {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        ImagePlus imp = new ImagePlus("http://imagej.nih.gov/ij/images/blobs.gif");
        Img img = ImageJFunctions.wrap(imp);
        ImgPlus imgp = new ImgPlus(img, "blobs");
//        ij.ui().show(imgp);
        ij.display().createDisplay(imgp);
        
        ij.command().run(DisplayPlugin.class, true);
    }
}

