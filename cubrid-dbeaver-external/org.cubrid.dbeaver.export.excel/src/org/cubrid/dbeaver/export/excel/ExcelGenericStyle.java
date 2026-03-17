package org.cubrid.dbeaver.export.excel;

import java.util.List;
import java.util.Set;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.WorkbookUtil;
import org.cubrid.dbeaver.export.excel.core.TableDefinitionFetcher;
import org.cubrid.dbeaver.export.excel.core.TableDefinitionFetcher.IndexColumn;
import org.cubrid.dbeaver.export.excel.core.TableDefinitionFetcher.IndexKey;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.cubrid.model.CubridDataSource;
import org.jkiss.dbeaver.ext.cubrid.model.CubridTable;
import org.jkiss.dbeaver.ext.cubrid.model.CubridTableColumn;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;

public class ExcelGenericStyle extends ExcelMainStyle {

    public ExcelGenericStyle(DBRProgressMonitor monitor, CubridDataSource dataSource, String filePath) {
    	super(monitor, dataSource, filePath);
    }

    @Override
    public void generateTableNamesSheet(List<CubridTable> tables) throws DBException {
        Sheet sheet = getWorkbook().createSheet("Tables");
        applySheetDimensions(sheet, 25, 28, 15, 18, 15, 20);

        // === Row 1 ===
        addCell(sheet, 0, 0, "Table List", getBoldStyle());
        mergeCell(sheet, 0, 0, 0, 5);

        // === Row 2 ===
        addCell(sheet, 1, 0, "Project", getBoldStyle());
        addCell(sheet, 1, 1, "", getCenterStyle());
        addCell(sheet, 1, 2, "Date", getBoldStyle());
        addCell(sheet, 1, 3, getDateString(), getCenterStyle());
        addCell(sheet, 1, 4, "Author", getBoldStyle());
        addCell(sheet, 1, 5, "", getCenterStyle());

        // === Row 3 ===
        addCell(sheet, 2, 0, "Table Name", getBoldStyle());
        addCell(sheet, 2, 1, "Table ID", getBoldStyle());
        addCell(sheet, 2, 2, "Description", getBoldStyle());
        addCell(sheet, 2, 5, "Memo", getBoldStyle());
        mergeCell(sheet, 2, 2, 2, 4);

        // === Row 4 to End ===
        int rowIndex = 3;
        for (CubridTable table : tables) {
            String tableName = table.getSchema() + "." + table.getName();
            addCell(sheet, rowIndex, 0, table.getDescription(), getLeftStyle());
            addCell(sheet, rowIndex, 1, tableName, getLeftStyle());
            addCell(sheet, rowIndex, 5, "", getCenterStyle());
            mergeCell(sheet, rowIndex, rowIndex, 2, 4);
            rowIndex++;
        }
    }

