package timeBench.data;

import ieg.util.lang.CustomIterable;

import prefuse.data.Graph;
import prefuse.data.Schema;
import prefuse.data.Table;
import prefuse.data.column.Column;
import prefuse.data.expression.Predicate;
import prefuse.data.tuple.TableEdge;
import prefuse.data.tuple.TupleManager;
import prefuse.data.util.Index;
import prefuse.util.collections.IntIterator;
import timeBench.calendar.Granule;
import timeBench.data.expression.AnchoredPredicate;
import timeBench.data.util.DefaultIntervalComparator;
import timeBench.data.util.IntervalComparator;
import timeBench.data.util.IntervalIndex;
import timeBench.data.util.IntervalTreeIndex;

/**
 * This class maintains data structures that encompass a temporal dataset. It
 * consists of a {@link Graph} of temporal objects and a {@link Graph} of
 * temporal elements. Temporal elements are in a 1:n relation with temporal
 * objects to encompass temporal occurrence. Furthermore, the class provides
 * utility methods to index and query the dataset.
 * 
 * @author BA, AR, TL
 * 
 */
public class TemporalDataset extends Graph implements Lifespan, Cloneable {

    /**
     * {@link Graph} of temporal elements
     */
    private Graph temporalElements;

    // TODO roots as a linked list?
    private long[] roots = null; // if we have a forest or tree of temporal
                                 // objects, null for tables

    /**
     * tuple manager for primitives (e.g., {@link Instance})
     */
    private TemporalElementManager temporalPrimitives;
    /**
     * tuple manager for {@link GenericTemporalElement}s)
     */
    private TemporalElementManager temporalGenerics;

    /**
     * index for {@link TemporalObject} row numbers by {@link TemporalObject#ID}. 
     */
    private Index indexObjects;

    /**
     * index for {@link TemporalElement} row numbers by {@link TemporalElement#ID}. 
     */
    private Index indexElements;

    /**
     * index for {@link TemporalObject} row numbers by {@link TemporalElement#ID}. 
     */
    private Index indexObjectsByElements;
    
    /**
     * interval index for anchored {@link TemporalElement}s.
     * Initialized on demand by {@link #intervalIndex()}.
     */
    private IntervalIndex indexElementIntervals = null;
    
    /**
     * additional data fields of {@link TemporalObject}s.
     */
    private Schema dataColumns;
    
    /**
     * Constructs an empty {@link TemporalDataset}
     */
    public TemporalDataset() {
        // temporal objects are by default in an directed graph
        super(true);

        this.temporalElements = new Graph(true);
        // define temporal element columns for nodes of the temporal e. graph
        this.temporalElements.getNodeTable().addColumns(
                this.getTemporalElementSchema());

        // add temporal objects columns (primary and foreign key)
        super.addColumn(TemporalObject.ID, long.class, -1);
        super.addColumn(TemporalObject.TEMPORAL_ELEMENT_ID, long.class, -1);
        
        this.dataColumns = new Schema();

        // add indices
        this.indexObjects = super.getNodeTable().index(TemporalObject.ID);
        this.indexObjectsByElements = super.getNodeTable().index(
                TemporalObject.TEMPORAL_ELEMENT_ID);
        this.indexElements = this.temporalElements.getNodeTable().index(
                TemporalElement.ID);

        initTupleManagers();
    }

    /**
     * Constructs an empty {@link TemporalDataset} with the given schema for
     * data elements.
     * 
     * @param dataColumns
     *            schema for data elements
     * @throws TemporalDataException
     *             if a reserved column name was passed
     */
    public TemporalDataset(Schema dataColumns) throws TemporalDataException {
        this();

        // for loop mimics behavior of table.addColumns(schema)
        for (int i = 0; i < dataColumns.getColumnCount(); ++i) {
            this.addDataColumn(dataColumns.getColumnName(i),
                    dataColumns.getColumnType(i), dataColumns.getDefault(i));
        }
    }

