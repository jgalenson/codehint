package codehint.exprgen.weightedlist;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A class that represents a weighted list of elements.
 * These elements can be extracted with probability
 * proportional to their weights.
 *
 * @param <T> The type of the elements in the list.
 */
public abstract class AbstractWeightedList<T> extends AbstractList<T> implements WeightedList<T> {
	
	private final ArrayList<T> elems;
	private final ArrayList<Double> cumulativeWeights;
	private double totalWeight;
	private static final Random random = new Random();
	
	/**
	 * Creates an empty list.
	 */
	protected AbstractWeightedList() {
		elems = new ArrayList<T>();
		cumulativeWeights = new ArrayList<Double>();
		totalWeight = 0d;
	}

	/**
	 * Computes the weight of elements in the list.
	 * @param elem An element.
	 * @return The weight of the element.
	 */
	public abstract double getWeight(T elem);
	
	/* (non-Javadoc)
	 * @see codehint.exprgen.weightedlist.WeightedList#addWeighted(T)
	 */
	@Override
	public void addWeighted(T elem) {
		elems.add(elem);
		totalWeight += getWeight(elem);
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
	
	/**
	 * Adds all the given elements to this list.
	 * @param elems The elements to add.
	 */
	public void addAllWeighted(List<T> elems) {
		for (T elem: elems)
			addWeighted(elem);
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
