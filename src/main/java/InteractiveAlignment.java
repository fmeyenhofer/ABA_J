import net.imagej.ImgPlus;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * @author Felix Meyenhofer
 */
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > Mapping > Interactive")
public class InteractiveAlignment implements Command {

    @Parameter(label = "Section Image")
    ImgPlus imgSection;

    @Parameter(label = "Reference Atlas resolution", choices = {"100um", "50um", "25um", "10um"})
    String araResolution;

    @Parameter(label = "Reference Modality", choices = {"Auto-Fluorescence", "Nissl"})
    String araModality;

    @Override
    public void run() {

        // Try to get the pixel size from the image

        // Load the reference volume

        // if no pixel size estimate from the section area

        // determine initial transform for the section image

        // initialize the UI and open it

        // TODO
    }
}