    /**
     * Add a data column with the given name and data type to the temporal
     * objects.
     * 
     * @param name
     *            the data field name for the column
     * @param type
     *            the data type, as a Java Class, for the column
     * @param defaultValue
     *            the default value for column data values or <tt>null</tt>
     * @throws TemporalDataException
     *             if a reserved column name was passed
     * @see prefuse.data.tuple.TupleSet#addColumn(java.lang.String,
     *      java.lang.Class, java.lang.Object)
     */
    public void addDataColumn(String name,
            @SuppressWarnings("rawtypes") Class type, Object defaultValue)
            throws TemporalDataException {
        // check that schema does not interfere with primary and foreign key
        // schema.getColumnIndex(s) would build a HashMap --> less efficient
        if (name.equals(TemporalObject.ID)
                || name.equals(TemporalObject.TEMPORAL_ELEMENT_ID)) {
            throw new TemporalDataException("The column names \""
                    + TemporalObject.ID + "\" and \""
                    + TemporalObject.TEMPORAL_ELEMENT_ID + "\" are reserved.");
        }

        super.getNodeTable().addColumn(name, type, defaultValue);
        
        this.dataColumns.addColumn(name, type, defaultValue);
    }
    
    public Schema getDataColumnSchema() {
        return (Schema) dataColumns.clone();
    }
    
    public int getDataColumnCount() {
        return dataColumns.getColumnCount();
    }
    
    public Column getDataColumn(int index) {
        String fieldName = dataColumns.getColumnName(index);
        return super.getNodeTable().getColumn(fieldName);
    }

    /**
     * Set tuple managers for temporal elements, temporal primitives, and
     * temporal objects and use them in the underlying data structures.
     * 
     * <p>
     * This method is called from all constructors and will cause all existing
     * Tuples retrieved from this dataset to be invalidated.
     */
    private void initTupleManagers() {
        // nodes of temporal object graph --> TemporalObject
        TupleManager tempObjectManager = new TupleManager(
                super.getNodeTable(), this, TemporalObject.class);

        // nodes of temporal element graph --> GenericTemporalElement
        this.temporalGenerics = new TemporalElementManager(this, true);

        // additional tuple manager for temporal element graph --> temporal
        // primitives
        this.temporalPrimitives = new TemporalElementManager(this, false);
        this.temporalPrimitives.invalidateAutomatically();

        // dummy manager for edges in both graphs
        TupleManager tempObjectEdgeManager = new TupleManager(
                this.getEdgeTable(), this, TableEdge.class);
        TupleManager tempElementEdgeManager = new TupleManager(
                temporalElements.getEdgeTable(), temporalElements,
                TableEdge.class);

        // assign to temporal object graph
        super.setTupleManagers(tempObjectManager, tempObjectEdgeManager);
        super.getNodeTable().setTupleManager(tempObjectManager);
        super.getEdgeTable().setTupleManager(tempObjectEdgeManager);

        // assign to temporal element graph
        temporalElements.setTupleManagers(temporalGenerics,
                tempElementEdgeManager);
        temporalElements.getNodeTable().setTupleManager(temporalGenerics);
        temporalElements.getEdgeTable().setTupleManager(tempElementEdgeManager);
    }

    @Deprecated
    public TemporalDataset clone() {
        throw new UnsupportedOperationException("clone no longer needed");
    }

    /**
     * Gets the roots if the TemporalObjects form a wood, the root, if they form
     * a tree, or null for tables
     * 
     * @return the roots
     */
    public long[] getRoots() {
        return roots;
    }

    /**
     * Sets the roots: If the TemporalObjects form a wood use the roots, if they
     * form a tree, use the root, for tables set null
     * 
     * @param roots
     *            the roots to set
     */
    public void setRoots(long[] roots) {
        this.roots = roots;
    }

    // ----- TEMPORAL ELEMENT ACCESSORS -----

    /**
     * Gets the temporal elements in the dataset
     * 
     * @return a {@link Graph} containing the temporal elements and how they are
     *         related.
     */
    public Graph getTemporalElements() {
        return temporalElements;
    }

    /**
     * Get the number of temporal elements in this dataset.
     * 
     * @return the number of temporal elements
     */
    public int getTemporalElementCount() {
        return temporalElements.getNodeCount();
    }

    /**
     * Get the TemporalElement instance corresponding to its row number.
     * 
     * @param row
     *            temporal element table row number
     * @return the TemporalElement instance corresponding to the row number
     */
    public GenericTemporalElement getTemporalElementByRow(int row) {
        return (GenericTemporalElement) temporalElements.getNode(row);
    }

