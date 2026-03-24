package org.cubrid.dbeaver.export.loaddb.ui;

import org.cubrid.dbeaver.export.loaddb.core.CubridLoadDBExporter;
import org.cubrid.dbeaver.export.loaddb.model.CubridExportSettings;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.Wizard;
import org.jkiss.dbeaver.ext.cubrid.model.CubridDataSource;
import org.jkiss.dbeaver.ext.cubrid.model.CubridUser;
import org.eclipse.core.runtime.IStatus;
import org.jkiss.dbeaver.model.runtime.AbstractJob;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.eclipse.core.runtime.Status;
import java.util.List;

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
            AbstractJob exportJob = new AbstractJob("CUBRID LoadDB Export") {
                @Override
                protected IStatus run(DBRProgressMonitor monitor) {
                    try {
                        CubridLoadDBExporter export = new CubridLoadDBExporter(monitor, dataSource, settings, selectedSchema);
                        monitor.beginTask("Generating LoadDB file...", IProgressMonitor.UNKNOWN);
                        
                        export.exportLoadDB();
                        
                        if (monitor.isCanceled()) {
                            UIUtils.asyncExec(() -> {
                                DBWorkbench.getPlatformUI().showWarningNotification("Export Cancelled", "The database export process was cancelled by the user.");
                            });
                            monitor.done();
                            return Status.CANCEL_STATUS;
                        }

                        List<String> errorMessages = settings.getErrorMessages();
                        boolean hasErrors = errorMessages != null && !errorMessages.isEmpty();
 
                        UIUtils.asyncExec(() -> {
                            if (hasErrors) {
                                DBWorkbench.getPlatformUI().showWarningNotification("Export Finished with Errors", "Check the log file for details.");
                            } else {
                                DBWorkbench.getPlatformUI().showWarningNotification("Export Completed", "Database export finished successfully.");
                            }
                        });

                        monitor.done(); 
                        return Status.OK_STATUS;

                    } catch (Exception e) {
                        if (monitor != null) monitor.done(); 
                        
                        UIUtils.asyncExec(() -> {
                            DBWorkbench.getPlatformUI().showError("Export Failed", "An error occurred during export.", e);
                        });
                        return Status.CANCEL_STATUS;
                    }
                }
            };
            exportJob.setUser(true);
            exportJob.schedule();
            return true;
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
