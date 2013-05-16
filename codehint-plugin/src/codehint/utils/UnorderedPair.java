package codehint.utils;

public class UnorderedPair<T1, T2> extends Pair<T1, T2> {

	public UnorderedPair(T1 first, T2 second) {
		super(first, second);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = prime * ((first == null) ? 0 : first.hashCode()) + prime * ((second == null) ? 0 : second.hashCode());
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
		Pair<?, ?> other = (Pair<?, ?>) obj;
		return (equals(first, other.first) && equals(second, other.second)) || (equals(first, other.second) && equals(second, other.first));
	}
	
	private static <T1, T2> boolean equals(T1 x, T2 y) {
		return x == null ? y == null : x.equals(y);
	}

}
