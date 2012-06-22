package codehint.dialogs;

import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;

import codehint.Activator;
import codehint.Synthesizer;
import codehint.exprgen.ExpressionSkeleton;
import codehint.property.Property;
import codehint.utils.EclipseUtils;

public abstract class SynthesisDialog extends ModelessDialog {
	
	private Text pdspecInput;
	private String pdspecResult;
	
	private final String initialSkeletonText;
	private Text skeletonInput;
	private final IInputValidator skeletonValidator;
	private String skeletonResult;

    private Button okButton;
	
	private boolean getSkeleton;

	protected SynthesisDialog(Shell parentShell, String varName, String varTypeName, IJavaStackFrame stack, boolean getSkeleton) {
		super(parentShell);
		this.pdspecResult = null;
		ExpressionSkeleton lastCrashedSkeleton = Synthesizer.getLastCrashedSkeleton(varName);
		if (lastCrashedSkeleton == null)
			this.initialSkeletonText = ExpressionSkeleton.HOLE_SYNTAX;
		else
			this.initialSkeletonText = lastCrashedSkeleton.getSugaredString();
		this.skeletonValidator = new ExpressionSkeletonValidator(stack, varTypeName);
		this.skeletonResult = null;
		this.getSkeleton = getSkeleton;
	}
	
	// Layout code

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite) super.createDialogArea(parent);

		pdspecInput = createInput(composite, getPdspecMessage(), getInitialPdspecText(), getPdspecValidator(), getHelpID());
		
		if (getSkeleton) {
			String message = "Give an expression skeleton that describes the form of the desired expression, using " + ExpressionSkeleton.HOLE_SYNTAX + "s for unknown expressions and names.";
			skeletonInput = createInput(composite, message, initialSkeletonText, skeletonValidator, "skeleton");
		}
		
		return composite;
	}
	
	private Text createInput(Composite composite, String message, String initialText, final IInputValidator validator, String helpID) {
		Label label = new Label(composite, SWT.NONE);
		label.setText(message);
		label.setFont(composite.getFont());
		
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
                setErrorMessage(errorText, validator.isValid(input.getText()));
            }
        });
        
		return input;
	}

    private void setErrorMessage(Text errorText, String errorMessage) {
    	if (!errorText.isDisposed()) {
    		errorText.setText(errorMessage == null ? " \n " : errorMessage);
    		boolean hasError = errorMessage != null;
    		errorText.setEnabled(hasError);
    		errorText.setVisible(hasError);
    		errorText.getParent().update();
			okButton.setEnabled(!hasError);
    	}
    }
    
    @Override
	protected Button createButton(Composite parent, int id, String label, boolean defaultButton) {
    	Button button = super.createButton(parent, id, label, defaultButton);
    	if (id == IDialogConstants.OK_ID)
    		okButton = button;
    	return button;
    }

    @Override
	protected void buttonPressed(int buttonId) {
        if (buttonId == IDialogConstants.OK_ID) {
            pdspecResult = pdspecInput.getText();
            if (skeletonInput != null)
            	skeletonResult = skeletonInput.getText();
        } else {
        	pdspecResult = null;
        	skeletonResult = null;
        }
        super.buttonPressed(buttonId);
    }
    
    // Logic code
    
    protected String getPdspecText() {
    	return pdspecResult;
    }
    
    protected static String getFullMessage(String message, String extraMessage) {
    	if (extraMessage == null)
    		return message;
    	else
    		return message + System.getProperty("line.separator") + extraMessage;
    }
    
    protected abstract String getPdspecMessage();
    
    protected abstract String getInitialPdspecText();
    
    protected abstract IInputValidator getPdspecValidator();
    
    protected abstract String getHelpID();
    
    public abstract Property getProperty();
    
    public ExpressionSkeleton getSkeleton() {
    	if (skeletonResult == null)
    		return null;
    	else
    		return ExpressionSkeleton.fromString(skeletonResult);
    }

	private static class ExpressionSkeletonValidator implements IInputValidator {
		
	    private final IJavaStackFrame stackFrame;
	    private final IAstEvaluationEngine evaluationEngine;
	    private final String varTypeName;
	    
	    public ExpressionSkeletonValidator(IJavaStackFrame stackFrame, String varTypeName) {
	    	this.stackFrame = stackFrame;
	    	this.evaluationEngine = EclipseUtils.getASTEvaluationEngine(stackFrame);
	    	this.varTypeName = varTypeName;
	    }
	    
	    @Override
		public String isValid(String newText) {
	    	return ExpressionSkeleton.isLegalSkeleton(newText, varTypeName, stackFrame, evaluationEngine);
	    }
	}

}
