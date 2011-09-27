package timeBench.data.relational;

import java.util.Iterator;

import prefuse.data.Graph;
import prefuse.data.Table;
import prefuse.data.column.Column;
import prefuse.data.tuple.TableEdge;
import prefuse.data.tuple.TupleManager;
import timeBench.data.TemporalDataException;
import timeBench.data.util.IntervalComparator;
import timeBench.data.util.IntervalIndex;
import timeBench.data.util.IntervalTreeIndex;

/**
 * This class maintains data structures that encompass a temporal dataset.
 * It consists of a {@link Table} of DataElements and a {@link Graph} of temporal elements.
 * Temporal occurrences of data elements are saved in an additional {@link Table}.
 * Furthermore, the class provides utility methods to index and query the dataset.
 * 
 * <p><b>Warning:</b> If a pre-existing temporal elements table is used, 
 * it needs to provide four columns for inf, sup, kind, and granularity id. 
 * Furthermore, it needs to have {@link TemporalElement} as its tuple type. 
 *  
 * @author bilal
 *
 */
public class TemporalDataset {
	
	private BipartiteGraph graph;
	
	private Graph temporalElements;
	
	private Table dataElements;
	
	public static final String INF = "inf";

	public static final String SUP = "sup";

	public static final String GRANULARITY_ID = "granularityID";

	public static final String KIND = "kind";
	
	/**
	 * Constructs an empty {@link TemporalDataset}
	 */
	public TemporalDataset() {
		this(new Table(), new Table());
		temporalElements.addColumn(INF, long.class);
		temporalElements.addColumn(SUP, long.class);
		temporalElements.addColumn(GRANULARITY_ID, int.class);
		temporalElements.addColumn(KIND, int.class);

		// create specific tuple managers and set them to underlying structures
		// invalidates existing tuples
		TupleManager temporalTuples = new TupleManager(temporalElements.getNodeTable(), temporalElements, TemporalElement.class);
		TupleManager edgeTuples = new TupleManager(temporalElements.getEdgeTable(), temporalElements, TableEdge.class);
		temporalElements.setTupleManagers(temporalTuples, edgeTuples);
		temporalElements.getNodeTable().setTupleManager(temporalTuples);
		temporalElements.getEdgeTable().setTupleManager(edgeTuples);
	}
	
	/**
	 * Constructs a {@link TemporalDataset} with the given data- and temporal-elements 
	 * @param dataElements a {@link Table} containing the data elements
	 * @param temporalElements a {@link Table} containing the temporal elements 
	 */
	public TemporalDataset(Table dataElements, Table temporalElements) {
		this(dataElements , new Graph(temporalElements, true));
	}
		
	/**
	 * Constructs a {@link TemporalDataset} with the given data- and temporal-elements 
	 * @param dataElements a {@link Table} containing the data elements
	 * @param temporalElements a directed {@link Graph} containing the temporal elements   
	 * and how they are related
	 */
	public TemporalDataset(Table dataElements, Graph temporalElements) {
		if (!temporalElements.isDirected()) {
			throw new TemporalDataException("The graph of the temporal elements must be directed");
		}
		this.dataElements = dataElements;
		this.temporalElements = temporalElements;		
		graph = new BipartiteGraph(dataElements, getTemporalElements());
	}
	
	/**
	 * Gets the data elements in the dataset
	 * @return a {@link Table} containing the data elements
	 */
	public Table getDataElements() {
		return dataElements;
	}
	
	/**
	 * Gets the temporal elements in the dataset
	 * @return a {@link Table} containing the temporal elements.
	 */
	public Table getTemporalElements() {
		return temporalElements.getNodeTable();
	}
	
	/**
	 * Gets the temporal elements in the dataset
	 * @return a {@link Graph} containing the temporal elements and how they are related.
	 */
	public Graph getTemporalElementsGraph() {
		return temporalElements;
	}
	
    /**
     * Get the TemporalElement instance corresponding to its id.
     * @param n element id (temporal element table row number)
     * @return the TemporalElement instance corresponding to the node id
     */
	public TemporalElement getTemporalElement(int n) {
	    return (TemporalElement) temporalElements.getNode(n);
	}

    /**
     * Get an iterator over all temporal elements in the temporal dataset.
     * @return an iterator over TemporalElement instances
     */
    @SuppressWarnings("unchecked")
    public Iterator<TemporalElement> temporalElements() {
        return temporalElements.nodes();
    }

    /**
     * allows iteration over all temporal elements. 
     * @return an object, which provides an iterator
     */
    public Iterable<TemporalElement> temporalElementsIterable() {
        return new Iterable<TemporalElement>() {
            @SuppressWarnings("unchecked")
            @Override
            public Iterator<TemporalElement> iterator() {
                return temporalElements.nodes();
            }
        };
    }

	/**
	 * Gets all (temporal) occurrences of data elements
	 * @return a {@link Table} containing all temporal occurrences
	 */
	public Table getOccurrences() {
		return graph.getEdgeTable();
	}
	
	/**
	 * Adds an occurrence of a data element at a given temporal element
	 * @param dataElementInd the index of the data element in the {@link Table} of data elements
	 * @param temporalElementInd the index of the temporal element in the {@link Table} of temporal elements
	 * @return the index of the added occurrence in the {@link Table} of occurrences
	 */
	public int addOccurrence(int dataElementInd, int temporalElementInd) {
		return graph.addEdge(dataElementInd, temporalElementInd);
	}
	
	/**
	 * Creates an {@link IntervalIndex} for the temporal elements. It helps in querying
	 * the elements based on intervals.
	 * @param comparator an {@link IntervalComparator} to compare intervals for indexing any querying purposes.
	 */
	public IntervalIndex createTemporalIndex(IntervalComparator comparator) {
		Table elements = getTemporalElements();
		Column colLo = elements.getColumn(INF);
		Column colHi = elements.getColumn(SUP);
		return new IntervalTreeIndex(elements, elements.rows(), colLo, colHi, comparator);
	}

	/**
	 * Adds a new temporal element to the dataset
	 * @param inf the lower end of the temporal element
	 * @param sup the upper end of the temporal element
	 * @param granularityId the granularityID of the temporal element
	 * @param kind the kind of the temporal element
	 * @return the index of the created element in the table of temporal elements
	 */
	public int addTemporalElement(long inf, long sup, int granularityId, int kind) {
		Table nodeTable = temporalElements.getNodeTable();
		int row = nodeTable.addRow();
		nodeTable.set(row, INF, inf);
		nodeTable.set(row, SUP, sup);
		nodeTable.set(row, GRANULARITY_ID, granularityId);
		nodeTable.set(row, KIND, kind);
		return row;
	}
}
