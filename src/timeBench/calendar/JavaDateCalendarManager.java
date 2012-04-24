package timeBench.calendar;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

import timeBench.data.TemporalDataException;

/**
 * The calendarManager which maps calendar functionality to the Java Calendar class.
 * 
 * <p>
 * Added:          2011-07-19 / TL<br>
 * Modifications: 
 * </p>
 * 
 * @author Tim Lammarsch
 *
 */
public class JavaDateCalendarManager implements CalendarManager {
	protected static JavaDateCalendarManager singleton = null;
	protected Calendar defaultCalendar = null;
	protected java.util.Calendar javaCalendar = null;
	
	/**
	 * Constructs a JavaDateCalendarManager. Consider using the
	 * {@linkplain JavaDateCalendarManager#getDefaultCalendar() singleton} instead.
	 */
	public JavaDateCalendarManager() {
		javaCalendar = java.util.Calendar.getInstance();
		javaCalendar.setLenient(true);
	}	
	
	/**
	 * The granularities enumeration supports a certain number of granularities.
	 *  
	 * <p>
	 * Added:          2011-07-19 / TL<br>
	 * Modifications: 
	 * </p>
	 * 
	 * @author Tim Lammarsch
	 *
	 */
	public enum Granularities {
		Millisecond (0),
		Second (1),
		Minute (2),
		Hour (3),
		Day (4),
		Week (5),
		Month (6),
		Quarter (7),
		Year (8),
		Calendar (16383),	// Calendar has one granule from start to end of the calendar
		Top (32767);	// Top has one granule from start to end of time

		
		private int intValue;
		
		Granularities(int toInt) {
			intValue = toInt;
		}
		
		
		/**
		 * Converts a granularity from the enumeration to an identifier.
		 * This identifier is not globally unique, it depends on calendarManager and calendar.
		 * @return The equivalent identifier.
		 */
		public int toInt()
		{
			return intValue;
		}
		
		
		/**
		 * Converts an identifier to a granularity from the enumeration.
		 * This identifier is not globally unique, it depends on calendarManager and calendar.
		 * @param intValue The identifier.
		 * @return The granularity from the enumeration.
		 * @throws TemporalDataException
		 */
		public static Granularities fromInt(int intValue) throws TemporalDataException {
			switch(intValue) {
				case 0: return Granularities.Millisecond;
				case 1: return Granularities.Second;
				case 2: return Granularities.Minute;
				case 3: return Granularities.Hour;
				case 4: return Granularities.Day;
				case 5: return Granularities.Week;
				case 6: return Granularities.Month;
				case 7: return Granularities.Quarter;
				case 8: return Granularities.Year;
				case 16383: return Granularities.Calendar; 
				case 32767: return Granularities.Top; 
				default: throw new TemporalDataException("Unknown Granularity");
			}
		}
	}
	
	
	/**
	 * Provides access to a singleton instance of the class. This is not a generic factory method. It
	 * does only create one instance and provides that one with every call.
	 * @return The JavaDateCalendarManager instance.
	 */
	public static CalendarManager getSingleton() {
		if (singleton == null)
			singleton = new JavaDateCalendarManager();
		return singleton;
	}	
	
	
	/**
	 * Generates an instance of a calendar, the only one currently provided by this class.
	 * @return The calendar.
	 */
	public Calendar calendar() {
		return new Calendar(this);
	}
	
	
	/**
	 * Provides access to a singleton instance of a calendar, the only one currently provided by this class.
	 * It does only create one instance and provides that one with every call.
	 * @return The calendar.
	 */
	public Calendar getDefaultCalendar() {
		if (defaultCalendar == null)
			defaultCalendar = calendar();
		return defaultCalendar;
	}
    
