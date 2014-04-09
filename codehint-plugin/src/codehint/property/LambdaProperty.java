package codehint.property;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import codehint.ast.ASTFlattener;
import codehint.ast.Expression;
import codehint.ast.SimpleName;

/**
 * Class that stores a user-entered property (essentially
 * a lambda).
 */
// TODO: We could store the value entered in the concrete cases and then compare against it directly rather than with the EvaluationEngine.q
public abstract class LambdaProperty extends Property {
	
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
	public boolean usesLHS() {
		return true;
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
