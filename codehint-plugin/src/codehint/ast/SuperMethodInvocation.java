package codehint.ast;

import java.util.Arrays;

import org.eclipse.jdt.debug.core.IJavaType;

import codehint.utils.Utils;

public class SuperMethodInvocation extends Expression {

	private final Name qualifier;
	private final SimpleName name;
	private final Expression[] arguments;
	private final Expression[] typeArguments;
	
	public SuperMethodInvocation(IJavaType staticType, Name qualifier, SimpleName name, Expression[] arguments, Expression[] typeArguments) {
		super(staticType);
		this.qualifier = qualifier;
		this.name = name;
		this.arguments = arguments;
		this.typeArguments = typeArguments;
	}
	
	public SuperMethodInvocation(IJavaType staticType, Name qualifier, SimpleName name, Expression[] arguments) {
		this(staticType, qualifier, name, arguments, null);
	}
	
	public Name getQualifier() {
		return qualifier;
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
			acceptChild(visitor, qualifier);
			acceptChild(visitor, name);
			acceptChildren(visitor, arguments);
			acceptChildren(visitor, typeArguments);
		}
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("super.");
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
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((qualifier == null) ? 0 : qualifier.hashCode());
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
		SuperMethodInvocation other = (SuperMethodInvocation) obj;
		if (!Arrays.equals(arguments, other.arguments))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (qualifier == null) {
			if (other.qualifier != null)
				return false;
		} else if (!qualifier.equals(other.qualifier))
			return false;
		if (!Arrays.equals(typeArguments, other.typeArguments))
			return false;
		return true;
	}

}