    private int[] buildGranularityListForCreateGranule(Granularity granularity) throws TemporalDataException {
        int[] result;

        switch (Granularities.fromInt(granularity.getIdentifier())) {
        	case Millisecond:
        		result = new int[0];
        		break;
        	case Second:
        		result = new int[] { java.util.Calendar.MILLISECOND };
        		break;
        	case Minute:
        		result = new int[] { java.util.Calendar.SECOND, java.util.Calendar.MILLISECOND };
        		break;
        	case Hour:
        		result = new int[] { java.util.Calendar.MINUTE,
        				java.util.Calendar.SECOND, java.util.Calendar.MILLISECOND };
        		break;
        	case Day:
        		result = new int[] {
        				java.util.Calendar.AM_PM, java.util.Calendar.HOUR,
        				java.util.Calendar.HOUR_OF_DAY, java.util.Calendar.MINUTE,
        				java.util.Calendar.SECOND, java.util.Calendar.MILLISECOND };
        		break;
        	case Week:
        		result = new int[] { // java.util.Calendar.DAY_OF_WEEK, commented out because only works manually
        				java.util.Calendar.AM_PM, java.util.Calendar.HOUR,
        				java.util.Calendar.HOUR_OF_DAY, java.util.Calendar.MINUTE,
        				java.util.Calendar.SECOND, java.util.Calendar.MILLISECOND };
        		break;
        	case Month:
        		result = new int[] { java.util.Calendar.DAY_OF_MONTH,
        				java.util.Calendar.AM_PM, java.util.Calendar.HOUR,
        				java.util.Calendar.HOUR_OF_DAY, java.util.Calendar.MINUTE,
        				java.util.Calendar.SECOND, java.util.Calendar.MILLISECOND };
        		break;
        	case Quarter:
        		result = new int[] { java.util.Calendar.DAY_OF_MONTH,
        				java.util.Calendar.AM_PM, java.util.Calendar.HOUR,
        				java.util.Calendar.HOUR_OF_DAY, java.util.Calendar.MINUTE,
        				java.util.Calendar.SECOND, java.util.Calendar.MILLISECOND };
        		break;
        	case Year:
        		result = new int[] { java.util.Calendar.DAY_OF_YEAR,
        				java.util.Calendar.AM_PM, java.util.Calendar.HOUR,
        				java.util.Calendar.HOUR_OF_DAY, java.util.Calendar.MINUTE,
        				java.util.Calendar.SECOND, java.util.Calendar.MILLISECOND };
        		break;
        	default:
        		throw new TemporalDataException("Granularity not implemented yet");
        }

        return result;    	
    }

    
    protected Granule createGranule(long chronon, Granularity granularity) throws TemporalDataException {
		GregorianCalendar calInf = new GregorianCalendar();
		GregorianCalendar calSup = new GregorianCalendar();
		calInf.setTimeInMillis(chronon);
		calSup.setTimeInMillis(chronon);

		return createGranule(calInf,calSup,granularity);
	}
	
	/**
	 * Constructs a {@link Granule} from a given {@link Date}. Consider using the adequate constructor of
	 * {@link Granule} instead.
	 * @param date the {@link Date} used to generate the granule
	 * @param granularity granularity the {@link Granularity} to which the granule belongs
	 * @return the constructed {@link Granule}
	 * @throws TemporalDataException TemporalDataException thrown when granularities are not fully implemented
	 */ 
	public Granule createGranule(Date date, Granularity granularity) throws TemporalDataException {
		GregorianCalendar calInf = new GregorianCalendar();
		GregorianCalendar calSup = new GregorianCalendar();
		calInf.setTime(date);
		calSup.setTime(date);

		return createGranule(calInf,calSup,granularity);
	}
	
