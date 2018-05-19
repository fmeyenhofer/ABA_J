import ij.ImagePlus;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import java.io.IOException;


@Plugin(type = Command.class, menuPath = "Plugins > ExtendedImgPlus")
public class ExtendedImgPlusPlugin implements Command {

    @Parameter
    ImgPlus imgp;


    @Override
    public void run() {
        if (imgp instanceof ExtendedImgPlus) {
            ExtendedImgPlus eimgp = (ExtendedImgPlus) imgp;
            eimgp.additionalMethod();
        } else {
            System.out.println("this is " + imgp.getClass().toString());
        }
    }


    public static void main(String[] args) throws IOException {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        ImagePlus imp = new ImagePlus("http://imagej.nih.gov/ij/images/blobs.gif");
        Img img = ImageJFunctions.wrap(imp);

        ExtendedImgPlus eimgp2 = new ExtendedImgPlus(img);
        ij.ui().show("Img -> ExtendedImgPlus", eimgp2);

        ExtendedImgPlus eimgp1 = new ExtendedImgPlus(new ImgPlus(img));
        ij.ui().show("ImgPlus -> ExtendedImgPlus", eimgp1);
        
        ij.command().run(ExtendedImgPlusPlugin.class, true);
    }
}

