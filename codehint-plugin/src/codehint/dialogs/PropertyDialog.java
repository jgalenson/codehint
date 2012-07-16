package codehint.dialogs;

import org.eclipse.jface.dialogs.IInputValidator;

import codehint.property.Property;

public abstract class PropertyDialog {
	
	protected final String varName;
	protected final String extraMessage;
	
	protected PropertyDialog(String varName, String extraMessage) {
		this.varName = varName;
		this.extraMessage = extraMessage;
	}

	public abstract String getPdspecMessage();
    
	public abstract String getInitialPdspecText();
    
	public abstract IInputValidator getPdspecValidator();
    
	public abstract String getHelpID();
    
	public abstract Property computeProperty(String propertyText);
    
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