    /**
     * Get the TemporalElement instance corresponding to its id, or
     * <tt>null</tt> if this dataset contains no element for the id.
     * 
     * @param id
     *            element id
     * @return the TemporalElement instance corresponding to the element id, or
     *         <tt>null</tt> if this dataset contains no element for the id.
     */
    public GenericTemporalElement getTemporalElement(long id) {
        int row = this.indexElements.get(id);
        return (row == Integer.MIN_VALUE) ? null : getTemporalElementByRow(row);
    }

    /**
     * Get the temporal primitive corresponding to its row number.
     * 
     * @param row
     *            temporal element table row number
     * @return the temporal primitive corresponding to the row number
     */
    public TemporalElement getTemporalPrimitiveByRow(int row) {
        return (TemporalElement) this.temporalPrimitives.getTuple(row);
    }

    /**
     * Get the temporal primitive corresponding to its id, or <tt>null</tt> if
     * this dataset contains no primitive for the id.
     * 
     * @param n
     *            element id
     * @return the temporal primitive corresponding to the element id, or
     *         <tt>null</tt> if this dataset contains no primitive for the id.
     */
    public TemporalElement getTemporalPrimitive(long id) {
        int row = this.indexElements.get(id);
        return (row == Integer.MIN_VALUE) ? null
                : getTemporalPrimitiveByRow(row);
    }

    /**
     * Get an iterator over all temporal elements in the temporal dataset.
     * 
     * @return an object, which provides an iterator over TemporalElement
     *         instances
     */
    @SuppressWarnings("unchecked")
    public Iterable<GenericTemporalElement> temporalElements() {
        return new CustomIterable(temporalElements.nodes());
    }

    /**
     * Get an iterator over {@link TemporalElement}s in the temporal dataset,
     * filtered by the given predicate.
     * 
     * @param filter
     *            predicate to apply to tuples in this set, only tuples for
     *            which the predicate evaluates to true are included in the
     *            iteration
     * @return an object, which provides an iterator over TemporalElement
     *         instances
     */
    @SuppressWarnings("unchecked")
    public Iterable<GenericTemporalElement> temporalElements(Predicate filter) {
        return new CustomIterable(temporalElements.getNodeTable()
                .tuples(filter));
    }

    /**
     * Get an iterator over all temporal primitives in the temporal dataset.
     * 
     * @return an object, which provides an iterator over TemporalElement
     *         instances
     */
    @SuppressWarnings("unchecked")
    public Iterable<TemporalElement> temporalPrimitives() {
        return new CustomIterable(temporalPrimitives.iterator(temporalElements
                .nodeRows()));
    }

    /**
     * Get an iterator over temporal primitives in the temporal dataset,
     * filtered by the given predicate.
     * 
     * @param filter
     *            predicate to apply to tuples in this set, only tuples for
     *            which the predicate evaluates to true are included in the
     *            iteration
     * @return an object, which provides an iterator over TemporalElement
     *         instances
     */
    @SuppressWarnings("unchecked")
    public Iterable<GenericTemporalElement> temporalPrimitives(Predicate filter) {
        IntIterator iit = temporalElements.getNodeTable().rows(filter);
        return new CustomIterable(temporalPrimitives.iterator(iit));
    }

    // ----- TEMPORAL OBJECT ACCESSORS -----

    /**
     * Get the number of temporal objects in this dataset.
     * 
     * @return the number of temporal objects
     */
    public int getTemporalObjectCount() {
        return super.getNodeCount();
    }

    /**
     * Get an iterator over all {@link TemporalObject}s in the temporal dataset.
     * 
     * @return an object, which provides an iterator over TemporalObject
     *         instances
     */
    @SuppressWarnings("unchecked")
    public Iterable<TemporalObject> temporalObjects() {
        return new CustomIterable(super.nodes());
    }

    /**
     * Get an iterator over {@link TemporalObject}s in the temporal dataset,
     * filtered by the given predicate.
     * 
     * @param filter
     *            predicate to apply to tuples in this set, only tuples for
     *            which the predicate evaluates to true are included in the
     *            iteration
     * @return an object, which provides an iterator over TemporalObject
     *         instances
     */
    @SuppressWarnings("unchecked")
    public Iterable<TemporalObject> temporalObjects(Predicate filter) {
        return new CustomIterable(super.getNodeTable().tuples(filter));
    }

