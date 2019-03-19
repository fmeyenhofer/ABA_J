import gui.VolumeSlicerDialog;

import ij.ImagePlus;
import ij.WindowManager;
import ij.io.OpenDialog;

import net.imagej.Dataset;
import net.imagej.legacy.LegacyService;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import io.scif.services.DatasetIOService;

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
}
