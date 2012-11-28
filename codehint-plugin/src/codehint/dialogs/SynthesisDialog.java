package codehint.dialogs;

import java.util.ArrayList;

import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;

import codehint.Activator;
import codehint.expreval.FullyEvaluatedExpression;
import codehint.property.Property;
import codehint.utils.EclipseUtils;

public abstract class SynthesisDialog extends ModelessDialog {
	
	private final String varTypeName;
	protected final IJavaType varType;
	
	private int comboIndex;

    private static final int MESSAGE_WIDTH = 1000;
	protected PropertyDialog propertyDialog;
	private Composite pdspecComposite;
	protected Text pdspecInput;
	protected boolean pdspecIsValid;

    protected Button okButton;

    protected final IJavaStackFrame stack;

    protected ArrayList<FullyEvaluatedExpression> results;
    protected Property property;

    protected SynthesisDialog(Shell parentShell, String varTypeName, IJavaType varType, IJavaStackFrame stack, PropertyDialog propertyDialog) {
		super(parentShell);
		this.varTypeName = varTypeName;
		this.varType = varType;
		this.comboIndex = -1;
		this.propertyDialog = propertyDialog;
		this.pdspecIsValid = false;
		this.okButton = null;
		this.stack = stack;
		this.results = null;
		this.property = null;
	}
	
