package codehint.ast;

import java.util.Arrays;

import codehint.utils.Utils;

public class ParameterizedType extends Type {
	
	private final Type type;
	private final Type[] typeArguments;
	
	public ParameterizedType(Type type, Type[] typeArguments) {
		this.type = type;
		this.typeArguments = typeArguments;
	}

	public Type getType() {
		return type;
	}

	public Type[] getTypeArguments() {
		return typeArguments;
	}
	
	@Override
	protected void accept0(ASTVisitor visitor) {
		boolean visitChildren = visitor.visit(this);
		if (visitChildren) {
			acceptChild(visitor, type);
			acceptChildren(visitor, typeArguments);
		}
	}

	@Override
	public String toString() {
		return type.toString() + "<" + Utils.arrayToString(typeArguments) + ">";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
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
		ParameterizedType other = (ParameterizedType) obj;
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
