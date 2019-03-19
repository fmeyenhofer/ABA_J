import gui.SectionDatasetDownloadDialog;
import rest.AllenClient;

import net.imagej.ImageJ;
import org.scijava.Initializable;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.net.URISyntaxException;

/**
 * @author Felix Meyenhofer
 */
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > Misc. > Download SectionDataset")
public class DownloadSectionDataset extends DynamicCommand implements Initializable {

    @Parameter
    private LogService log;

    @Parameter
    private StatusService status;


    private AllenClient client;


    @Override
    public void initialize() {
        client = AllenClient.getInstance();
        client.setLogService(log);
        client.setStatusService(status);
    }

    @Override
    public void run() {
        log.info("Running Section Dataset Download plugin");
        try {
            SectionDatasetDownloadDialog dialog = SectionDatasetDownloadDialog.createAndShow();
            String dataset = dialog.getDatasetId();
            if (dataset == null) {
                log.info("Cancelled.");
                return;
            }
            int dsf = dialog.getSampling();
            int jpgq = dialog.getQuality();
            client.downloadSectionDataSet(dataset, dsf, jpgq);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TransformerException e) {
            log.error("Xml error.");
            log.error(e);
        } catch (URISyntaxException e) {
            log.error("Malformed uri");
            log.error(e);
        }

        log.info("Done.");
    }
}
