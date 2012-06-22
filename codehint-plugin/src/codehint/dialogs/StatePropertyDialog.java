package codehint.dialogs;

import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.swt.widgets.Shell;

import codehint.property.Property;
import codehint.property.StateProperty;
import codehint.utils.EclipseUtils;

public class StatePropertyDialog extends SynthesisDialog {
	
	private final String varName;
	private final String pdspecMessage;
	private final String initialPdspecText;
	private final IInputValidator pdspecValidator;
    
    public StatePropertyDialog(String varName, String varTypeName, IJavaStackFrame stack, Shell shell, String initialValue, String extraMessage, boolean getSkeleton) {
    	super(shell, varName, varTypeName, stack, getSkeleton);
    	this.varName = varName;
    	String pdspecMessage = "Demonstrate a state property that should hold for " + varName + " after this statement is executed.  You may refer to the values of variables after this statement is executed using the prime syntax, e.g., " + varName + "\'";
    	this.pdspecMessage = getFullMessage(pdspecMessage, extraMessage);
    	this.initialPdspecText = initialValue;
    	this.pdspecValidator = new StatePropertyValidator(stack);
    }

	@Override
	protected String getPdspecMessage() {
    	return pdspecMessage;
	}

	@Override
	protected String getInitialPdspecText() {
		return initialPdspecText;
	}

	@Override
	protected IInputValidator getPdspecValidator() {
		return pdspecValidator;
	}

	private static class StatePropertyValidator implements IInputValidator {
		
	    private final IJavaStackFrame stackFrame;
	    private final IAstEvaluationEngine evaluationEngine;
	    
	    public StatePropertyValidator(IJavaStackFrame stackFrame) {
	    	this.stackFrame = stackFrame;
	    	this.evaluationEngine = EclipseUtils.getASTEvaluationEngine(stackFrame);
	    }
	    
	    @Override
		public String isValid(String newText) {
	    	return StateProperty.isLegalProperty(newText, stackFrame, evaluationEngine);
	    }
	}

	@Override
	public Property getProperty() {
		String pdspecText = getPdspecText();
		if (pdspecText == null)
			return null;
		else
			return StateProperty.fromPropertyString(varName, pdspecText);
	}

	@Override
	protected String getHelpID() {
		return "state";
	}

}
