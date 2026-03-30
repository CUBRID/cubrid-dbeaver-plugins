package org.cubrid.dbeaver.export.loaddb.ui;

import org.cubrid.dbeaver.export.loaddb.model.CubridExportObjectInfo;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.jkiss.dbeaver.ext.cubrid.model.CubridDataSource;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.runtime.AbstractJob;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.ui.UIUtils;

public class CubridExportOptionsWizardPage extends WizardPage {

    private static final String[] ALL_CHARSET = {"UTF-8", "Cp1252", "ISO-8859-1", "EUC-KR", "EUC-JP", "GB2312", "GBK"};
    private CubridLoadDBExportWizard wizard;
    private Text path;
    private Text jdbcCombo;
    private Button browse;
    private Button autoInc;
    private Button splitSchemaFile;
    private boolean charsetLoaded = false;

    public CubridExportOptionsWizardPage(CubridLoadDBExportWizard wizard) {
        super("Export Configuration");
        setTitle("Export Configuration");
        setDescription("Set database export settings");
        this.wizard = wizard;
    }

    @Override
    public void createControl(Composite parent) {
        Composite composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout(1, false));
        setControl(composite);

        Group exportGroup = new Group(composite, SWT.NONE);
        exportGroup.setText("Export Options");
        exportGroup.setLayout(new GridLayout(4, false));
        exportGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        createExportRow(exportGroup, "Schema");
        createExportRow(exportGroup, "Index");
        createExportRow(exportGroup, "Trigger");
        createExportRow(exportGroup, "Data");
        
        Group locationGroup = new Group(composite, SWT.NONE);
        locationGroup.setText("Export Location");
        locationGroup.setLayout(new GridLayout(3, false));
        locationGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        new Label(locationGroup, SWT.NONE).setText("Location:");

        path = new Text(locationGroup, SWT.BORDER | SWT.READ_ONLY);
        path.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        path.setEditable(true);

        browse = new Button(locationGroup, SWT.PUSH);
        browse.setText("Browse...");
        browse.setEnabled(true);
        
        Group dataOptions = new Group(composite, SWT.NONE);
        dataOptions.setText("Data Options");
        dataOptions.setLayout(new GridLayout(1, false));
        dataOptions.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        Composite charsetRow = new Composite(dataOptions, SWT.NONE);
        GridLayout charsetLayout = new GridLayout(4, false);
        charsetRow.setLayout(charsetLayout);
        charsetRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(charsetRow, SWT.NONE).setText("JDBC Charset:");
        
        jdbcCombo = new Text(charsetRow, SWT.BORDER);
        jdbcCombo.setText("Loading...");
        jdbcCombo.setEnabled(false);
        jdbcCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(charsetRow, SWT.NONE).setText("File Charset:");

        Combo fileCombo = new Combo(charsetRow, SWT.READ_ONLY);
        fileCombo.setItems(ALL_CHARSET);
        fileCombo.select(0);
        fileCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        splitSchemaFile = new Button(dataOptions, SWT.CHECK);
        splitSchemaFile.setText("Split schema files");
        splitSchemaFile.setSelection(false);
        splitSchemaFile.setEnabled(true);
        splitSchemaFile.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                wizard.getSettings().setSplitSchemaFile(splitSchemaFile.getSelection());
            }
        });

        autoInc = new Button(dataOptions, SWT.CHECK);
        autoInc.setText("Use auto increment value with recent incremented value on");
        autoInc.setSelection(true);
        autoInc.setEnabled(true);
        autoInc.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        autoInc.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                wizard.getSettings().setExportStartValue(autoInc.getSelection());
            }
        });

        fileCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                wizard.getSettings().setCharset(fileCombo.getText());
            }
        });
    
        browse.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
            	DirectoryDialog dialog = new DirectoryDialog(parent.getShell(), SWT.SAVE);
                String selectedPath = dialog.open();
                if (selectedPath != null) {
                    path.setText(selectedPath);
                    wizard.getSettings().setOutputFolderPattern(selectedPath);
                }
                updateState();
            }
        });
    }
    
    private void createExportRow(Composite parent, String name) {
    	CubridExportObjectInfo info =  new CubridExportObjectInfo(name, true);
        wizard.getSettings().getExportObjects().add(info);

        Button check = new Button(parent, SWT.CHECK);
        check.setText(name);
        check.setSelection(true);

        check.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                boolean isChecked = check.getSelection();
                info.setExport(isChecked);
                if ("schema".equalsIgnoreCase(name)) {
                    autoInc.setEnabled(isChecked);
                    splitSchemaFile.setEnabled(isChecked);
                }
                boolean anySelected = wizard.getSettings()
                        .getExportObjects().stream()
                        .anyMatch(CubridExportObjectInfo::isExport);
                path.setEnabled(anySelected);
                browse.setEnabled(anySelected);
                updateState();
            }
        });
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            loadCharset();
        }
    }

    public void loadCharset() {
        if (charsetLoaded) {
            return;
        }
        new AbstractJob("Load CUBRID Charset") {
            @Override
            protected IStatus run(DBRProgressMonitor monitor) {
                String finalCharset = "UTF-8"; 

                CubridDataSource dataSource = wizard.getSettings().getDataSource();
                String sql = dataSource.wrapShardQuery("SELECT charset FROM db_root");
                try (JDBCSession session = DBUtils.openMetaSession(monitor, dataSource, "Load charset")) {
                    try (JDBCPreparedStatement stmt = session.prepareStatement(sql)) {
                        try (JDBCResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                int charsetNumber = rs.getInt("charset");
                                switch (charsetNumber) {
                                    case 2: finalCharset = "Binary"; break;
                                    case 3: finalCharset = "ISO-8859-1"; break;
                                    case 4: finalCharset = "EUC-KR"; break;
                                    default: finalCharset = "UTF-8"; break;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Could not load charset: " + e.getMessage());
                }

                final String result = finalCharset;

                UIUtils.syncExec(() -> {
                    if (!jdbcCombo.isDisposed()) {
                        jdbcCombo.setText(result);
                    }
                });
                charsetLoaded = true;
                return Status.OK_STATUS;
            }
        }.schedule();
    }

    @Override
    public boolean isPageComplete() {
    	var objects = wizard.getSettings().getExportObjects();
        boolean anySelected = objects.stream().anyMatch(CubridExportObjectInfo::isExport);
        if (!anySelected) {
            return false;
        }
        String selectedPath = path.getText();
        if (selectedPath == null || selectedPath.trim().isEmpty()) {
            return false;
        }
        return true;
    }

    protected void updateState() {
    	setPageComplete(isPageComplete());
    }
}
