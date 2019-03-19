package table;

import ij.measure.ResultsTable;
import org.scijava.table.*;

/**
 * Author: Robert Haase, Scientific Computing Facility, MPI-CBG Dresden, rhaase@mpi-cbg.de
 * Date: July 2016
 * <p>
 * Copyright 2017 Max Planck Institute of Molecular Cell Biology and Genetics,
 * Dresden, Germany
 * <p>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
public class ResultsTableConverter {
    public static ResultsTable convertIJ2toIJ1(GenericTable tableIn) {
        ResultsTable tableOut = new ResultsTable();

        for (int c = 0; c < tableIn.getColumnCount(); c++) {
            String header = tableIn.getColumnHeader(c);
            for (int r = 0; r < tableIn.getRowCount(); r++) {
                Object value = tableIn.get(c, r);
                try {
                    tableOut.setValue(header, r, (double) value);
                } catch (Exception e) {
                    try {
                        tableOut.setValue(header, r, (int) value);
                    } catch (Exception e1) {
                        tableOut.setValue(header, r, (String) value);
                    }
                }

            }
        }

        return tableOut;
    }

    public static DefaultGenericTable convertIJ1toIJ2(ResultsTable tableIn) {
        DefaultGenericTable table = new DefaultGenericTable();

        for (String header : tableIn.getHeadings()) {
            int columnIndex = tableIn.getColumnIndex(header);

            // copy column wise
            Column column;
            if (columnIndex == -1) {
                column = new GenericColumn(header);
                for (int rowIndex = 0; rowIndex < tableIn.getCounter(); rowIndex++) {
                    column.add(tableIn.getLabel(rowIndex));
                }
            } else if (!Double.isNaN(tableIn.getValueAsDouble(columnIndex, 0))) {
                column = new DoubleColumn(header);
                for (int rowIndex = 0; rowIndex < tableIn.getCounter(); rowIndex++) {
                    double value = tableIn.getValueAsDouble(columnIndex, rowIndex);
                    column.add(value);
                }
            } else {
                column = new GenericColumn(header);
                for (int rowIndex = 0; rowIndex < tableIn.getCounter(); rowIndex++) {
                    String value = tableIn.getStringValue(columnIndex, rowIndex);
                    column.add(value);
                }
            }

            table.add(column);
        }
        return table;
    }
}
