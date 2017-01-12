package rest;

import javax.xml.transform.TransformerException;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Class to handle the image files from the Allen API
 *
 * @author Felix Meyenhofer
 */
class AllenImage extends AllenFile {

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
        InputStream is = url.openStream();
        OutputStream os = new FileOutputStream(getFile());
        byte[] b = new byte[2048];
        int len;
        while ((len = is.read(b)) != -1) {
            os.write(b, 0, len);
        }

        is.close();
        os.close();
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