	private Granule createGranule(GregorianCalendar calInf, GregorianCalendar calSup, Granularity granularity) throws TemporalDataException {
		for (int field : buildGranularityListForCreateGranule(granularity)) {
			calInf.set(field, calInf.getActualMinimum(field));
			calSup.set(field, calSup.getActualMaximum(field));		
		}
		
		if(granularity.getIdentifier() == Granularities.Quarter.intValue) {
			switch(calInf.get(GregorianCalendar.MONTH)) {
				case GregorianCalendar.JANUARY:
				case GregorianCalendar.FEBRUARY:
				case GregorianCalendar.MARCH:
					calInf.set(GregorianCalendar.MONTH,GregorianCalendar.JANUARY);
					calSup.set(GregorianCalendar.MONTH,GregorianCalendar.MARCH);
					break;
				case GregorianCalendar.APRIL:
				case GregorianCalendar.MAY:
				case GregorianCalendar.JUNE:
					calInf.set(GregorianCalendar.MONTH,GregorianCalendar.APRIL);
					calSup.set(GregorianCalendar.MONTH,GregorianCalendar.JUNE);
					break;
				case GregorianCalendar.JULY:
				case GregorianCalendar.AUGUST:
				case GregorianCalendar.SEPTEMBER:
					calInf.set(GregorianCalendar.MONTH,GregorianCalendar.JULY);
					calSup.set(GregorianCalendar.MONTH,GregorianCalendar.SEPTEMBER);
					break;
				case GregorianCalendar.OCTOBER:
				case GregorianCalendar.NOVEMBER:
				case GregorianCalendar.DECEMBER:
					calInf.set(GregorianCalendar.MONTH,GregorianCalendar.OCTOBER);
					calSup.set(GregorianCalendar.MONTH,GregorianCalendar.DECEMBER);
					break;
			}
		} else if(granularity.getIdentifier() == Granularities.Week.intValue) {
			long dow = (calInf.get(GregorianCalendar.DAY_OF_WEEK) + 6) % 7;
			calInf.setTimeInMillis(calInf.getTimeInMillis()-dow*86400000);
			calSup.setTimeInMillis(calSup.getTimeInMillis()+(6-dow)*86400000);
		}
		
		return new Granule(calInf.getTimeInMillis(),calSup.getTimeInMillis(),Granule.MODE_FORCE,granularity);
	}


	/**
	 * Returns an {@link Array} of granularity identifiers that are provided by the calendar.
	 * @return {@link Array} of granularity identifiers
	 */
	@Override
	public int[] getGranularityIdentifiers() {
		return new int[] {0,1,2,3,4,5,6,7,8,32767};
	}