    /**
     * Get the {@link TemporalObject} instance corresponding to its id, or
     * <tt>null</tt> if this dataset contains no object for the id.
     * 
     * @param id
     *            object id
     * @return the TemporalObject instance corresponding to the object id, or
     *         <tt>null</tt> if this dataset contains no object for the id.
     */
    public TemporalObject getTemporalObject(long id) {
        int row = this.indexObjects.get(id);
        return (row == Integer.MIN_VALUE) ? null : 
            (TemporalObject) super.getNode(row);
    }

    /**
     * Get an iterator over all {@link TemporalObject}s occurring with the given
     * temporal element.
     * 
     * @param temporalId
     *            temporal element id
     * @return an object, which provides an iterator over temporal objects
     *         occurring with the temporal element
     */
    @SuppressWarnings("unchecked")
    public Iterable<TemporalObject> getTemporalObjectsByElementId(
            long temporalId) {
        IntIterator rows = this.indexObjectsByElements.rows(temporalId);
        return new CustomIterable(super.getNodeTable().tuples(rows));
    }

    /**
     * Gets all (temporal) occurrences of data elements
     * 
     * @return a {@link Table} containing all temporal occurrences
     * 
     *         TL 2011-11-07: not deprecated because needed to generate
     *         VisualTable
     *         AR 2012-04-03: renamed to getTemporalObjectTable()
     */
    public Table getTemporalObjectTable() {
        return super.getNodeTable();
    }

    /**
     * Adds a temporal object to the temporal dataset.
     * 
     * @param temporalObjectId
     *            the id of the temporal object
     * @param temporalElementId
     *            the id of the temporal element
     * @return the proxy tuple of the new temporal object.
     */
    public TemporalObject addTemporalObject(long temporalObjectId,
            long temporalElementId) {
//        if (this.indexObjects.get(temporalObjectId) != Integer.MIN_VALUE)
//            throw new TemporalDataException("Duplicate temporal object id");
//        if (this.indexElements.get(temporalElementId) == Integer.MIN_VALUE)
//            throw new TemporalDataException(
//                    "Temporal element id does not exist");

        TemporalObject object = (TemporalObject) super.addNode();
        object.set(TemporalObject.ID, temporalObjectId);
        object.set(TemporalObject.TEMPORAL_ELEMENT_ID, temporalElementId);
        return object;
    }

    /**
     * Adds a temporal object to the temporal dataset.
     * 
     * @param temporalElementId
     *            the id of the temporal element
     * @return the proxy tuple of the new temporal object.
     */
    public TemporalObject addTemporalObject(long temporalElementId) {
        long id = (indexObjects.size() > 0) ? super.getNodeTable().getLong(
                this.indexObjects.maximum(), TemporalObject.ID) + 1 : 1;

        return addTemporalObject(id, temporalElementId);
    }

    /**
     * Adds a temporal object to the temporal dataset.
     * 
     * @param temporalElement
     *            the temporal element
     * @return the proxy tuple of the new temporal object.
     */
    public TemporalObject addTemporalObject(TemporalElement temporalElement) {
        return addTemporalObject(temporalElement.getId());
    }

    /**
     * Create (if necessary) and return the {@link IntervalIndex} for
     * {@link TemporalElement}s. It helps in querying the elements based on
     * intervals. The first call to this method will cause the index to be
     * created and stored. Subsequent calls will simply return the stored index.
     * To attempt to retrieve an index without triggering creation of a new
     * index, use the {@link #getIntervalIndex()} method.
     * 
     * @return the interval index
     */
    public IntervalIndex intervalIndex() {
        if (indexElementIntervals != null) {
            return indexElementIntervals; // already indexed
        }

        Table elements = this.temporalElements.getNodeTable();
        Column colLo = elements.getColumn(TemporalElement.INF);
        Column colHi = elements.getColumn(TemporalElement.SUP);
        IntIterator rows = elements.rows(new AnchoredPredicate());
        IntervalComparator comparator = new DefaultIntervalComparator();
        indexElementIntervals = new IntervalTreeIndex(elements, rows, colLo,
                colHi, comparator);

        // TODO keep interval index up-to-date
        
        // TODO predicate to take advantage of interval index
        
        return indexElementIntervals;
    }

    /**
     * Retrieve, without creating, the interval index for
     * {@link TemporalElement}s.
     * 
     * @return the stored interval index , or null if no index has been created
     */
    public IntervalIndex getIntervalIndex() {
        return indexElementIntervals;
    }
    
