package org.cubrid.dbeaver.export.loaddb.model;

import java.util.List;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.jkiss.dbeaver.ext.cubrid.model.CubridDataSource;

public class CubridExportSettings {

    private List<String> tables = new ArrayList<>();
    private List<CubridExportObjectInfo> exportObjects = new ArrayList<>();
    private String charset = "UTF-8";
    private CubridExportSettings settings;
    private CubridDataSource dataSource;
    private boolean isSplitSchemaFile = false;
    private boolean isExportStartValue = true;
    private String outputFolderPattern;

    public List<CubridExportObjectInfo> getExportObjects() {
        return exportObjects;
    }

    public String getOutputFile(CubridExportObjectInfo info) {
    	String dbName = dataSource.getContainer().getName();
    	String suffix = null;
    	switch (info.getName()) {
        case "Schema":
            suffix = "schema";
            break;
        case "Index":
            suffix = "indexes";
            break;
        case "Trigger":
            suffix = "trigger";
            break;
        case "Data":
            suffix = "objects";
            break;
    	}
    	String fileName = dbName + "_" + suffix;
        return Paths.get(getOutputFolder(info), fileName).toString();
    }

    public String getOutputFolder(CubridExportObjectInfo info) {
        return getOutputFolderPattern();
    }

    public String getOutputFolderPattern() {
        return outputFolderPattern;
    }

    public void setOutputFolderPattern(String outputFolderPattern) {
        this.outputFolderPattern = outputFolderPattern;
    }

    public CubridExportObjectInfo getExportObject(String name) {
        return exportObjects.stream().filter(item -> item.getName().equals(name)).findAny().orElse(null);
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public boolean isSplitSchemaFile() {
        return isSplitSchemaFile;
    }

    public void setSplitSchemaFile(boolean isSplitSchemaFile) {
    	this.isSplitSchemaFile = isSplitSchemaFile;
    }

    public boolean isExportStartValue() {
        return isExportStartValue;
    }

    public void setExportStartValue(boolean isExportStartValue) {
    	this.isExportStartValue = isExportStartValue;
    }

    public CubridDataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(CubridDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<String> getTables() {
        return tables;
    }

    public void setTables(List<String> tables) {
        this.tables = tables;
    }

    public CubridExportSettings getSettings() {
        return settings;
    }

    public void setSettings(CubridExportSettings settings) {
        this.settings = settings;
    }
}
