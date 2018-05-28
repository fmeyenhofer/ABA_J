package io;

import io.scif.AbstractFormat;
import io.scif.Format;
import org.scijava.plugin.Plugin;

/**
 * @author Felix Meyenhofer
 */
public class AraFileFormats {

    @Plugin(type = Format.class)
    public static class AraMapping extends AbstractFormat {

        @Override
        public String getFormatName() {
            return "Allen Reference Atlas Mapping";
        }

        @Override
        protected String[] makeSuffixArray() {
            return new String[]{"ara.xml"};
        }


    }

}