	/**
	 * Calculate and return the identifier of a {@link Granule}. An identifier is a numeric label given in the context
	 * of the {@link Granularity}. Consider using the adequate method of
	 * {@link Granule} instead.
	 * @return the identifier
	 * @throws TemporalDataException thrown when granularities are not fully implemented
	 */
	@Override
	public long createGranuleIdentifier(Granule granule) throws TemporalDataException {
		long result = 0;
		
		switch(Granularities.fromInt(granule.getGranularity().getIdentifier())) {
			case Millisecond:
				switch(Granularities.fromInt(granule.getGranularity().getGranularityContextIdentifier())) {
					case Second:
						result = granule.getInf()%1000;
						break;
					case Minute:
						result = granule.getInf()%60000;
						break;
					case Hour:
						result = granule.getInf()%3600000;
						break;
					case Day:
						result = granule.getInf()%86400000;
						break;
					case Week: {
						GregorianCalendar cal = new GregorianCalendar();
						cal.setTimeInMillis(granule.getInf());
						result = cal.get(GregorianCalendar.DAY_OF_WEEK)*86400000 + granule.getInf()%86400000;
						break;
					}
					case Month: {
						GregorianCalendar cal = new GregorianCalendar();
						cal.setTimeInMillis(granule.getInf());
						result = cal.get(GregorianCalendar.DAY_OF_MONTH)*86400000 + granule.getInf()%86400000;
						break;
					}
					case Quarter: 
						result = getDayInQuarter(granule.getInf())*86400000 + granule.getInf()%86400000;
						break;
					case Year: {
						GregorianCalendar cal = new GregorianCalendar();
						cal.setTimeInMillis(granule.getInf());
						result = cal.get(GregorianCalendar.DAY_OF_YEAR)*86400000 + granule.getInf()%86400000;
						break;
					}
					default:
						result = granule.getInf();					
				}
				break;
			case Second:
				result = granule.getInf()/1000;
				switch(Granularities.fromInt(granule.getGranularity().getGranularityContextIdentifier())) {
					case Minute:
						result %= 60;
						break;
					case Hour:
						result %= 3600;
						break;
					case Day:
						result %= 86400;
					case Week: {
						GregorianCalendar cal = new GregorianCalendar();
						cal.setTimeInMillis(granule.getInf());
						result = cal.get(GregorianCalendar.DAY_OF_WEEK)*86400 + result % 86400;
						break;
					}
					case Month: {
						GregorianCalendar cal = new GregorianCalendar();
						cal.setTimeInMillis(granule.getInf());
						result = cal.get(GregorianCalendar.DAY_OF_MONTH)*86400 + result % 86400;
						break;
					}
					case Quarter: 
						result = getDayInQuarter(granule.getInf())*86400 + result % 86400;
						break;
					case Year: {
						GregorianCalendar cal = new GregorianCalendar();
						cal.setTimeInMillis(granule.getInf());
						result = cal.get(GregorianCalendar.DAY_OF_YEAR)*86400 + result % 86400;
						break;
					}
				}
				break;
			case Minute:
				result = granule.getInf()/60000;
				switch(Granularities.fromInt(granule.getGranularity().getGranularityContextIdentifier())) {
					case Hour:
						result %= 60;
						break;
					case Day:
						result %= 1440;
					case Week: {
						GregorianCalendar cal = new GregorianCalendar();
						cal.setTimeInMillis(granule.getInf());
						result = cal.get(GregorianCalendar.DAY_OF_WEEK)*1440 + result % 1440;
						break;
					}
					case Month: {
						GregorianCalendar cal = new GregorianCalendar();
						cal.setTimeInMillis(granule.getInf());
						result = cal.get(GregorianCalendar.DAY_OF_MONTH)*1440 + result % 1440;
						break;
					}
					case Quarter: 
						result = getDayInQuarter(granule.getInf())*1440 + result % 1440;
						break;
					case Year: {
						GregorianCalendar cal = new GregorianCalendar();
						cal.setTimeInMillis(granule.getInf());
						result = cal.get(GregorianCalendar.DAY_OF_YEAR)*1440 + result % 1440;
						break;
					}
				}
				break;
			case Hour:
				result = granule.getInf()/3600000;
				switch(Granularities.fromInt(granule.getGranularity().getGranularityContextIdentifier())) {
					case Day:
						result %= 24;
						break;
					case Week: {
						GregorianCalendar cal = new GregorianCalendar();
						cal.setTimeInMillis(granule.getInf());
						result = cal.get(GregorianCalendar.DAY_OF_WEEK)*24 + result % 24;
						break;
					}
					case Month: {
						GregorianCalendar cal = new GregorianCalendar();
						cal.setTimeInMillis(granule.getInf());
						result = cal.get(GregorianCalendar.DAY_OF_MONTH)*24 + result % 24;
						break;
					}
					case Quarter: 
						result = getDayInQuarter(granule.getInf())*24 + result % 24;
						break;
					case Year: {
						GregorianCalendar cal = new GregorianCalendar();
						cal.setTimeInMillis(granule.getInf());
						result = cal.get(GregorianCalendar.DAY_OF_YEAR)*24 + result % 24;
						break;
					}
				}
				break;
			case Day:
				switch(Granularities.fromInt(granule.getGranularity().getGranularityContextIdentifier())) {
					case Week: {
						GregorianCalendar cal = new GregorianCalendar();
						cal.setTimeInMillis(granule.getInf());
						result = cal.get(GregorianCalendar.DAY_OF_WEEK);
						break;
					}
					case Month: {
						GregorianCalendar cal = new GregorianCalendar();
						cal.setTimeInMillis(granule.getInf());
						result = cal.get(GregorianCalendar.DAY_OF_MONTH);
						break;
					}
					case Quarter: 
						result = getDayInQuarter(granule.getInf());
						break;
					case Year: {
						GregorianCalendar cal = new GregorianCalendar();
						cal.setTimeInMillis(granule.getInf());
						result = cal.get(GregorianCalendar.DAY_OF_YEAR);
						break;
					}
					default:
						result = granule.getInf() / 86400000;
				}
				break;
			case Week:
				switch(Granularities.fromInt(granule.getGranularity().getGranularityContextIdentifier())) {
					case Month: {
						GregorianCalendar cal = new GregorianCalendar();
						cal.setTimeInMillis(granule.getInf());
						result = cal.get(GregorianCalendar.WEEK_OF_MONTH);
						break;
						}
					case Quarter: {
						GregorianCalendar cal = new GregorianCalendar();
						cal.setTimeInMillis(granule.getInf());
						result = cal.get(GregorianCalendar.WEEK_OF_YEAR) / 4;
						break;
						}
					case Year: {
						GregorianCalendar cal = new GregorianCalendar();
						cal.setTimeInMillis(granule.getInf());
						result = cal.get(GregorianCalendar.WEEK_OF_MONTH);
						break;
						}
					default:
						result = granule.getInf() / 60480000;
						break;
				}
				break;
			case Month: {
				GregorianCalendar cal = new GregorianCalendar();
				cal.setTimeInMillis(granule.getInf());
				switch(Granularities.fromInt(granule.getGranularity().getGranularityContextIdentifier())) {
					case Quarter:
						result = cal.get(GregorianCalendar.MONTH) % 3;
						break;
					case Year:
						result = cal.get(GregorianCalendar.MONTH);
						break;
					default:
						result = granule.getInf() / 2592000000L;
						break;
					}
				break;}
			case Quarter:
				switch(Granularities.fromInt(granule.getGranularity().getGranularityContextIdentifier())) {
					case Year:{
						GregorianCalendar cal = new GregorianCalendar();
						cal.setTimeInMillis(granule.getInf());
						result = cal.get(GregorianCalendar.MONTH) / 4;
						break;}
					default:{
						result = granule.getInf() / 10368000000L;
						break;
					}
				}
				break;
			case Year:
				GregorianCalendar cal = new GregorianCalendar();
				result = cal.get(GregorianCalendar.YEAR);
				break;
		}
		
		return result;
	}

