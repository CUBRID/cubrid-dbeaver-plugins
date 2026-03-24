package org.cubrid.dbeaver.export.loaddb.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.cubrid.dbeaver.export.loaddb.model.CubridExportObjectInfo;
import org.cubrid.dbeaver.export.loaddb.model.CubridExportSettings;
import org.jkiss.dbeaver.ext.cubrid.model.CubridDataSource;
import org.jkiss.dbeaver.ext.cubrid.model.CubridSequence;
import org.jkiss.dbeaver.ext.cubrid.model.CubridTable;
import org.jkiss.dbeaver.ext.cubrid.model.CubridUser;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;

public class CubridLoadDBExporter {

    private DBRProgressMonitor monitor;
    private CubridExportSettings settings;
    private CubridLoadDBRepository repo;
    private CubridLoadDBSQLBuilder sql;
    private List<CubridUser> users = new ArrayList<>();
    private List<CubridTable> tables = new ArrayList<>();
    private List<CubridSequence> serials = new ArrayList<>();

    public CubridLoadDBExporter(
        DBRProgressMonitor monitor,
        CubridDataSource dataSource,
        CubridExportSettings settings,
        CubridUser selectedSchema
    ) {
        this.monitor = monitor;
        this.settings = settings;
        this.repo = new CubridLoadDBRepository(monitor, dataSource, settings, selectedSchema);
        this.sql = new CubridLoadDBSQLBuilder(monitor, dataSource, settings, repo);

        this.users.addAll(repo.loadUsers());
        this.tables.addAll(repo.loadTables(users));
        this.serials.addAll(repo.loadSerials(users));
    }

    public void exportLoadDB() {
        List<CubridExportObjectInfo> items = settings.getExportObjects();
        String charset = settings.getCharset();

        for (CubridExportObjectInfo co : items) {
            if (monitor.isCanceled()) return;
            if (!co.isExport()) {
                continue;
            }
            switch (co.getName()) {
                case "Schema":
                    exportSchema(co, charset);
                    break;
                case "Index":
                    exportIndex(co, charset);
                    break;
                case "Trigger":
                    exportTrigger(co, charset);
                    break;
                case "Data":
                    exportData(co, charset);
                    break;
            }
        }
        generateReport();
    }

    private void generateReport() {
        List<String> errors = settings.getErrorMessages();
        if (errors.isEmpty()) {
            return;
        }

        String timeStamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        String outputFolder = settings.getOutputFolderPattern();
        String reportFileName = outputFolder + File.separator + "loaddb_export_results_" + timeStamp + ".log";

        StringBuilder sb = new StringBuilder();
        sb.append("CUBRID LoadDB Export Results Report\n");
        sb.append("Generated on: ").append(new java.util.Date().toString()).append("\n");
        sb.append("===================================\n\n");

        for (String error : errors) {
            sb.append("- ").append(error).append("\n");
        }

        repo.saveFile(sb, reportFileName, settings.getCharset());
    }

    public void exportSchema(CubridExportObjectInfo info, String charset) {
        String fileName = settings.getOutputFile(info);

        if (settings.isSplitSchemaFile()) {
            // 1) tables + serials + fks
            StringBuilder sb = new StringBuilder();
            sql.buildTable(sb, tables, serials);
            sql.buildForeignKey(sb, tables);
            if (sb.length() > 0) {
                sb.append("COMMIT WORK;");
                repo.saveFile(sb, fileName, charset);
            }

            // 2) vclass
            sb.setLength(0);
            sql.buildView(sb, users);
            if (sb.length() > 0) {
                sb.append("COMMIT WORK;");
                repo.saveFile(sb, fileName + "_vclass", charset);
            }

            // 3) vclass query spec
            sb.setLength(0);
            sql.buildViewQuerySpec(sb, users);
            if (sb.length() > 0) {
                sb.append("COMMIT WORK;");
                repo.saveFile(sb, fileName + "_vclass_query_spec", charset);
            }
        } else {
            StringBuilder sb = new StringBuilder();
            sql.buildTable(sb, tables, serials);
            sql.buildView(sb, users);
            sql.buildViewQuerySpec(sb, users);
            sql.buildForeignKey(sb, tables);
            if (sb.length() > 0) {
                sb.append("COMMIT WORK;");
                repo.saveFile(sb, fileName, charset);
            }
        }
    }

    public void exportIndex(CubridExportObjectInfo info, String charset) {
        String fileName = settings.getOutputFile(info);
        StringBuilder sb = new StringBuilder();

        for (CubridTable table : tables) {
            if (monitor.isCanceled()) return;
            sql.buildIndex(sb, table);
        }
        if (sb.length() > 0) {
            sb.append("COMMIT WORK;");
            repo.saveFile(sb, fileName, charset);
        }
    }

    public void exportTrigger(CubridExportObjectInfo info, String charset) {
        String fileName = settings.getOutputFile(info);
        StringBuilder sb = new StringBuilder();
        sql.buildTrigger(sb, users);
        if (sb.length() > 0) {
            sb.append("COMMIT WORK;");
            repo.saveFile(sb, fileName, charset);
        }
    }

    public void exportData(CubridExportObjectInfo info, String charset) {
        final int FLUSH_THRESHOLD = 16 * 1024 * 1024; // 16 MB threshold
        String fileName = settings.getOutputFile(info);
        StringBuilder sb = new StringBuilder();
        repo.saveFile(new StringBuilder(""), fileName, charset); 

        for (CubridTable table : tables) {
            if (monitor.isCanceled()) return;
            sql.buildData(sb, table);
            
            if (sb.length() > FLUSH_THRESHOLD) {
                repo.appendFile(sb, fileName, charset);
                sb.setLength(0);
            }
        }

        if (sb.length() > 0) {
            repo.appendFile(sb, fileName, charset);
        }
    }
}
