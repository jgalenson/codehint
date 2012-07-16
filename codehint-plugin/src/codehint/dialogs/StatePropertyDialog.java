package codehint.dialogs;

import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jface.dialogs.IInputValidator;

import codehint.property.Property;
import codehint.property.StateProperty;
import codehint.utils.EclipseUtils;

public class StatePropertyDialog extends PropertyDialog {
	
	private final String pdspecMessage;
	private final String initialPdspecText;
	private final IInputValidator pdspecValidator;
    
    public StatePropertyDialog(String varName, IJavaStackFrame stack, String initialValue, String extraMessage) {
    	super(varName, extraMessage);
    	String pdspecMessage = "Demonstrate a state property that should hold for " + varName + " after this statement is executed.  You may refer to the values of variables after this statement is executed using the prime syntax, e.g., " + varName + "\'";
    	this.pdspecMessage = getFullMessage(pdspecMessage, extraMessage);
    	this.initialPdspecText = initialValue;
    	this.pdspecValidator = new StatePropertyValidator(stack);
    }

	@Override
	public String getPdspecMessage() {
    	return pdspecMessage;
	}

	@Override
	public String getInitialPdspecText() {
		return initialPdspecText;
	}

	@Override
	public IInputValidator getPdspecValidator() {
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
	public Property computeProperty(String propertyText) {
		if (propertyText == null)
			return null;
		else
			return StateProperty.fromPropertyString(varName, propertyText);
	}

	@Override
	public String getHelpID() {
		return "state";
	}

}