	private long getDayInQuarter(long inf) {
		GregorianCalendar cal = new GregorianCalendar();
		cal.setTimeInMillis(inf);
		switch(cal.get(GregorianCalendar.MONTH)) {
			case GregorianCalendar.JANUARY:
				return cal.get(GregorianCalendar.DAY_OF_MONTH);
			case GregorianCalendar.FEBRUARY:
				return 31+cal.get(GregorianCalendar.DAY_OF_MONTH);
			case GregorianCalendar.MARCH:
				return 31+
						(cal.isLeapYear(cal.get(GregorianCalendar.YEAR)) ? 29 : 28) +
						cal.get(GregorianCalendar.DAY_OF_MONTH);
			case GregorianCalendar.APRIL:
				return cal.get(GregorianCalendar.DAY_OF_MONTH);
			case GregorianCalendar.MAY:
				return 30+cal.get(GregorianCalendar.DAY_OF_MONTH);
			case GregorianCalendar.JUNE:
				return 61+cal.get(GregorianCalendar.DAY_OF_MONTH);
			case GregorianCalendar.JULY:
				return cal.get(GregorianCalendar.DAY_OF_MONTH);
			case GregorianCalendar.AUGUST:
				return 31+cal.get(GregorianCalendar.DAY_OF_MONTH);
			case GregorianCalendar.SEPTEMBER:
				return 62+cal.get(GregorianCalendar.DAY_OF_MONTH);
			case GregorianCalendar.OCTOBER:
				return cal.get(GregorianCalendar.DAY_OF_MONTH);
			case GregorianCalendar.NOVEMBER:
				return 31+cal.get(GregorianCalendar.DAY_OF_MONTH);
			default: // GregorianCalendar.DECEMBER:
				return 61+cal.get(GregorianCalendar.DAY_OF_MONTH);
		}
	}