    @Override
    public void generateTableDetailSheets(CubridTable table) throws DBException {
    	String tableName = table.getSchema() + "." + table.getName();
    	String sheetName = tableName.length() > 31 ? tableName.substring(0, 31) : tableName;
        String safeName = WorkbookUtil.createSafeSheetName(sheetName);
    	Sheet tableSheet = getWorkbook().createSheet(safeName);
        applySheetDimensions(tableSheet, 18, 20, 13, 13, 11, 11, 11, 20);

    	// === Row 1 ===
        addCell(tableSheet, 0, 0, "Table Definitions", getBoldStyle());
        mergeCell(tableSheet, 0, 0, 0, 7);

    	// === Row 2 ===
        addCell(tableSheet, 1, 0, "System", getBoldStyle());
        addCell(tableSheet, 1, 1, "", getCenterStyle());
        addCell(tableSheet, 1, 2, "Date", getBoldStyle());
        addCell(tableSheet, 1, 3, getDateString(), getCenterStyle());
        addCell(tableSheet, 1, 5, "Author", getBoldStyle());
        addCell(tableSheet, 1, 7, "", getCenterStyle());
        mergeCell(tableSheet, 1, 1, 3, 4);
        mergeCell(tableSheet, 1, 1, 5, 6);

        // === Row 3 ===
        addCell(tableSheet, 2, 0, "Table Name", getBoldStyle());
        addCell(tableSheet, 2, 1, table.getDescription(), getLeftStyle());
        mergeCell(tableSheet, 2, 2, 1, 7);

        // === Row 4 ===
        addCell(tableSheet, 3, 0, "Table ID", getBoldStyle());
        addCell(tableSheet, 3, 1, tableName, getLeftStyle());
        mergeCell(tableSheet, 3, 3, 1, 7);

        // === Row 5 ===
        addCell(tableSheet, 4, 0, "Column Name", getBoldStyle());
        addCell(tableSheet, 4, 1, "Column ID", getBoldStyle());
        addCell(tableSheet, 4, 2, "Data Type", getBoldStyle());
        addCell(tableSheet, 4, 3, "Size", getBoldStyle());
        addCell(tableSheet, 4, 4, "NULL", getBoldStyle());
        addCell(tableSheet, 4, 5, "PK", getBoldStyle());
        addCell(tableSheet, 4, 6, "FK", getBoldStyle());
        addCell(tableSheet, 4, 7, "Memo", getBoldStyle());

        int rowIndex = 5;
        Set<String> pkColumnNames = TableDefinitionFetcher.getPrimaryKeyColumnNames(getMonitor(), table);
        for (CubridTableColumn column : TableDefinitionFetcher.getColumns(getMonitor(), table)) {
            String isNull = column.isRequired() ? "" : "Y";
            String isFK = column.isForeignKey() ? "Y" : "";
            String isPK = pkColumnNames.contains(column.getName()) ? "Y" : "";

            addCell(tableSheet, rowIndex, 0, column.getDescription(), getLeftStyle());
            addCell(tableSheet, rowIndex, 1, column.getName(), getLeftStyle());
            addCell(tableSheet, rowIndex, 2, column.getTypeName(), getLeftStyle());
            addCell(tableSheet, rowIndex, 3, column.getMaxLength(), getRightStyle());
            addCell(tableSheet, rowIndex, 4, isNull, getCenterStyle());
            addCell(tableSheet, rowIndex, 5, isPK, getCenterStyle());
            addCell(tableSheet, rowIndex, 6, isFK, getCenterStyle());
            addCell(tableSheet, rowIndex, 7, "", getCenterStyle());
            rowIndex++;
        }

        // Create one empty row with borders
        for (int i = 0; i <= 7; i++) {
            addCell(tableSheet, rowIndex, i, "", getCenterStyle());
        }
        rowIndex++;

        // Definition of Indexes
        addCell(tableSheet, rowIndex, 0, "Definition of indexes", getBoldStyle());
        mergeCell(tableSheet, rowIndex, rowIndex, 0, 7);
        rowIndex++;

        addCell(tableSheet, rowIndex, 0, "NO", getBoldStyle());
        addCell(tableSheet, rowIndex, 1, "Index Name", getBoldStyle());
        addCell(tableSheet, rowIndex, 3, "Column ID", getBoldStyle());
        addCell(tableSheet, rowIndex, 5, "Ordering", getBoldStyle());
        addCell(tableSheet, rowIndex, 6, "Memo", getBoldStyle());
        mergeCell(tableSheet, rowIndex, rowIndex, 1, 2);
        mergeCell(tableSheet, rowIndex, rowIndex, 3, 4);
        mergeCell(tableSheet, rowIndex, rowIndex, 6, 7);

        int indexNo = 1;
        for (IndexKey index : TableDefinitionFetcher.getIndexes(getMonitor(), table)) {
            int numColumns = index.getColumns().size();
            int startRow = rowIndex + 1;

            for (int i = 0; i < numColumns; i++) {
                IndexColumn indexColumn = index.getColumns().get(i);
                rowIndex++;
                if (i == 0) {
                    addCell(tableSheet, rowIndex, 0, indexNo++, getCenterStyle());
                } else {
                    addCell(tableSheet, rowIndex, 0, "", getCenterStyle());
                }
                addCell(tableSheet, rowIndex, 1, (i == 0 ? index.getIndexName() : ""), getLeftStyle());
                addCell(tableSheet, rowIndex, 3, indexColumn.getColumnName(), getLeftStyle());
                addCell(tableSheet, rowIndex, 5, indexColumn.getKeyPosition(), getCenterStyle());
                addCell(tableSheet, rowIndex, 6, "", getCenterStyle());
                mergeCell(tableSheet, rowIndex, rowIndex, 3, 4);
                mergeCell(tableSheet, rowIndex, rowIndex, 6, 7);
            }
            int endRow = rowIndex;
            if (numColumns > 1) {
                mergeCell(tableSheet, startRow, endRow, 0, 0);
            }
            mergeCell(tableSheet, startRow, endRow, 1, 2);
        }
        rowIndex++;

        // Create one empty row with borders
        for (int i = 0; i <= 7; i++) {
            addCell(tableSheet, rowIndex, i, "", getCenterStyle());
        }
        mergeCell(tableSheet, rowIndex, rowIndex, 1, 2);
        mergeCell(tableSheet, rowIndex, rowIndex, 3, 4);
        mergeCell(tableSheet, rowIndex, rowIndex, 6, 7);
        rowIndex++;

        addCell(tableSheet, rowIndex, 0, "DDL", getBoldStyle());
        mergeCell(tableSheet, rowIndex, rowIndex, 0, 7);
        rowIndex++;

        String ddl = TableDefinitionFetcher.getDDL(getMonitor(), table);
        addCell(tableSheet, rowIndex, 0, ddl, getLeftStyle());
        mergeCell(tableSheet, rowIndex, rowIndex, 0, 7);

        // === Column widths ===
        int numLines = ddl.split("\n").length;
        tableSheet.getRow(rowIndex).setHeightInPoints((numLines + 1) * tableSheet.getDefaultRowHeightInPoints());
    }
}
