package org.cam.dbeaver.cubrid.export.excel.ui;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.cam.dbeaver.cubrid.export.excel.ExcelGenericStyle;
import org.cam.dbeaver.cubrid.export.excel.ExcelSimpleStyle;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.cubrid.model.CubridDataSource;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;

public class ExcelExportDialog extends TitleAreaDialog {

    private Text txtPath;
    private Text txtName;
    private Button btnSimple;
    private Button btnGeneric;
    private CubridDataSource dataSource;
    private DocumentStyle selectedStyle = DocumentStyle.SIMPLE;

    private enum DocumentStyle {
        SIMPLE, GENERIC
    }

    public ExcelExportDialog(Shell parentShell, CubridDataSource dataSource) {
        super(parentShell);
        this.dataSource = dataSource;
    }

    @Override
    public void create() {
        super.create();
        setTitle("Exporting table definitions to Excel");
        setMessage("Select export path");
    }

    @Override
    protected boolean isResizable() {
        return true;
    }

    @Override
    protected void okPressed() {
        String path = txtPath.getText();
        String fileName = txtName.getText();
        String fullPath = path + File.separator + fileName + ".xlsx";

        if (path.isEmpty() || fileName.isEmpty()) {
            MessageDialog.openError(getShell(), "Error", "Please input Excel path and name.");
            return;
        }

        ProgressMonitorDialog progress = new ProgressMonitorDialog(getShell());
        try {
            progress.run(true, false, monitor -> {
                monitor.beginTask("Generating Excel file...", IProgressMonitor.UNKNOWN);
                DBRProgressMonitor dbMonitor = new VoidProgressMonitor();
                try {
                    if (selectedStyle == DocumentStyle.GENERIC) {
                        new ExcelGenericStyle(dbMonitor, dataSource, fullPath).generateExcel();
                    } else {
                        new ExcelSimpleStyle(dbMonitor, dataSource, fullPath).generateExcel();
                    }
                } catch (DBException | IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    monitor.done();
                }
            });

            MessageDialog.openInformation(getShell(), "Success", "Excel file created:\n" + fullPath);
            super.okPressed();

        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            MessageDialog.openError(getShell(), "Export Failed", "Failed to generate Excel: " + cause.getMessage());
        }
    }

    @Override
    protected Composite createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);

        Composite inputArea = new Composite(container, SWT.NONE);
        inputArea.setLayout(new GridLayout(3, false));
        inputArea.setLayoutData(new GridData(GridData.FILL_BOTH));

        Label lblPath = new Label(inputArea, SWT.NONE);
        lblPath.setText("Excel path :");

        txtPath = new Text(inputArea, SWT.BORDER | SWT.READ_ONLY);
        txtPath.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Button btnBrowse = new Button(inputArea, SWT.PUSH);
        btnBrowse.setText("Browse...");
        btnBrowse.addListener(SWT.Selection, e -> {
            DirectoryDialog dialog = new DirectoryDialog(parent.getShell());
            String dir = dialog.open();
            if (dir != null) {
                txtPath.setText(dir);
            }
        });

        Label lblName = new Label(inputArea, SWT.NONE);
        lblName.setText("Excel name :");

        String databaseName = dataSource.getName();
        String currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        txtName = new Text(inputArea, SWT.BORDER);
        txtName.setText("tablelist_" + databaseName + "_" + currentDate);
        txtName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label extension = new Label(inputArea, SWT.NONE);
        extension.setText(".xlsx");

        Label lblStyle = new Label(inputArea, SWT.NONE);
        lblStyle.setText("Document style :");

        Composite styleGroup = new Composite(inputArea, SWT.NONE);
        GridLayout styleLayout = new GridLayout(2, false);
        styleLayout.marginWidth = 0;
        styleLayout.marginHeight = 0;
        styleLayout.horizontalSpacing = 10;
        styleGroup.setLayout(styleLayout);
        styleGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        btnSimple = new Button(styleGroup, SWT.RADIO);
        btnSimple.setText("Simple");
        btnSimple.setSelection(true);

        btnGeneric = new Button(styleGroup, SWT.RADIO);
        btnGeneric.setText("Generic");

        btnSimple.addListener(SWT.Selection, e -> {
            if (btnSimple.getSelection()) {
                selectedStyle = DocumentStyle.SIMPLE;
            }
        });

        btnGeneric.addListener(SWT.Selection, e -> {
            if (btnGeneric.getSelection()) {
                selectedStyle = DocumentStyle.GENERIC;
            }
        });
        return container;
    }
}
