package timeBench.action.analytical;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;

import timeBench.calendar.CalendarManager;
import timeBench.calendar.CalendarManagerFactory;
import timeBench.calendar.CalendarManagers;
import timeBench.calendar.Granularity;
import timeBench.calendar.Granule;
import timeBench.data.TemporalDataException;
import timeBench.data.relational.AnchoredTemporalElement;
import timeBench.data.relational.GenericTemporalElement;
import timeBench.data.relational.Instant;
import timeBench.data.relational.TemporalDataset;
import timeBench.data.relational.TemporalDatasetProvider;
import timeBench.data.relational.TemporalObject;

/**
 * 
 * 
 * <p>
 * Added:          / TL<br>
 * Modifications: 
 * </p>
 * 
 * @author Tim Lammarsch
 *
 */
public class TimeAggregationTree extends prefuse.action.Action implements TemporalDatasetProvider, MinMaxValuesProvider {
	timeBench.data.relational.TemporalDataset sourceDataset;
	timeBench.data.relational.TemporalDataset workingDataset;
	CalendarManager calendarManager;
	Granularity[] granularities;	
	double[][] minValues;
	double[][] maxValues;
	Double missingValueIdentifier;

	/**
	 * @param data
	 * @param temporalDataset
	 * @param columnsUsed 
	 */
	public TimeAggregationTree(timeBench.data.relational.TemporalDataset sourceDataset, CalendarManagers calendarManager,GranularityAggregationSettings[] granularities, Double missingValueIdentifier) {
		this.sourceDataset = sourceDataset;
		this.calendarManager = CalendarManagerFactory.getSingleton(calendarManager);
		this.granularities = new Granularity[granularities.length];
		for(int i=0; i<granularities.length; i++) {
			this.granularities[i] = new Granularity(this.calendarManager.getDefaultCalendar(),granularities[i].getIdentifier(),granularities[i].getContextIdentifier());
		}
		this.missingValueIdentifier = missingValueIdentifier;	
	}

	/* (non-Javadoc)
	 * @see prefuse.action.Action#run(double)
	 */
	@Override
	public void run(double frac) {
		try {
			workingDataset = new TemporalDataset(sourceDataset.getDataColumnSchema());
		
			int columnCount = sourceDataset.getDataColumnCount();
			minValues = new double[columnCount][granularities.length];
			maxValues = new double[columnCount][granularities.length];
			for(int i=0; i<columnCount;	i++) {
				if (sourceDataset.getDataColumn(i).canGetDouble()) {
					for(int j=0; j<granularities.length; j++) {
						minValues[i][j] = Double.MAX_VALUE;
						maxValues[i][j] = Double.MIN_VALUE;
					}
				}
			}
						
			GenericTemporalElement te = workingDataset.addTemporalElement(0 /* TODO Interval Tree */,0 /* TODO Interval Tree */,
					32767, 32767, TemporalDataset.PRIMITIVE_INTERVAL);
			TemporalObject root = workingDataset.addTemporalObject(te);

			ArrayList<ArrayList<TemporalObject>> currentLeaves = new ArrayList<ArrayList<TemporalObject>>();
			currentLeaves.add(new ArrayList<TemporalObject>());
			ArrayList<TemporalObject> currentBranches = new ArrayList<TemporalObject>();
			currentBranches.add(root);
			for(int i=granularities.length-1; i>=0;i--) {
				ArrayList<ArrayList<TemporalObject>> futureLeaves = new ArrayList<ArrayList<TemporalObject>>();
				ArrayList<TemporalObject> futureBranches = new ArrayList<TemporalObject>(); 
				for(int k=0; k<currentLeaves.size();k++) {
					ArrayList<TemporalObject> iCurrentLeaves = currentLeaves.get(k);
					while(iCurrentLeaves.size() > 0) {
						TemporalObject currentLeave = iCurrentLeaves.get(iCurrentLeaves.size()-1);
						iCurrentLeaves.remove(iCurrentLeaves.size()-1);
						long inf = currentLeave.getTemporalElement().asGeneric().getInf();
						long sup = currentLeave.getTemporalElement().asGeneric().getSup();
						TemporalObject targetBranch = null;
					    for(int j=futureBranches.size()-1;j>=0;j++) {
					    	TemporalObject potentialBranch = futureBranches.get(j);
					    	if (potentialBranch.getTemporalElement().asGeneric().getInf() <= inf &&
					    			potentialBranch.getTemporalElement().asGeneric().getInf() >= sup) {
					    		targetBranch = potentialBranch;
					    	    break;
					    	}
					    }
					    if (targetBranch == null) {
					    	Instant newTe = workingDataset.addInstant(new Granule(inf,sup,granularities[i]));
					    	targetBranch = workingDataset.addTemporalObject(newTe);
					    	futureBranches.add(targetBranch);
					    	futureLeaves.add(new ArrayList<TemporalObject>());
					    }
				    	currentBranches.get(k).linkWithChild(targetBranch);
				    	futureLeaves.get(futureLeaves.size()).add(currentLeave);
					}
				}
				if(i==0) {
					for(int j=0; j<futureBranches.size(); j++ ) {
						aggregate(futureBranches.get(j),futureLeaves.get(j).iterator(),-1);
					}
				} else {
					currentBranches = futureBranches;
					currentLeaves = futureLeaves;
				}
			}
			
			aggregate(root,0);
		
			workingDataset.setRoots(new long[] { root.getId() } );
		} catch (TemporalDataException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	
	

	private void aggregate(TemporalObject parent,int level) {
		for(Iterator<TemporalObject> i = parent.childElements(); i.hasNext(); ) {
			TemporalObject child = i.next();
				aggregate(child,level+1);
	    }
		aggregate(parent,parent.childElements(),level-1);
	}
	private void aggregate(TemporalObject parent,Iterator<TemporalObject> childs,int level) {		
		double[] numObjects = new double[sourceDataset.getDataColumnCount()]; 
		double[] totalValue = new double[sourceDataset.getDataColumnCount()]; 
		for(int i=0; i<sourceDataset.getDataColumnCount(); i++) {
			numObjects[i] = 0;
			totalValue[i] = 0;
		}

		for(Iterator<TemporalObject> i = parent.childElements(); i.hasNext(); ) {
			TemporalObject temporalObject = i.next();
			for(int j=0; j<sourceDataset.getDataColumnCount(); j++) {
				if(sourceDataset.getDataColumn(j).canGetDouble()) {
					double value = temporalObject.getDouble(j);
					totalValue[j] += value;
					numObjects[j]++;
					if (level >= 0 ) {
						minValues[j][level] = Math.min(minValues[j][level], value);
						maxValues[j][level] = Math.max(maxValues[j][level], value);
					}
				}
			}
		}
		
		for(int i=0; i<sourceDataset.getDataColumnCount(); i++) {
			totalValue[i] /= numObjects[i];
			if(Double.isNaN(totalValue[i])) {
				minValues[i][level] = Double.NaN;
				maxValues[i][level] = Double.NaN;
			}
		}
	}

	/* (non-Javadoc)
	 * @see timeBench.data.relational.TemporalDatasetProvider#getTemporalDataset()
	 */
	@Override
	public timeBench.data.relational.TemporalDataset getTemporalDataset() {
		return workingDataset;
	}

	@Override
	public Double getMinValue(int level,int index) {
		if(minValues == null || minValues.length <= index)
			return null;
		else
			return minValues[index][level];
	}

	@Override
	public Double getMaxValue(int level,int index) {
		if(maxValues == null || maxValues.length <= index)
			return null;
		else
			return maxValues[index][level];	}
}
