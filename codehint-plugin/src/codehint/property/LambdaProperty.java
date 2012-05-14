package codehint.property;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.internal.core.dom.NaiveASTFlattener;

import codehint.EclipseUtils;
import codehint.property.Property;

/**
 * Class that stores a user-entered property (essentially
 * a lambda).
 */
// TODO: We could store the value entered in the concrete cases and then compare against it directly rather than with the EvaluationEngine.q
public class LambdaProperty extends Property {
	
    private final static Pattern lambdaPattern = Pattern.compile("(\\w+)(?: ?: ?(\\w+))?\\s*=>\\s*(.*)");
    protected final static ASTParser parser = ASTParser.newParser(AST.JLS4);
	
	private final String lhs;
	private final String type;
	private final Expression rhs;

	// TODO: If the user has a variable with this name, the property cannot refer to it.
	protected final static String DEFAULT_LHS = "_$x";
	
	protected LambdaProperty(String lhs, String type, Expression rhs) {
		this.lhs = lhs;
		this.type = type;
		this.rhs = rhs;
	}
	
	public static LambdaProperty fromPropertyString(String propertyStr) {
		Matcher matcher = lambdaPattern.matcher(propertyStr);
		matcher.matches();
		String lhs = matcher.group(1);
		String type = matcher.group(2);
		Expression rhs = (Expression)EclipseUtils.parseExpr(parser, matcher.group(3));
		return new LambdaProperty(lhs, type, rhs);
	}
	
	public static String isLegalProperty(String str, IJavaStackFrame stackFrame, IJavaProject project, IType varType, IType thisType) {
		Matcher matcher = lambdaPattern.matcher(str);
    	if (!matcher.matches())
    		return "Input must of the form \"<identifier>(: <type>)? => <expression>\".";
    	String type = matcher.group(2);
    	if (type != null) {
	    	String typeError = EclipseUtils.getValidTypeError(project, varType, thisType, type);
	    	if (typeError != null)
	    		return typeError;
    	}
    	ASTNode rhs = EclipseUtils.parseExpr(parser, matcher.group(3));
    	if (rhs instanceof CompilationUnit)
    		return "The right-hand side must be a valid Java expression.";
    	String lhs = matcher.group(1);
    	try {
			if (stackFrame.findVariable(lhs) != null)
				return "The left-hand side cannot be a variable in the current scope.";
		} catch (DebugException e) {
			e.printStackTrace();
		}
    	return null;
	}
	
	/**
	 * Gets a string representing the evaluation of this
	 * property with lambda argument replaced by the given string.
	 * @param arg Actual argument.
	 * @return  string representing the evaluation of this
	 * property with lambda argument replaced by the given string.
	 */
	public String getReplacedString(String arg) {
		NaiveASTFlattener flattener = new PropertyASTFlattener(lhs, arg, type);
		rhs.accept(flattener);
		String typeStr = type == null ? "" : "(" + arg + " == " + null +" || " + arg + " instanceof " + type + ") && ";
		return typeStr + flattener.getResult();
	}
	
	@Override
	public String toString() {
		String typeStr = type == null ? "" : ": " + type;
		return lhs.toString() + typeStr + " => " + rhs.toString();
	}
	
	private static final class PropertyASTFlattener extends NaiveASTFlattener {
		
		private final String lhs;
		private final String arg;
		private final String type;
		
		public PropertyASTFlattener(String lhs, String arg, String type) {
			super();
			this.lhs = lhs;
			this.arg = arg;
			this.type = type;
		}
		
		@Override
		public boolean visit(SimpleName node) {
			String nodeId = node.getIdentifier();
			this.buffer.append(nodeId.equals(lhs) ? (type == null ? arg : "((" + type + ")" + arg + ")") : nodeId);
			return false;
		}
		
	}

}