	/**
	 * Returns the identifier of the bottom granularity
	 * @return the bottom granularity identifier
	 */
	public int getBottomGranularityIdentifier() {
		return 0;
	}
	
	/**
	 * Returns the identifier of the top granularity. This is the highest possible granularity of the calendar.
	 * Usually, this is a granularity where one granule is composed of all the time the calendar is defined for.
	 * Let all calendars that would normally have this be modified so they have one. 
	 * @return the top granularity identifier
	 */
	public int getTopGranularityIdentifier() {
		return 16383;
	}

	/**
	 * Constructs a {@link Granule} from inf to sup using a given {@linkplain Granule#MODE_INF_GANULE mode} and
	 * for a given {@link Granularity}.
	 * Consider using the adequate constructor of {@link Granule} instead.
	 * @param inf the chronon that determines the start of the granule constructed
	 * @param sup the chronon that determines the end of the granule constructed
	 * @param mode the {@linkplain Granule#MODE_INF_GANULE mode} used to construct the granule
	 * @param granularity the {@link Granularity} to use
	 * @return the constructed {@link Granule}
	 * @throws TemporalDataException TemporalDataException thrown when granularities are not fully implemented
	 */
	@Override
	public Granule createGranule(long inf, long sup, int mode, Granularity granularity) throws TemporalDataException {
		switch(mode) {
			case Granule.MODE_FORCE:
				return new Granule(inf,sup,mode,granularity);
			case Granule.MODE_INF_GRANULE:
				return createGranule(inf,granularity);
			case Granule.MODE_MIDDLE_GRANULE:
				return createGranule(inf+(sup-inf)/2,granularity);
			case Granule.MODE_SUP_GRANULE:
				return createGranule(sup,granularity);
			default:
				throw new TemporalDataException("Illegal mode in createGranule");
		}
	}


	/**
	 * Constructs several {@link Granule} objects from inf to sup that are at least partly in the given interval with
	 * a coverage of a least a given fraction and
	 * for a given {@link Granularity}. Consider using the adequate factory of {@link Granularity} instead.
	 * @param inf the chronon that determines the start of the {@link Granule} range constructed
	 * @param sup the chronon that determines the end of the {@link Granule} range constructed
	 * @param cover the coverage fraction of a granule needed to be included in the result
	 * @param granularity the {@link Granularity} to use
	 * @return the constructed {@link Array} of {@link Granule}
	 * @throws TemporalDataException TemporalDataException thrown when granularities are not fully implemented
	 */
	@Override
	public Granule[] createGranules(long inf, long sup, double cover,
			Granularity granularity) throws TemporalDataException {
		Granule first = createGranule(inf,granularity);
		Granule last = createGranule(sup, granularity);
		
		long firstIdentifier = first.getIdentifier();
		long lastIdentifier = last.getIdentifier();
		
		if( (double)(inf-first.getInf()) / (double)(first.getSup()-first.getInf()) < cover) {
			firstIdentifier++;
		}
		if( (double)(last.getSup()-sup) / (double)(last.getSup()-last.getInf()) < cover) {
			lastIdentifier--;
		}

		Granule[] result;
		if(firstIdentifier>lastIdentifier)
			result = new Granule[0];
		else {
			result = new Granule[(int)(lastIdentifier-firstIdentifier+1)];
			result[0] = first;
			for(int i=1; i<(int)(lastIdentifier-firstIdentifier); i++) {
				result[i] = createGranule(result[i-1].getSup()+1,granularity);
			}
			result[(int)(lastIdentifier-firstIdentifier)] = last;	// when first=last set 0 again
		}
		
		return result;
	}

