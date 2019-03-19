import rest.*;
import io.AraIO;
import img.AraImgPlus;
import table.XYHeaders;
import table.AraResultsTable;
import table.ResultsTableConverter;
import gui.CoordinateColumnHeaderDialog;

import ij.measure.ResultsTable;

import org.scijava.command.Command;
import org.scijava.display.DisplayService;
import org.scijava.io.IOService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import javax.naming.ConfigurationException;
import javax.xml.transform.TransformerException;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

/**
 * @author Felix Meyenhofer
 */
@SuppressWarnings("unused")
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > 4. Mapping > | Batch > Sections Coords. to ARA")
public class MapSectionCoordinates2AraBatch extends AraIO implements Command {

    @Parameter
    private LogService log;

    @Parameter
    private DisplayService disp;

    @Parameter
    private IOService ioService;


    @Override
    public void run() {
        AllenClient client = AllenClient.getInstance();
        List<String> imgIds = getImageDisplays();

        if (imgIds.size() < 1) {
            File inputDir = ui.chooseFile(new File(System.getProperty("user.home")), "directory");
            imgIds = getMappedImagePaths(inputDir);
        }

        XYHeaders header = null;

        for (String imgId : imgIds) {
            log.info("Process section image: " + imgId);

            try {
                AraImgPlus img = getImage(imgId);
                AraResultsTable resTable;

                File outPath = deriveResultFile(new File(img.getSource()));
                String outStr = outPath.getAbsolutePath().substring(0, outPath.getAbsolutePath().length() - 3) + "mapped.txt";
                if (new File(outStr).exists()) {
                    log.warn("Mapped result file already exists: " + outStr);
                    continue;
                }

                // If there is only one image we look first for a open table (maybe it has just been produced
                // ... and we won't bother to first save it.
                File resPath = deriveResultFile(new File(imgId));
                if (resPath.exists()) {
                    ResultsTable ij1Table = ResultsTable.open2(resPath.getAbsolutePath());
                    if (ij1Table == null) {
                        log.error("Aborted. Could not read result file" + resPath);
                        return;
                    }
                    resTable = new AraResultsTable(ij1Table);
                    resTable.setName(resPath.getName());
                } else {
                    log.warn("... no results found");
                    continue;
                }

                // Prompt the user to select headers if necessary
                if (header == null || !resTable.hasCoordinateHeaders()) {
                    header = CoordinateColumnHeaderDialog.createAndShow(resTable.getHeaderNames());
                    if (header.getColumns().contains(null)) {
                        log.info("Column header selection aborted.");
                        return;
                    }
                }

                AraResultsTable mappedTable = resTable.mapSectionCoordinates(client, img, header);
                ResultsTable ijTable = ResultsTableConverter.convertIJ2toIJ1(mappedTable);
                ijTable.saveAs(outStr);
                log.info("               saved: " + imgId);

            } catch (TransformerException e) {
                log.error("XML parser error");
                e.printStackTrace();
            } catch (IOException e) {
                log.error("Cannot load annotation volume");
                e.printStackTrace();
            } catch (URISyntaxException e) {
                log.error("Cannot download annotation volume");
                e.printStackTrace();
            } catch (ConfigurationException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                log.error("ARA serialization error");
                e.printStackTrace();
            }
        }
    }
}
