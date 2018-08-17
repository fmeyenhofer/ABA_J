import net.imagej.ImageJ;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * @author Felix Meyenhofer
 */
@SuppressWarnings("unused")
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > 4. IO > Load ARA.SEC")
public class LoadAraImgMenuEntry implements Command {

    @Parameter
    ImageJ ij;

    @Override
    public void run() {
        ij.command().run(LoadAraImg.class, true);
    }
}
