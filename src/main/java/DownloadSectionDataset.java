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

//    @Parameter(label = "Species", callback = "updateProducts")
//    private String species = "mouse";
//
//    @Parameter(label = "Product", callback = "updateDatasets")
//    private String product;
//
//    @Parameter(label = "Dataset")
//    private String dataset;
//
//    @Parameter(label = "Down-sampling", min = "0", max = "10" )
//    private int dsf = 4;
//
//    @Parameter(label = "Quality", min = "0", max = "100")
//    private int jpgq = 100;


    @Parameter
    private LogService log;

    @Parameter
    private StatusService status;


    private AllenClient client;


    @Override
    public void initialize() {
        client = AllenClient.getInstance();
        client.setLoggerService(log);
        client.setStatusService(status);

//        final MutableModuleItem<String> productItem = getInfo().getMutableInput("species", String.class);
//        productItem.setChoices(Atlas.Species.getNames());
//
//        updateProducts();
//        updateDatasets();
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
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        log.info("Done.");
    }

//    private void updateProducts() {
//        try {
//            List<String> products = client.getProductList(species);
//            final MutableModuleItem<String> productItem = getInfo().getMutableInput("product", String.class);
//            productItem.setChoices(products);
//            product = products.get(0);
//        } catch (IOException e) {
//            e.printStackTrace();
//        } catch (TransformerException e) {
//            e.printStackTrace();
//        } catch (URISyntaxException e) {
//            e.printStackTrace();
//        }
//    }
//
//    private void updateDatasets() {
//        if (product == null) {
//            return;
//        }
//        try {
//            Pattern pattern = Pattern.compile("(.*)(\\d+)\\)$");
//            Matcher matcher = pattern.matcher(product);
//            if (matcher.matches()) {
//                String id = matcher.group(2);
//                List<String> datasets = client.getDatasetList(id);
//                final MutableModuleItem<String> datasetItem = getInfo().getMutableInput("dataset", String.class);
//                datasetItem.setChoices(datasets);
//
//                Frame frame = SwingUtils.grabFrame("Download SectionDataset");
//                if (frame != null) {
//                    frame.repaint();
//                }
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        } catch (TransformerException e) {
//            e.printStackTrace();
//        } catch (URISyntaxException e) {
//            e.printStackTrace();
//        }
//    }

    public static void main(String[] args) {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();
        ij.command().run(DownloadSectionDataset.class, true);
    }
}
