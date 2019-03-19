import img.AraImgPlus;
import io.AraIO;
import io.AraMapping;

import net.imagej.Dataset;
import net.imagej.ImageJ;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author Felix Meyenhofer
 */
@Plugin(type = Command.class, menuPath = "File > Import > ARA.SEC...")
public class LoadAraImg extends AraIO implements Command {

    @Parameter
    File file;

    @Parameter(type = ItemIO.OUTPUT)
    AraImgPlus araImg;


    @Parameter
    LogService log;


    @Override
    public void run() {
        File imgFile;
        File mapFile;
        try {
            if (file.getAbsolutePath().endsWith(MAPPING_FILE_FORMAT)) {
                mapFile = file;
                imgFile = getImageFile(mapFile);
            } else {
                imgFile = file;
                mapFile = deriveMappingFile(imgFile);

                if (!mapFile.exists()) {
                    throw new FileNotFoundException(mapFile.getName() + " does not exist in " + imgFile.getParent());
                }
            }

            log.info("Read section image file: " + imgFile.getName());
            Dataset dataset = dsio.open(imgFile.getAbsolutePath());
            log.info("Read mapping metadata:   " + mapFile.getName());
            AraMapping mapping = AraMapping.load(mapFile);

            araImg = new AraImgPlus(dataset.getImgPlus().getImg(), mapping);
            araImg.setSource(imgFile.getAbsolutePath());
            araImg.setName(imgFile.getName());
        } catch (IOException e) {
            log.error("Could not read ARA.SEC.");
            log.error(e);
        } catch (ClassNotFoundException e) {
            log.error("Parsing error with" + file.getName() + " or its match");
            log.error(e);
        }
    }
}
