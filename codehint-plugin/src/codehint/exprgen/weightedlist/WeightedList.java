package codehint.exprgen.weightedlist;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/**
 * A class that represents a weighted list of elements.
 * These elements can be extracted with probability
 * proportional to their weights.
 *
 * @param <T> The type of the elements in the list.
 */
public class WeightedList<T> extends AbstractList<T> implements IWeightedList<T> {
	
	protected final ArrayList<T> elems;
	protected final ArrayList<Double> cumulativeWeights;
	protected double totalWeight;
	protected static final Random random = new Random();
	
	/**
	 * Creates an empty list.
	 */
	public WeightedList() {
		this.elems = new ArrayList<T>();
		this.cumulativeWeights = new ArrayList<Double>();
		totalWeight = 0d;
	}
	
	public WeightedList(T[] elems, double[] weights) {
		this();
		for (int i = 0; i < elems.length; i++)
			addWeighted(elems[i], weights[i]);
	}
	
	public void addWeighted(T elem, double weight) {
		elems.add(elem);
		totalWeight += weight;
		cumulativeWeights.add(totalWeight);
	}
	
	/* (non-Javadoc)
	 * @see codehint.exprgen.weightedlist.WeightedList#getWeighted()
	 */
	@Override
	public T getWeighted() {
		if (elems.isEmpty())
			return null;
		double rand = random.nextDouble() * totalWeight;
		int index = Collections.binarySearch(cumulativeWeights, rand);
		if (index < 0)  // If we happen to pick a number in the list, we use it.
			index = -index - 1;
		return elems.get(index);
	}

	/* (non-Javadoc)
	 * @see codehint.exprgen.weightedlist.WeightedList#getTotalWeight()
	 */
	@Override
	public double getTotalWeight() {
		return totalWeight;
	}
	
	/* (non-Javadoc)
	 * @see codehint.exprgen.weightedlist.WeightedList#size()
	 */
	@Override
	public int size() {
		return elems.size();
	}
	
	/**
	 * Gets the element at the specified index,
	 * ignoring the weights.
	 * @param index The index.
	 * @return The element stored at the specified
	 * index, ignoring the weights.
	 */
	@Override
	public T get(int index) {
		return elems.get(index);
	}
	
	@Override
	public String toString() {
		int numElems = elems.size();
		if (numElems == 0)
			return "";
		StringBuilder sb = new StringBuilder();
		double cumulativeWeight = 0d;
		for (int i = 0; true; i++) {
			double curCumulativeWeight = cumulativeWeights.get(i);
			sb.append(elems.get(i)).append("(").append(curCumulativeWeight - cumulativeWeight).append(")");
			if (i == numElems - 1)
				break;
			cumulativeWeight = curCumulativeWeight;
			sb.append(", ");
		}
		return sb.toString();
	}

}
