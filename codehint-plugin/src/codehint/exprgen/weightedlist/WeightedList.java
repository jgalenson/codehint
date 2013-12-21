package codehint.exprgen.weightedlist;

public interface WeightedList<T> {

	/**
	 * Adds the given element to the list.
	 * @param elem The element to add.
	 */
	public abstract void addWeighted(T elem);

	/**
	 * Gets an element from the list according to
	 * the weights of the elements.
	 * @return An element chosen from this list
	 * according to the weights.
	 */
	public abstract T getWeighted();

	/**
	 * Gets the size of the list.
	 * @return The size of the list.
	 */
	public abstract int size();
	
	/**
	 * Gets the total weight of all elements in this list.
	 * @return The total weight of all elements in this list.
	 */
	public abstract double getTotalWeight();

}