	/**
	 * Constructs several {@link Granule} objects from other {@link Granule} objects for a given {@link Granularity}
	 * that can (and most likely
	 * will) be in a different {@link Granularity}. All {@link Granule} with
	 * a coverage of a least a given fraction are returned.
	 * Consider using the adequate factory of {@link Granularity} instead.
	 * @param Granule[] the {@link Array} of {@link Granule} used as source
	 * @param cover the coverage fraction of a granule needed to be included in the result
	 * @param granularity the {@link Granularity} to use
	 * @return the constructed {@link Array} of {@link Granule}
	 * @throws TemporalDataException TemporalDataException thrown when granularities are not fully implemented
	 */
	@Override
	public Granule[] createGranules(Granule[] granules, double cover,
			Granularity granularity) throws TemporalDataException {
		ArrayList<Granule> result = new ArrayList<Granule>();
		for(Granule iGran : granules) {
			for(Granule iGran2 : createGranules(iGran.getInf(), iGran.getSup(), cover, granularity)) {
				if(result.get(result.size()-1).getIdentifier() < iGran2.getIdentifier())
					result.add(iGran2);
			}
		}
		
		return (Granule[])result.toArray();
	}

	/**
	 * Calculate and return the human readable label of a {@link Granule}.
	 * Consider using the adequate method of
	 * {@link Granule} instead.
	 * @return the label
	 * @throws TemporalDataException thrown when granularities are not fully implemented
	 */
	@Override
	public String createGranuleLabel(Granule granule) throws TemporalDataException {
		String result = null;
		
		switch(Granularities.fromInt(granule.getGranularity().getIdentifier())) {
			case Day:
				if(Granularities.fromInt(granule.getGranularity().getGranularityContextIdentifier()) == Granularities.Week ) {
					GregorianCalendar cal = new GregorianCalendar();
					cal.setTimeInMillis(granule.getInf());
					cal.getDisplayName(GregorianCalendar.DAY_OF_WEEK, GregorianCalendar.LONG, Locale.getDefault());
				} else
					result = String.format("%d",granule.getIdentifier()+1);
				break;
			case Month:
				if(Granularities.fromInt(granule.getGranularity().getGranularityContextIdentifier()) == Granularities.Week ) {
					GregorianCalendar cal = new GregorianCalendar();
					cal.setTimeInMillis(granule.getInf());
					cal.getDisplayName(GregorianCalendar.MONTH, GregorianCalendar.LONG, Locale.getDefault());
				} else
					result = String.format("%d",granule.getIdentifier()+1);
				break;
			default:
				result = String.format("%d",granule.getIdentifier()+1);
		}
		
		return result;
	}


	/**
	 * Calculate and return the inf of a {@link Granule}.
	 * @return the inf
	 * @throws TemporalDataException thrown when granularities are not fully implemented
	 */
	@Override
	public long createInf(Granule granule) throws TemporalDataException {
		long result = 0;
		
		switch(Granularities.fromInt(granule.getGranularity().getIdentifier())) {
			case Millisecond:
				result = granule.getIdentifier();
				break;
			case Second:
				result = granule.getIdentifier()*1000;
				break;
			case Minute:
				result = granule.getIdentifier()*60000;
				break;
			case Hour:
				result = granule.getIdentifier()*360000;
				break;
			case Day:
				result = granule.getIdentifier()*8640000;
				break;
			case Week:
				result = granule.getIdentifier()*60480000;
				break;
			case Month:{
				GregorianCalendar cal = new GregorianCalendar();
				cal.setTimeInMillis(0);
				cal.set(GregorianCalendar.YEAR, (int)(granule.getIdentifier()/12+1970));
				cal.set(GregorianCalendar.MONTH, (int)(granule.getIdentifier()%12+1));
				result = cal.getTimeInMillis();
				break;}
			case Quarter:{
				GregorianCalendar cal = new GregorianCalendar();
				cal.setTimeInMillis(0);
				cal.set(GregorianCalendar.YEAR, (int)(granule.getIdentifier()/4+1970));
				cal.set(GregorianCalendar.MONTH, (int)(granule.getIdentifier()%4*3+1));
				result = cal.getTimeInMillis();
				break;}
			case Year:{
				GregorianCalendar cal = new GregorianCalendar();
				cal.setTimeInMillis(0);
				cal.set(GregorianCalendar.YEAR, (int)(granule.getIdentifier()+1970));
				result = cal.getTimeInMillis();
				break;}
		}
		
		return result;
	}

