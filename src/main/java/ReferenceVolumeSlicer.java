import gui.VolumeSlicerDialog;
import ij.ImagePlus;
import ij.WindowManager;
import ij.io.OpenDialog;
import io.scif.services.DatasetIOService;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.legacy.LegacyService;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.io.IOException;

/**
 * @author Felix Meyenhofer
 */
@SuppressWarnings("WeakerAccess")
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > Misc. > Reference Volume Slicer")
public class ReferenceVolumeSlicer implements Command {

    @Parameter
    DatasetIOService ioService;

    @Parameter
    LegacyService legacyService;


    @Override
    public void run() {
        /* Reference to the active image(-display) */
        ImagePlus imp = WindowManager.getCurrentImage();

        if (imp == null) {
            OpenDialog dialog = new OpenDialog("Choose an image file (monochrome volume (xyz)");
            try {
                Dataset dataset = ioService.open(dialog.getPath());
                imp = legacyService.getImageMap().registerDataset(dataset);
                imp.show();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        VolumeSlicerDialog.createAndShow(imp);
    }


    public static void main(final String... args) throws Exception {
        // Get the ImageJ instance
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        // Open an image
//        File file = new File ("/Users/turf/switchdrive/SJMCS_Thesis/data/aba/mouse-ccf-3/reference-volumes/average_template_50.nrrd");
        File file = new File("/Users/turf/switchdrive/SJMCS_Thesis/data/aba/mouse-ccf-3/reference-volumes/average_template_50.tif");
        Dataset dataset = ij.scifio().datasetIO().open(file.getAbsolutePath());
        ij.display().createDisplay(dataset);

        // Run the plugin
        ij.command().run(ReferenceVolumeSlicer.class, true);
    }
}