    /**
     * Adds a new temporal element to the dataset but does not return a proxy
     * tuple.
     * 
     * @param inf
     *            the lower end of the temporal element
     * @param sup
     *            the upper end of the temporal element
     * @param granularityId
     *            the granularityID of the temporal element
     * @param granularityContextId
     *            the granularityContextID of the temporal element
     * @param kind
     *            the kind of the temporal element
     * @return the index of the created element in the table of temporal
     *         elements
     */
    private int addTemporalElementAsRow(long inf, long sup, int granularityId,
            int granularityContextId, int kind) {
        long id = (indexElements.size() > 0) ? temporalElements.getNodeTable()
                .getLong(this.indexElements.maximum(), TemporalElement.ID) + 1
                : 1;
        return addTemporalElementAsRow(id, inf, sup, granularityId,
                granularityContextId, kind);
    }

    /**
     * Adds a new temporal element to the dataset but does not return a proxy
     * tuple.
     * 
     * @param id
     *            the id of the temporal element
     * @param inf
     *            the lower end of the temporal element
     * @param sup
     *            the upper end of the temporal element
     * @param granularityId
     *            the granularityID of the temporal element
     * @param granularityContextId
     *            the granularityContextID of the temporal element
     * @param kind
     *            the kind of the temporal element
     * @return the index of the created element in the table of temporal
     *         elements
     */
    private int addTemporalElementAsRow(long id, long inf, long sup,
            int granularityId, int granularityContextId, int kind) {
        Table nodeTable = temporalElements.getNodeTable();
        int row = nodeTable.addRow();
        nodeTable.set(row, TemporalElement.ID, id);
        nodeTable.set(row, TemporalElement.INF, inf);
        nodeTable.set(row, TemporalElement.SUP, sup);
        nodeTable.set(row, TemporalElement.GRANULARITY_ID, granularityId);
        nodeTable.set(row, TemporalElement.GRANULARITY_CONTEXT_ID, granularityContextId);
        nodeTable.set(row, TemporalElement.KIND, kind);

        // only proxy tuple is GenericTemporalElement -> no need to invalidate

        return row;
    }

    /**
     * Adds a new temporal element to the dataset
     * 
     * @param inf
     *            the lower end of the temporal element
     * @param sup
     *            the upper end of the temporal element
     * @param granularityId
     *            the granularityID of the temporal element
     * @param granularityContextId
     *            the granularityContextID of the temporal element
     * @param kind
     *            the kind of the temporal element
     * @return the created temporal element
     */
    public GenericTemporalElement addTemporalElement(long inf, long sup,
            int granularityId, int granularityContextId, int kind) {
        int row = addTemporalElementAsRow(inf, sup, granularityId,
                granularityContextId, kind);
        return (GenericTemporalElement) this.temporalGenerics.getTuple(row);

    }

    /**
     * Adds a new temporal element to the dataset
     * 
     * @param id
     *            the id of the temporal element
     * @param inf
     *            the lower end of the temporal element
     * @param sup
     *            the upper end of the temporal element
     * @param granularityId
     *            the granularityID of the temporal element
     * @param granularityContextId
     *            the granularityContextID of the temporal element
     * @param kind
     *            the kind of the temporal element
     * @return the created temporal element
     */
    public GenericTemporalElement addTemporalElement(long id, long inf,
            long sup, int granularityId, int granularityContextId, int kind) {
        int row = addTemporalElementAsRow(inf, sup, granularityId,
                granularityContextId, kind);
        return (GenericTemporalElement) this.temporalGenerics.getTuple(row);

    }
    
    /**
     * Add a new instant to the dataset. This method returns a proxy tuple of
     * this instant, which is of class {@link Instant}.
     * 
     * @param inf
     *            the lower end of the temporal element
     * @param sup
     *            the upper end of the temporal element
     * @param granularityId
     *            the granularityID of the temporal element
     * @param granularityContextId
     *            the granularityContextID of the temporal element
     * @return a proxy tuple of the created temporal element
     */
    public Instant addInstant(long inf, long sup, int granularityId,
            int granularityContextId) {
        int row = this.addTemporalElementAsRow(inf, sup, granularityId,
                granularityContextId, TemporalDataset.PRIMITIVE_INSTANT);
        Instant result = (Instant) this.temporalPrimitives.getTuple(row);
        return result;
    }

