package codehint.ast;

import org.eclipse.jdt.debug.core.IJavaType;

public class InstanceofExpression extends Expression {
	
	private final Expression lhs;
	private final Type rhs;
	
	public InstanceofExpression(IJavaType staticType, Expression lhs, Type rhs) {
		super(staticType);
		this.lhs = lhs;
		this.rhs = rhs;
	}

	public Expression getLeftOperand() {
		return lhs;
	}

	public Type getRightOperand() {
		return rhs;
	}
	
	@Override
	protected void accept0(ASTVisitor visitor) {
		boolean visitChildren = visitor.visit(this);
		if (visitChildren) {
			acceptChild(visitor, lhs);
			acceptChild(visitor, rhs);
		}
	}
	
	@Override
	public String toString() {
		return lhs.toString() + " instanceof " + rhs.toString(); 
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((lhs == null) ? 0 : lhs.hashCode());
		result = prime * result + ((rhs == null) ? 0 : rhs.hashCode());
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
		InstanceofExpression other = (InstanceofExpression) obj;
		if (lhs == null) {
			if (other.lhs != null)
				return false;
		} else if (!lhs.equals(other.lhs))
			return false;
		if (rhs == null) {
			if (other.rhs != null)
				return false;
		} else if (!rhs.equals(other.rhs))
			return false;
		return true;
	}

}
