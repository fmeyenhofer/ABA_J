package rest;

import org.scijava.app.StatusService;
import org.scijava.log.LogService;

import javax.xml.transform.TransformerException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Class to handle the image files from the Allen API
 *
 * @author Felix Meyenhofer
 */
class AllenImage extends AllenFile {

    AllenImage(URL url, File file, LogService logService, StatusService statusService)
            throws TransformerException, IOException, URISyntaxException {
        super(url, file, logService, statusService);
    }

    /**
     * {@inheritDoc}
     */
    AllenImage(URL url, File file) throws IOException, TransformerException, URISyntaxException {
        super(url, file);
    }

    /**
     * {@inheritDoc}
     */
    AllenImage(File file) throws IOException, URISyntaxException {
        super(file);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void load(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.connect();

//        System.out.println("content length: " + host.getContentLength());

        InputStream is = connection.getInputStream();
        OutputStream os = new FileOutputStream(getFile());
//        ByteArrayOutputStream os = new ByteArrayOutputStream();

        boolean doStatusUpdates = (getStatusService() != null);

        double sta = (double)System.currentTimeMillis();
        long niter = 100;
        double chunkSize = (4096.0 * (double)niter) / 1000000.0;
        int cnt = 0;
        int pos = 1;
        int maxPos = 100;

        byte[] b = new byte[4096];
        int len;
        while ((len = is.read(b)) != -1) {
            os.write(b, 0, len);

            if (doStatusUpdates && (cnt++ % niter) == 0) {
                double td = ((double)System.currentTimeMillis() - sta) / 1000.0;
                double speed = chunkSize / td;
                String message = "Downloading from brain.map.org (" + String.format("%.1f", speed) + " MB/s)";
                statusUpdate(pos, maxPos, message);
                sta = System.currentTimeMillis();
                pos = (pos == maxPos) ? 1 : pos + 1;
            }
        }
        is.close();
        os.close();
        connection.disconnect();

//        byte[] bytes = os.toByteArray();
//        FileOutputStream fos = new FileOutputStream(getFile().getAbsoluteFile());
//        fos.write(bytes);
//        fos.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void load(File file) throws IOException, URISyntaxException {
        // not used
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void save() {
        // not used
    }
}
