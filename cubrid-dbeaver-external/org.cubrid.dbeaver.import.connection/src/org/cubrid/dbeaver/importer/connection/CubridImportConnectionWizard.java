package org.cubrid.dbeaver.importer.connection;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.cubrid.dbeaver.importer.connection.CubridImportPrefsParser.CMDatabase;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.IWorkbench;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.app.DBPDataSourceRegistry;
import org.jkiss.dbeaver.model.app.DBPProject;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.connection.DBPDriver;
import org.jkiss.dbeaver.registry.DataSourceDescriptor;
import org.jkiss.dbeaver.registry.DataSourceProviderRegistry;
import org.jkiss.dbeaver.runtime.DBWorkbench;

public class CubridImportConnectionWizard extends Wizard implements IImportWizard {

    private static final Log log = Log.getLog(CubridImportConnectionWizard.class);
    private Path selectedFile;
    private String parseError;
    private CubridImportUploadPage uploadPage;
    private CubridImportConfirmPage confirmationPage;
    private DBPDataSourceRegistry registry;
    private DBPDriver driver;
    private List<CMDatabase> databases = new ArrayList<>();

    public CubridImportConnectionWizard() {
        setWindowTitle("Import CUBRID Connection");
        initialize();
    }

    @Override
    public void init(IWorkbench workbench, IStructuredSelection selection) {
        initialize();
    }

    private void initialize() {
        DBPProject project = DBWorkbench.getPlatform().getWorkspace().getActiveProject();
        if (project != null) {
            registry = project.getDataSourceRegistry();
            driver = DBWorkbench.getPlatform().getDataSourceProviderRegistry().findDriver("cubrid_jdbc");
        }
    }

    @Override
    public void addPages() {
        uploadPage = new CubridImportUploadPage(this);
        confirmationPage = new CubridImportConfirmPage(this);
        addPage(uploadPage);
        addPage(confirmationPage);
    }

    @Override
    public boolean performFinish() {
        if (registry == null || driver == null) {
            MessageDialog.openError(getShell(), "Import Error", 
                "Cannot proceed. Please ensure a project is active and the required driver is installed.");
            return false;
        }
        CMDatabase[] selected = confirmationPage.getCheckedDatabases();
        boolean confirmed = MessageDialog.openConfirm(getShell(), "Confirm Import",
                "Are you sure you want to import the selected database connections?");
        if (confirmed) {
            for (CMDatabase db : selected) {
                String host = db.host;
                String port = db.port;
                String dbName = db.dbName;
                String user = db.dbUser;
                if (user.isBlank()) {
                    user = "dba";
                }
                try {
                    DataSourceDescriptor dataSource = createCubridDataSource(host, port, dbName, user);
                    registry.addDataSource(dataSource);
                } catch (Exception ex) {
                    log.error("Failed to create CUBRID connection", ex);
                }
            }
            if (registry != null) {
                registry.flushConfig();
            }
            return true;
        }
        return false;
    }

    public void setSelectedFile(Path path) {
        this.selectedFile = path;
        parseSelectedFile();
    }

    public Path getSelectedFile() {
        return selectedFile;
    }

    public List<CMDatabase> getDatabases() {
        return Collections.unmodifiableList(databases);
    }

    public String getParseError() {
        return parseError;
    }

    private void parseSelectedFile() {
        databases.clear();
        parseError = null;
        if (selectedFile == null) {
            return;
        }
        try {
            databases = CubridImportPrefsParser.parse(selectedFile);
        } catch (Exception e) {
            parseError = e.toString();
        }
    }

    private DataSourceDescriptor createCubridDataSource(String host, String port, String dbName, String user) {
        DBPConnectionConfiguration config = new DBPConnectionConfiguration();
        config.setHostName(host);
        config.setHostPort(port);
        config.setDatabaseName(dbName);
        config.setUserName(user);
        config.setUrl("jdbc:cubrid:" + host + ":" + port + ":" + dbName + ":::");

        DataSourceDescriptor ds = new DataSourceDescriptor(registry, DataSourceDescriptor.generateNewId(driver), driver, config);
        ds.setName(dbName);
        ds.setSavePassword(false);
        return ds;
    }
}
