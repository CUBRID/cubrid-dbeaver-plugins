package org.cubrid.dbeaver.export.loaddb.model;

public class CubridExportObjectInfo {
    private String name;
    private boolean isExport;

    public CubridExportObjectInfo(String name, boolean isExport) {
        this.name = name;
        this.isExport = isExport;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isExport() {
        return isExport;
    }

    public void setExport(boolean isExport) {
        this.isExport = isExport;
    }
}
