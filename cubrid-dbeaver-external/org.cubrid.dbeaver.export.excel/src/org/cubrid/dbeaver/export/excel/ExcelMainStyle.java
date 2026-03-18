package org.cubrid.dbeaver.export.excel;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.RegionUtil;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.cubrid.dbeaver.export.excel.core.TableDefinitionFetcher;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.cubrid.model.CubridDataSource;
import org.jkiss.dbeaver.ext.cubrid.model.CubridTable;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;

public abstract class ExcelMainStyle {
    private final Workbook workbook;
    private final CellStyle centerStyle;
    private final CellStyle leftStyle;
    private final CellStyle rightStyle;
    private final CellStyle boldStyle;
    private final CubridDataSource dataSource;
    private final DBRProgressMonitor monitor;
    private final String dateString;
    private final String filePath;

    public ExcelMainStyle(DBRProgressMonitor monitor, CubridDataSource dataSource, String filePath) {
        this.monitor = monitor;
        this.dataSource = dataSource;
        this.filePath = filePath;
        this.workbook = new XSSFWorkbook();

        // Font
        Font normalFont = workbook.createFont();
        normalFont.setFontName("Arial");
        normalFont.setFontHeightInPoints((short) 10);
        normalFont.setBold(false);

        // Center style
        centerStyle = workbook.createCellStyle();
        centerStyle.setWrapText(true);
        centerStyle.setFont(normalFont);
        centerStyle.setBorderTop(BorderStyle.THIN);
        centerStyle.setBorderBottom(BorderStyle.THIN);
        centerStyle.setBorderLeft(BorderStyle.THIN);
        centerStyle.setBorderRight(BorderStyle.THIN);
        centerStyle.setAlignment(HorizontalAlignment.CENTER);
        centerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        // Left-aligned style
        leftStyle = workbook.createCellStyle();
        leftStyle.cloneStyleFrom(centerStyle);
        leftStyle.setAlignment(HorizontalAlignment.LEFT);

        // Right-aligned style
        rightStyle = workbook.createCellStyle();
        rightStyle.cloneStyleFrom(centerStyle);
        rightStyle.setAlignment(HorizontalAlignment.RIGHT);

        // Bold style
        boldStyle = workbook.createCellStyle();
        boldStyle.cloneStyleFrom(centerStyle);
        Font boldFont = workbook.createFont();
        boldFont.setFontName("Arial");
        boldFont.setFontHeightInPoints((short) 10);
        boldFont.setBold(true);
        boldStyle.setFont(boldFont);
        boldStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        boldStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        dateString = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
    }

    protected abstract void generateTableNamesSheet(List<CubridTable> tables) throws DBException;
    protected abstract void generateTableDetailSheets(CubridTable table) throws DBException;

    public final void generateExcel() throws DBException, IOException {
        try {
            List<CubridTable> tables = TableDefinitionFetcher.getTables(monitor, dataSource);
            generateTableNamesSheet(tables);
            for (CubridTable table : tables) {
                generateTableDetailSheets(table);
            }
            saveWorkbook(filePath);
        } finally {
            workbook.close();
        }
    }

    protected void saveWorkbook(String filePath) throws IOException {
        try (FileOutputStream out = new FileOutputStream(filePath)) {
            workbook.write(out);
        }
    }

    public DBRProgressMonitor getMonitor() {
        return monitor;
    }

    public CubridDataSource getDataSource() {
        return dataSource;
    }

    public CellStyle getCenterStyle() {
        return centerStyle;
    }

    public CellStyle getLeftStyle() {
        return leftStyle;
    }

    public CellStyle getRightStyle() {
        return rightStyle;
    }

    public CellStyle getBoldStyle() {
        return boldStyle;
    }

    public Workbook getWorkbook() {
        return workbook;
    }

    public String getDateString() {
        return dateString;
    }

    public String getFullTableName(CubridTable table) {
        return table.getSchema().getName() + "." + table.getName();
    }

    public void applySheetDimensions(Sheet sheet, int... widths) {
        sheet.createRow(0).setHeightInPoints(24);
        if (widths != null && widths.length > 0) {
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
        }
    }

    public void addCell(Sheet sheet, int rowIdx, int colIdx, String content, CellStyle style) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) {
            row = sheet.createRow(rowIdx);
        }
        Cell cell = row.createCell(colIdx);
        cell.setCellValue(content != null ? content : "");
        cell.setCellStyle(style);
    }

    public void addCell(Sheet sheet, int rowIdx, int colIdx, long value, CellStyle style) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) {
            row = sheet.createRow(rowIdx);
        }
        Cell cell = row.createCell(colIdx);
        if (value != 0) {
            cell.setCellValue(value);
        } else {
            cell.setBlank();
        }
        cell.setCellStyle(style);
    }

    public void mergeCell(Sheet sheet, int firstRow, int lastRow, int firstCol, int lastCol) {
        CellRangeAddress region = new CellRangeAddress(firstRow, lastRow, firstCol, lastCol);
        sheet.addMergedRegion(region);

        RegionUtil.setBorderTop(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderLeft(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
    }

    public String getUniqueSheetName(String tableName) {
        String safeBaseName = WorkbookUtil.createSafeSheetName(tableName);
        String name = safeBaseName.length() > 31 ? safeBaseName.substring(0, 31) : safeBaseName;
        int count = 1;

        while (workbook.getSheet(name) != null) {
            String suffix = "_" + count++;
            int maxLen = 31 - suffix.length();
            String trimmedBase = safeBaseName.length() > maxLen ? safeBaseName.substring(0, maxLen) : safeBaseName;
            name = trimmedBase + suffix;
        }

        return name;
    }
}
