package org.cam.dbeaver.cubrid.export.excel.ui;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.cam.dbeaver.cubrid.export.excel.ExcelGenericStyle;
import org.cam.dbeaver.cubrid.export.excel.ExcelSimpleStyle;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.jkiss.dbeaver.ext.cubrid.model.CubridDataSource;
import org.jkiss.dbeaver.model.DBIcon;
import org.jkiss.dbeaver.ui.dialogs.BaseDialog;

public class ExcelExportDialog extends BaseDialog {

	private static DBIcon cubridIcon = new DBIcon("platform:/plugin/org.jkiss.dbeaver.ext.cubrid/icons/cubrid_icon.png");
    private Text txtPath;
    private Text txtName;
    private Font boldFont;
    private Button btnSimple;
    private Button btnGeneric;
    private CubridDataSource dataSource;
	private DocumentStyle selectedStyle = DocumentStyle.SIMPLE;

	private enum DocumentStyle {
	    SIMPLE, GENERIC
	}

	public ExcelExportDialog(Shell parentShell, CubridDataSource dataSource) {
        super(parentShell, "Exporting table definitions to Excel", cubridIcon);
        this.dataSource = dataSource;
    }
 
	@Override
    protected Point getInitialSize() {
        Point calculatedSize = super.getInitialSize();
        calculatedSize.x = 500;
        return calculatedSize;
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

        ProgressMonitorDialog progressDialog = new ProgressMonitorDialog(getShell());
        try {
            progressDialog.run(true, false, (IRunnableWithProgress) monitor -> {
                monitor.beginTask("Generating Excel file...", IProgressMonitor.UNKNOWN);
            	if (selectedStyle == DocumentStyle.GENERIC) {
            		ExcelGenericStyle generic = new ExcelGenericStyle(fullPath, dataSource);
            		generic.generateExcel();
            	} else {
            		ExcelSimpleStyle simple = new ExcelSimpleStyle(fullPath, dataSource);
            		simple.generateExcel();
            	}
                monitor.done();
            });

            MessageDialog.openInformation(getShell(), "Success", "Excel file created:\n" + fullPath);

        try {
            progressDialog.run(true, false, (IRunnableWithProgress) monitor -> {
                monitor.beginTask("Generating Excel file...", IProgressMonitor.UNKNOWN);
            	if (selectedStyle == DocumentStyle.GENERIC) {
            		ExcelGenericStyle generic = new ExcelGenericStyle(fullPath, dataSource);
            		generic.generateExcel();
            	} else {
            		ExcelSimpleStyle simple = new ExcelSimpleStyle(fullPath, dataSource);
            		simple.generateExcel();
            	}
                monitor.done();
            });

            MessageDialog.openInformation(getShell(), "Success", "Excel file created:\n" + fullPath);
            super.okPressed();

        } catch (Exception e) {
            e.printStackTrace();
            MessageDialog.openError(getShell(), "Error", "Failed to generate Excel.");
        }
    }

    @Override
    protected Composite createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        Label title = new Label(container, SWT.NONE);
        title.setText("Exporting table definitions to Excel");
        FontData[] fD = title.getFont().getFontData();
        for (FontData fd : fD) {
            fd.setHeight(10);
            fd.setStyle(SWT.BOLD);
        }
        boldFont = new Font(container.getDisplay(), fD);
        title.setFont(boldFont);
        title.addDisposeListener(e -> boldFont.dispose());

        Label desc = new Label(container, SWT.NONE);
        desc.setText("Select export path");

        Label separator = new Label(container, SWT.SEPARATOR | SWT.HORIZONTAL);
        GridData sepLayout = new GridData(GridData.FILL_HORIZONTAL);
        separator.setLayoutData(sepLayout);
        
        Composite inputArea = new Composite(container, SWT.NONE);
        inputArea.setLayout(new GridLayout(3, false));
        inputArea.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        // === Excel path ===
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

        // === Excel name ===
        Label lblName = new Label(inputArea, SWT.NONE);
        lblName.setText("Excel name :");

        String databaseName = dataSource.getName();
        String currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        GridData gdName = new GridData(SWT.FILL, SWT.CENTER, true, false);
        txtName.setLayoutData(gdName);
        
        Label extension = new Label(inputArea, SWT.NONE);
        extension.setText(".xlsx");

        // === Document style ===
        Label lblStyle = new Label(inputArea, SWT.NONE);
        lblStyle.setText("Document style :");

        // Container for radio buttons
        Composite styleGroup = new Composite(inputArea, SWT.NONE);
        GridLayout styleLayout = new GridLayout(2, false);
        styleLayout.marginWidth = 0;
        styleLayout.marginHeight = 0;
        styleLayout.horizontalSpacing = 10;
        styleGroup.setLayout(styleLayout);

        // Make the buttons align nicely next to the label
        GridData gdStyle = new GridData(SWT.FILL, SWT.CENTER, true, false);
        styleGroup.setLayoutData(gdStyle);

        btnSimple = new Button(styleGroup, SWT.RADIO);
        btnSimple.setText("Simple");
        btnSimple.setSelection(true);

        btnGeneric = new Button(styleGroup, SWT.RADIO);
        btnGeneric.setText("Generic");

        // Listen for selection
        btnSimple.addListener(SWT.Selection, e -> {
            if (btnSimple.getSelection()) selectedStyle = DocumentStyle.SIMPLE;
        });
        btnGeneric.addListener(SWT.Selection, e -> {
            if (btnGeneric.getSelection()) selectedStyle = DocumentStyle.GENERIC;
        });

        return container;
    }
}
