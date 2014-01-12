package codehint.exprgen.weightedlist;

/**
 * A list that supports a randomized get operation that
 * gets elements according to their associated weight.
 * The list does not support removing elements, as the
 * data structure it currently supports makes that expensive,
 * and it is not needed for our purpose.
 * @param <T> The type of elements in the list.
 */
public interface IWeightedList<T> {

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