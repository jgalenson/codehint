package codehint.dialogs;

import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jface.dialogs.IInputValidator;

import codehint.exprgen.TypeCache;
import codehint.property.Property;

public abstract class PropertyDialog {
	
	protected final String varName;
	protected final String extraMessage;
	protected final IJavaStackFrame stack;
	
	protected PropertyDialog(String varName, String extraMessage, IJavaStackFrame stack) {
		this.varName = varName;
		this.extraMessage = extraMessage;
		this.stack = stack;
	}

	public abstract String getPdspecMessage();
    
	public abstract String getInitialPdspecText();
    
	public abstract IInputValidator getPdspecValidator();
    
	public abstract String getHelpID();
    
	public abstract Property computeProperty(String propertyText, TypeCache typeCache);
    
    protected static String getFullMessage(String message, String extraMessage) {
    	if (extraMessage == null)
    		return message;
    	else
    		return message + System.getProperty("line.separator") + extraMessage;
    }
    
    public String getVarName() {
    	return varName;
    }
    
    public String getExtraMessage() {
    	return extraMessage;
    }

}