	/**
	 * Calculate and return the sup of a {@link Granule}.
	 * @return the sup
	 * @throws TemporalDataException thrown when granularities are not fully implemented
	 */
	@Override
	public long createSup(Granule granule) throws TemporalDataException {
		long result = 0;
		
		switch(Granularities.fromInt(granule.getGranularity().getIdentifier())) {
			case Millisecond:
				result = granule.getIdentifier();
				break;
			case Second:
				result = granule.getIdentifier()*1000+999;
				break;
			case Minute:
				result = granule.getIdentifier()*60000+59999;
				break;
			case Hour:
				result = granule.getIdentifier()*360000+359999;
				break;
			case Day:
				result = granule.getIdentifier()*8640000+8639999;
				break;
			case Week:
				result = granule.getIdentifier()*60480000+60479999;
				break;
			case Month:{
				GregorianCalendar cal = new GregorianCalendar();
				cal.setTimeInMillis(0);
				cal.set(GregorianCalendar.YEAR, (int)(granule.getIdentifier()/12+1970));
				cal.set(GregorianCalendar.MONTH, (int)(granule.getIdentifier()%12+1));
				result = cal.getTimeInMillis()+cal.getActualMaximum(GregorianCalendar.DAY_OF_MONTH)*8640000-1;
				break;}
			case Quarter:{
				GregorianCalendar cal = new GregorianCalendar();
				cal.setTimeInMillis(0);
				cal.set(GregorianCalendar.YEAR, (int)(granule.getIdentifier()/4+1970));
				cal.set(GregorianCalendar.MONTH, (int)(granule.getIdentifier()%4*3+1));
				result = cal.getTimeInMillis()+cal.getActualMaximum(GregorianCalendar.DAY_OF_MONTH)*8640000-1;
				cal.add(GregorianCalendar.MONTH, 1);
				result += cal.getActualMaximum(GregorianCalendar.DAY_OF_MONTH)*8640000;
				cal.add(GregorianCalendar.MONTH, 1);
				result += cal.getActualMaximum(GregorianCalendar.DAY_OF_MONTH)*8640000;
				break;}
			case Year:{
				GregorianCalendar cal = new GregorianCalendar();
				cal.setTimeInMillis(0);
				cal.set(GregorianCalendar.YEAR, (int)(granule.getIdentifier()+1970));
				result = cal.getTimeInMillis()+cal.getActualMaximum(GregorianCalendar.DAY_OF_YEAR)*8640000-1;
				break;}
		}
		
		return result;
	}
	
	public static String formatDebugString(long inf) {
		GregorianCalendar cal = new GregorianCalendar();
		cal.setTimeInMillis(inf);
		return String.format("%04d-%02d-%02d, %02d:%02d:%02d,%03d",cal.get(GregorianCalendar.YEAR),cal.get(GregorianCalendar.MONTH)+1,cal.get(GregorianCalendar.DAY_OF_MONTH),
				cal.get(GregorianCalendar.HOUR_OF_DAY),cal.get(GregorianCalendar.MINUTE),cal.get(GregorianCalendar.SECOND),cal.get(GregorianCalendar.MILLISECOND));
	}
}