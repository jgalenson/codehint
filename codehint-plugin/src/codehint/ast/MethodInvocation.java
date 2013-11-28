package codehint.ast;

import java.util.Arrays;

import org.eclipse.jdt.debug.core.IJavaType;

import codehint.utils.Utils;

public class MethodInvocation extends Expression {
	
	private final Expression expression;
	private final SimpleName name;
	private final Expression[] arguments;
	private final Expression[] typeArguments;
	
	public MethodInvocation(IJavaType staticType, Expression expression, SimpleName name, Expression[] arguments, Expression[] typeArguments) {
		super(staticType);
		this.expression = expression;
		this.name = name;
		this.arguments = arguments;
		this.typeArguments = typeArguments;
	}
	
	public MethodInvocation(IJavaType staticType, Expression expression, SimpleName name, Expression[] arguments) {
		this(staticType, expression, name, arguments, null);
	}

	public Expression getExpression() {
		return expression;
	}

	public SimpleName getName() {
		return name;
	}

	public Expression[] arguments() {
		return arguments;
	}

	public Expression[] typeArguments() {
		return typeArguments;
	}
	
	@Override
	protected void accept0(ASTVisitor visitor) {
		boolean visitChildren = visitor.visit(this);
		if (visitChildren) {
			acceptChild(visitor, expression);
			acceptChild(visitor, name);
			acceptChildren(visitor, arguments);
			acceptChildren(visitor, typeArguments);
		}
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (expression != null)
			sb.append(expression.toString()).append(".");
		if (typeArguments != null && typeArguments.length > 0)
			sb.append("<").append(Utils.arrayToString(typeArguments)).append(">");
		sb.append(name.toString());
		sb.append("(");
		sb.append(Utils.arrayToString(arguments));
		sb.append(")");
		return sb.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(arguments);
		result = prime * result + ((expression == null) ? 0 : expression.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + Arrays.hashCode(typeArguments);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MethodInvocation other = (MethodInvocation) obj;
		if (!Arrays.equals(arguments, other.arguments))
			return false;
		if (expression == null) {
			if (other.expression != null)
				return false;
		} else if (!expression.equals(other.expression))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (!Arrays.equals(typeArguments, other.typeArguments))
			return false;
		return true;
	}

}
