package codehint.ast;

import java.util.Arrays;

import org.eclipse.jdt.debug.core.IJavaType;

import codehint.utils.Utils;

public class ArrayInitializer extends Expression {
	
	private final Expression[] expressions;
	
	public ArrayInitializer(IJavaType staticType, Expression[] expressions) {
		super(staticType);
		this.expressions = expressions;
	}

	public Expression[] expressions() {
		return expressions;
	}
	
	@Override
	protected void accept0(ASTVisitor visitor) {
		boolean visitChildren = visitor.visit(this);
		if (visitChildren)
			acceptChildren(visitor, expressions);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		sb.append(Utils.arrayToString(expressions));
		sb.append("}");
		return sb.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(expressions);
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
		ArrayInitializer other = (ArrayInitializer) obj;
		if (!Arrays.equals(expressions, other.expressions))
			return false;
		return true;
	}

}