    /**
     * Add a new instant to the dataset. This method returns a proxy tuple of
     * this instant, which is of class {@link Instant}.
     * 
     * @param id
     *            the id of the temporal element
     * @param inf
     *            the lower end of the temporal element
     * @param sup
     *            the upper end of the temporal element
     * @param granularityId
     *            the granularityID of the temporal element
     * @param granularityContextId
     *            the granularityContextID of the temporal element
     * @return a proxy tuple of the created temporal element
     */
    public Instant addInstant(long id, long inf, long sup, int granularityId,
            int granularityContextId) {
        int row = this.addTemporalElementAsRow(id, inf, sup, granularityId,
                granularityContextId, TemporalDataset.PRIMITIVE_INSTANT);
        Instant result = (Instant) this.temporalPrimitives.getTuple(row);
        return result;
    }

    public Instant addInstant(Granule granule) throws TemporalDataException {
        return addInstant(granule.getInf(), granule.getSup(), granule
                .getGranularity().getIdentifier(), granule.getGranularity()
                .getGranularityContextIdentifier());
    }

    public Interval addInterval(Instant begin, Instant end)
            throws TemporalDataException {

        // XXX make this more correct using Tim's classes (e.g., check &
        // handle different granularities)

        GenericTemporalElement interval = addTemporalElement(begin.getInf(),
                end.getSup(), begin.getGranularityId(),
                begin.getGranularityContextId(),
                TemporalDataset.PRIMITIVE_INTERVAL);

        // add edges to temporal element graph
        this.getTemporalElements().addEdge(begin, interval);
        this.getTemporalElements().addEdge(end, interval);

        return (Interval) interval.asPrimitive();
    }
    
    public Interval addInterval(Instant begin, Span span)
            throws TemporalDataException {
        throw new UnsupportedOperationException();
    }
    
    public Interval addInterval(Span span, Instant end)
            throws TemporalDataException {
        throw new UnsupportedOperationException();
    }
    
    /**
     * Get an instance of the default {@link Schema} used for
     * {@link TemporalElement} instances. Contains the data members internally
     * used to model a temporal element, i.e. inf, sup, granularity, granularity
     * context, and kind.
     * 
     * @return the TemporalElement data Schema
     */
    private Schema getTemporalElementSchema() {
        Schema s = new Schema();

        s.addColumn(TemporalElement.ID, long.class, -1);
        s.addColumn(TemporalElement.INF, long.class, Long.MIN_VALUE);
        s.addColumn(TemporalElement.SUP, long.class, Long.MAX_VALUE);
        s.addColumn(TemporalElement.GRANULARITY_ID, int.class, -1);
        s.addColumn(TemporalElement.GRANULARITY_CONTEXT_ID, int.class, -1);
        s.addColumn(TemporalElement.KIND, int.class, -1);

        return s;
    }

    // predefined kinds of temporal elements
    public static final int PRIMITIVE_SPAN = 0;
    public static final int PRIMITIVE_SET = 1;
    public static final int PRIMITIVE_INSTANT = 2;
    public static final int PRIMITIVE_INTERVAL = 3;

    /**
     * creates a human-readable string from a {@link TemporalDataset}.
     * <p>
     * Example:TemporalDataset [7 temporal elements, 9 temporal objects, 8
     * object relationships]
     * 
     * @return a string representation
     */
    @Override
    public String toString() {
        return "TemporalDataset [" + this.temporalElements.getNodeCount()
                + " temporal elements, " + super.getNodeCount()
                + " temporal objects, " + super.getEdgeCount()
                + " object relationships]";
    }

    @Override
    public long getInf() {
        // TODO ignore unanchored temporal elements (use IntervalIndex?)
        Table elem = temporalElements.getNodeTable(); 
        return elem.getLong(elem.getMetadata(TemporalElement.INF).getMinimumRow(), TemporalElement.INF);
    }

    @Override
    public long getSup() {
        // TODO ignore unanchored temporal elements (use IntervalIndex?)
        Table elem = temporalElements.getNodeTable(); 
        return elem.getLong(elem.getMetadata(TemporalElement.SUP).getMaximumRow(), TemporalElement.SUP);
    }
}