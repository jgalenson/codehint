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
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;

import codehint.utils.EclipseUtils;
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
	private final String typeName;
	private final Expression rhs;

	// TODO: If the user has a variable with this name, the property cannot refer to it.
	protected final static String DEFAULT_LHS = "_$x";
	
	protected LambdaProperty(String lhs, String type, Expression rhs) {
		this.lhs = lhs;
		this.typeName = type;
		this.rhs = rhs;
	}
	
	public static LambdaProperty fromPropertyString(String propertyStr) {
		Matcher matcher = lambdaPattern.matcher(propertyStr);
		matcher.matches();
		String lhs = matcher.group(1);
		String typeName = matcher.group(2);
		Expression rhs = (Expression)EclipseUtils.parseExpr(parser, matcher.group(3));
		return new LambdaProperty(lhs, typeName, rhs);
	}
	
	public static String isLegalProperty(String str, IJavaStackFrame stackFrame, IJavaProject project, String varTypeName, IType thisType, IAstEvaluationEngine evaluationEngine, String varName) {
		Matcher matcher = lambdaPattern.matcher(str);
    	if (!matcher.matches())
    		return "Input must of the form \"<identifier>(: <type>)? => <expression>\".";
    	String typeName = matcher.group(2);
    	if (typeName != null) {
	    	String typeError = EclipseUtils.getValidTypeError(project, varTypeName, thisType, typeName);
	    	if (typeError != null)
	    		return typeError;
    	}
    	ASTNode rhs = EclipseUtils.parseExpr(parser, matcher.group(3));
    	if (rhs instanceof CompilationUnit)
    		return "Enter a valid expression on the RHS: " + ((CompilationUnit)rhs).getProblems()[0].getMessage();
    	String lhs = matcher.group(1);
    	try {
			if (stackFrame.findVariable(lhs) != null)
				return "The left-hand side cannot be a variable in the current scope.";
		} catch (DebugException e) {
			e.printStackTrace();
		}
    	PropertyASTFlattener flattener = new PropertyASTFlattener(lhs, varName, typeName);
		String compileErrors = EclipseUtils.getCompileErrors(flattener.getResult(rhs), "boolean", stackFrame, evaluationEngine);
		if (compileErrors != null)
			return compileErrors;
    	return null;
	}
	
	public String getTypeName() {
		return typeName;
	}
	
	/**
	 * Gets a string representing the evaluation of this
	 * property with lambda argument replaced by the given string.
	 * @param arg Actual argument.
	 * @return  string representing the evaluation of this
	 * property with lambda argument replaced by the given string.
	 */
	@Override
	public String getReplacedString(String arg, IJavaStackFrame stack) {
		String typeStr = typeName == null ? "" : "(" + arg + " == " + null +" || " + arg + " instanceof " + typeName + ") && ";
		return typeStr + (new PropertyASTFlattener(lhs, arg, typeName)).getResult(rhs);
	}
	
	@Override
	public String toString() {
		String typeStr = typeName == null ? "" : ": " + typeName;
		return lhs.toString() + typeStr + " => " + rhs.toString();
	}
	
	private static final class PropertyASTFlattener extends ASTFlattener {
		
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
		protected void flatten(SimpleName node, StringBuilder sb) {
			String nodeId = node.getIdentifier();
			sb.append(nodeId.equals(lhs) ? (type == null ? arg : "((" + type + ")" + arg + ")") : nodeId);
		}
		
	}

}