	// Layout code

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite)super.createDialogArea(parent);

		makeCombo(composite);

		pdspecComposite = makeChildComposite(composite, GridData.HORIZONTAL_ALIGN_FILL, 1);
		pdspecInput = createInput(pdspecComposite, propertyDialog.getPdspecMessage(), propertyDialog.getInitialPdspecText(), propertyDialog.getPdspecValidator(), getPropertyModifyHandler(), propertyDialog.getHelpID());
		pdspecInput.setFocus();
		pdspecInput.selectAll();
		
		return composite;
	}

	private void makeCombo(Composite composite) {
		Composite comboComposite = makeChildComposite(composite, GridData.HORIZONTAL_ALIGN_CENTER, 2);
		
		Label comboLabel = new Label(comboComposite, SWT.NONE);
		comboLabel.setText("Select pdspec type: ");
		comboLabel.setFont(comboComposite.getFont());
		
		final Combo combo = new Combo(comboComposite, SWT.READ_ONLY);
		combo.setItems(new String[] { "Demonstrate value", "Demonstrate type", "Demonstrate state property"/*, "Demonstrate lambda property"*/ });
		combo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				int index = combo.getSelectionIndex();
				if (index == comboIndex)
					return;
				if (index == 0) {
					if (EclipseUtils.isObject(varType))
						propertyDialog = new ObjectValuePropertyDialog(propertyDialog.getVarName(), varTypeName, stack, "", propertyDialog.getExtraMessage());
					else if (EclipseUtils.isArray(varType))
						propertyDialog = new ArrayValuePropertyDialog(propertyDialog.getVarName(), varTypeName, stack, "", propertyDialog.getExtraMessage());
					else
						propertyDialog = new PrimitiveValuePropertyDialog(propertyDialog.getVarName(), varTypeName, stack, "", propertyDialog.getExtraMessage());
				} else if (index == 1)
					propertyDialog = new TypePropertyDialog(propertyDialog.getVarName(), varTypeName, stack, varTypeName, propertyDialog.getExtraMessage());
				else if (index == 2)
					propertyDialog = new StatePropertyDialog(propertyDialog.getVarName(), stack, "", propertyDialog.getExtraMessage());
				/*else if (index == 3)
					propertyDialog = new LambdaPropertyDialog(propertyDialog.getVarName(), varTypeName, varType, stack, "", propertyDialog.getExtraMessage());*/
				else
					throw new IllegalArgumentException();
				comboIndex = index;
				for (Control c: pdspecComposite.getChildren())
					c.dispose();
				pdspecInput = createInput(pdspecComposite, propertyDialog.getPdspecMessage(), propertyDialog.getInitialPdspecText(), propertyDialog.getPdspecValidator(), getPropertyModifyHandler(), propertyDialog.getHelpID());
				pdspecInput.setFocus();
				pdspecInput.selectAll();
				pdspecComposite.layout(true);
				pdspecComposite.getParent().layout(true);
				propertyTypeChanged();
			}
		});
		
		if (propertyDialog instanceof ValuePropertyDialog)
			comboIndex = 0;
		else if (propertyDialog instanceof TypePropertyDialog)
			comboIndex = 1;
		else if (propertyDialog instanceof StatePropertyDialog)
			comboIndex = 2;
		/*else if (propertyDialog instanceof LambdaPropertyDialog)
			comboIndex = 3;*/
		else
			throw new IllegalArgumentException();
		combo.select(comboIndex);
	}
	
	// For buttons, pass in 0 for numColumns, as createButtons increments it.
	protected static Composite makeChildComposite(Composite composite, int horizStyle, int numColumns) {
		Composite buttonComposite = new Composite(composite, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = numColumns;
		buttonComposite.setLayout(layout);
		buttonComposite.setLayoutData(new GridData(horizStyle | GridData.VERTICAL_ALIGN_CENTER));
		buttonComposite.setFont(composite.getFont());
		return buttonComposite;
	}
	
	protected Text createInput(Composite composite, String message, String initialText, final IInputValidator validator, final ModifyHandler modifyListener, String helpID) {
		Label label = new Label(composite, SWT.WRAP);
		label.setText(message);
		label.setFont(composite.getFont());
		GridData gridData = new GridData();
		gridData.widthHint = MESSAGE_WIDTH;
		label.setLayoutData(gridData);
		
		final Text input = new Text(composite, SWT.SINGLE | SWT.BORDER);
		input.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
		input.setText(initialText);
        PlatformUI.getWorkbench().getHelpSystem().setHelp(input, Activator.PLUGIN_ID + "." + helpID);
		
		final Text errorText = new Text(composite, SWT.READ_ONLY | SWT.WRAP);
		errorText.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL));
		errorText.setBackground(errorText.getDisplay().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
		
		input.addModifyListener(new ModifyListener() {
            @Override
			public void modifyText(ModifyEvent e) {
            	modifyListener.handle(input, validator, errorText);
            }
        });
    	modifyListener.handle(input, validator, errorText);
        
		return input;
	}
	
	protected abstract class ModifyHandler {

		public void handle(Text input, IInputValidator validator, Text errorText) {
			boolean hasError = setErrorMessage(errorText, validator.isValid(input.getText()));
            inputChanged(hasError);
		}
        
        public abstract void inputChanged(boolean hasError);
        
	}
	
	protected class PropertyModifyHandler extends ModifyHandler {

        @Override
		public void inputChanged(boolean hasError) {
			pdspecIsValid = !hasError;
        }
		
	}
	
	protected PropertyModifyHandler getPropertyModifyHandler() {
		return new PropertyModifyHandler();
	}
    
    @Override
    protected Button createButton(Composite parent, int id, String label, boolean defaultButton) {
    	Button button = super.createButton(parent, id, label, defaultButton);
    	if (id == IDialogConstants.OK_ID)
            okButton = button;
    	return button;
    }

    private static boolean setErrorMessage(Text errorText, String errorMessage) {
		boolean hasError = errorMessage != null;
    	if (!errorText.isDisposed()) {
    		errorText.setText(errorMessage == null ? " \n " : errorMessage);
    		errorText.setEnabled(hasError);
    		errorText.setVisible(hasError);
    		errorText.getParent().update();
    	}
    	return hasError;
    }
    
    /**
     * Called when the user changes the type of the pdspec
     * they intend to give.
     */
    protected void propertyTypeChanged() {
    	
    }
    
    // Logic code
    
    public ArrayList<FullyEvaluatedExpression> getExpressions() {
     	return results;
    }
    
    public Property getProperty() {
    	return property;
    }
    
    // Cleanup 
    
    @Override
	protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("CodeHint");
     }

	public void cleanup() {
		propertyDialog = null;
		pdspecComposite = null;
		pdspecInput = null;
		results = null;
		property = null;
	}

}
