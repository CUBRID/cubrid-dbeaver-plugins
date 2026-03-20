package org.cubrid.dbeaver.export.loaddb.ui;

import org.cubrid.dbeaver.export.loaddb.core.CubridLoadDBExporter;
import org.cubrid.dbeaver.export.loaddb.model.CubridExportSettings;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.wizard.Wizard;
import org.jkiss.dbeaver.ext.cubrid.model.CubridDataSource;
import org.jkiss.dbeaver.ext.cubrid.model.CubridUser;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.runtime.DefaultProgressMonitor;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.runtime.DBWorkbench;

public class CubridLoadDBExportWizard extends Wizard {

    private CubridTableSelectionWizardPage tableSelectionPage;
    private CubridExportOptionsWizardPage exportOptionPage;
    private CubridExportConfirmationWizardPage confirmationPage;
    private CubridExportSettings settings;
    private CubridDataSource dataSource;
    private CubridUser selectedSchema;

    public CubridLoadDBExportWizard(CubridDataSource dataSource, CubridUser selectedSchema) {
        setWindowTitle("Export Load DB");
        this.settings = new CubridExportSettings();
        this.dataSource = dataSource;
        this.selectedSchema = selectedSchema;
        getSettings().setDataSource(dataSource);
    }

    @Override
    public void addPages() {
        tableSelectionPage = new CubridTableSelectionWizardPage(this, selectedSchema);
        exportOptionPage = new CubridExportOptionsWizardPage(this);
        confirmationPage = new CubridExportConfirmationWizardPage(this, settings, selectedSchema);

        addPage(tableSelectionPage);
        addPage(exportOptionPage);
        addPage(confirmationPage);
    }

    @Override
    public boolean performCancel() {
        boolean confirmed = MessageDialog.openConfirm(
            getShell(),
            "Cancel Export",
            "Are you sure you want to cancel the export?\nAll settings will be lost."
        );
        return confirmed;
    }

    @Override
    public boolean performFinish() {
    	boolean confirmed = MessageDialog.openConfirm(
            getShell(),
            "Confirm Export",
            "Are you sure you want to export the selected database objects?"
        );
        if (confirmed) {
            try {
                ProgressMonitorDialog progressDialog = new ProgressMonitorDialog(getShell());
                progressDialog.run(true, true, monitor -> {
                    DBRProgressMonitor dbMonitor = new DefaultProgressMonitor(monitor);
                    CubridLoadDBExporter export = new CubridLoadDBExporter(dbMonitor, dataSource, settings, selectedSchema);
    	            monitor.beginTask("Generating LoadDB file...", IProgressMonitor.UNKNOWN);
    	            export.exportLoadDB();
    	            monitor.done();
    	        });
    	        MessageDialog.openInformation(getShell(), "Export Completed", "Database export finished successfully.");
    	        DBWorkbench.getPlatformUI().showWarningNotification("Export Completed", "Database export finished successfully.");
    	        return true;
    	    } catch (Exception e) {
    	        DBWorkbench.getPlatformUI().showError("Export Failed", "An error occurred during export.", e);
    	        return false;
    	    }
        }
        return false;
    }

    @Override
    public boolean canFinish() {
        return getContainer().getCurrentPage() == confirmationPage;
    }

    public CubridExportSettings getSettings() {
        return settings;
    }
	
    protected CubridExportSettings createSettings() {
        return new CubridExportSettings();
    }
}
