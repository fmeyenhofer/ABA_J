import net.imagej.ImageJ;
import java.io.IOException;


public class RunPlugin {

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

//        String path = "/path/to/image.tif";
//        Object img = ij.io().open(path);
//        ij.ui().show(img);

        ij.command().run(InteractiveAlignment.class, true);
    }
}