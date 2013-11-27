package codehint.ast;

import java.util.Arrays;

public class ArrayCreation extends Expression {

	private final Expression[] dimensions;
	private final ArrayInitializer initializer;
	private final ArrayType type;
	
	public ArrayCreation(Expression[] dimensions, ArrayInitializer initializer, ArrayType type) {
		this.dimensions = dimensions;
		this.initializer = initializer;
		this.type = type;
	}

	public Expression[] dimensions() {
		return dimensions;
	}

	public ArrayInitializer getInitializer() {
		return initializer;
	}

	public ArrayType getType() {
		return type;
	}
	
	@Override
	protected void accept0(ASTVisitor visitor) {
		boolean visitChildren = visitor.visit(this);
		if (visitChildren) {
			acceptChildren(visitor, dimensions);
			acceptChild(visitor, initializer);
			acceptChild(visitor, type);
		}
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("new ");
		sb.append(type.toString());
		for (Expression dim: dimensions)
			sb.append("[").append(dim.toString()).append("]");
		for (int i = 0; i < type.getDimensions(); i++)
			sb.append("[]");
		if (initializer != null)
			sb.append(initializer.toString());
		return sb.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(dimensions);
		result = prime * result + ((initializer == null) ? 0 : initializer.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
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
		ArrayCreation other = (ArrayCreation) obj;
		if (!Arrays.equals(dimensions, other.dimensions))
			return false;
		if (initializer == null) {
			if (other.initializer != null)
				return false;
		} else if (!initializer.equals(other.initializer))
			return false;
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
			return false;
		return true;
	}

}
