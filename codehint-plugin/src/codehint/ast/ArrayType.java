package codehint.ast;

public class ArrayType extends Type {
	
	private final Type componentType;

	public ArrayType(Type type) {
		this.componentType = type;
	}

	public Type getComponentType() {
		return componentType;
	}

	/**
	 * Returns the element type of this array type.
	 * The element type is the outermost non-array type,
	 * so this simple iterates down the chain until
	 * it is reached.
	 * @return the component type node
	 */
	public Type getElementType() {
		Type t = componentType;
		while (t instanceof ArrayType)
			t = ((ArrayType)t).getComponentType();
		return t;
	}

	/**
	 * Returns the number of dimensions in this array type.
	 * An individual array type only contains one level of nesting,
	 * so this simply counts the number of children that are
	 * also array types.
	 * @return the number of dimensions
	 */
	public int getDimensions() {
		int dimensions = 1;
		for (Type t = componentType; t instanceof ArrayType; ((ArrayType)t).getComponentType())
			dimensions++;
		return dimensions;
	}
	
	@Override
	protected void accept0(ASTVisitor visitor) {
		boolean visitChildren = visitor.visit(this);
		if (visitChildren)
			acceptChild(visitor, componentType);
	}
	
	@Override
	public String toString() {
		return componentType.toString() + "[]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((componentType == null) ? 0 : componentType.hashCode());
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
		ArrayType other = (ArrayType) obj;
		if (componentType == null) {
			if (other.componentType != null)
				return false;
		} else if (!componentType.equals(other.componentType))
			return false;
		return true;
	}

}
