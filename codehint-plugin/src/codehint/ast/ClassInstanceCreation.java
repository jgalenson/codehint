package codehint.ast;

import java.util.Arrays;

import codehint.utils.Utils;

public class ClassInstanceCreation extends Expression {
	
	private final Expression expression;
	private final Type type;
	private final Expression[] arguments;
	private final Expression[] typeArguments;
	
	public ClassInstanceCreation(Expression expression, Type type, Expression[] arguments, Expression[] typeArguments) {
		this.expression = expression;
		this.type = type;
		this.arguments = arguments;
		this.typeArguments = typeArguments;
	}
	
	public ClassInstanceCreation(Type type, Expression[] arguments) {
		this(null, type, arguments, null);
	}
	
	public Expression getExpression() {
		return expression;
	}

	public Type getType() {
		return type;
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
			acceptChild(visitor, type);
			acceptChildren(visitor, arguments);
			acceptChildren(visitor, typeArguments);
		}
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (expression != null)
			sb.append(expression.toString()).append(".");
		sb.append("new ");
		if (typeArguments != null && typeArguments.length > 0)
			sb.append("<").append(Utils.arrayToString(typeArguments)).append(">");
		sb.append(type.toString());
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
		result = prime * result + ((type == null) ? 0 : type.hashCode());
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
		ClassInstanceCreation other = (ClassInstanceCreation) obj;
		if (!Arrays.equals(arguments, other.arguments))
			return false;
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
			return false;
		if (!Arrays.equals(typeArguments, other.typeArguments))
			return false;
		return true;
	}

}
