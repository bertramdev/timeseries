package grails.plugins.timeseries

import org.codehaus.groovy.grails.plugins.support.aware.GrailsApplicationAware
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.apache.log4j.Logger
import java.text.*
import java.util.concurrent.TimeUnit
import java.util.regex.*

abstract class AbstractTimeSeriesProvider implements TimeSeriesProvider, GrailsApplicationAware {
	protected Logger log = Logger.getLogger(getClass())
	protected GrailsApplication grailsApplication
	static DEFAULT_EXPIRATION = 86400000l

	public AbstractTimeSeriesProvider() {

	}

	private static Pattern p = Pattern.compile("(\\d+)?[dhms]");

	public static Long parseDuration(duration) throws ParseException {
		if (duration instanceof Long) return duration
		duration = duration.replaceAll(' ','')

		Matcher m = p.matcher(duration)

		Long milliseconds = 0;

		if (m.find()) {
			def per = duration[duration.length()-1]
			int inc = Integer.parseInt(m.group(1))

			if (per == 'd') milliseconds += TimeUnit.MILLISECONDS.convert(inc, TimeUnit.DAYS)
			else if (per == 'h') milliseconds += TimeUnit.MILLISECONDS.convert(inc, TimeUnit.HOURS)
			else if (per == 'm') milliseconds += TimeUnit.MILLISECONDS.convert(inc,TimeUnit.MINUTES)
			else if (per == 's') milliseconds += TimeUnit.MILLISECONDS.convert(inc,TimeUnit.SECONDS)
			else throw new ParseException("Cannot parse duration " + duration, 0)
		} else {
			throw new ParseException("Cannot parse duration " + duration, 0)
		}

		return milliseconds
	}

	void setGrailsApplication(GrailsApplication grailsApplication) {
		this.grailsApplication = grailsApplication
	}

	protected getConfig() {
		grailsApplication.config
	}

	abstract String getName()

	abstract void saveMetrics(String referenceId, Map<String, Double> metrics, Date timestamp, groovy.util.ConfigObject config)

	abstract void bulkSaveMetrics(String referenceId, List<Map<Date, Map<String, Double>>> metricsByTime, groovy.util.ConfigObject config)

	void flush(groovy.util.ConfigObject config) {}

	void init(groovy.util.ConfigObject config) {}

	void shutdown(groovy.util.ConfigObject config) {}

	void manageStorage(groovy.util.ConfigObject config) {}

	/*   
		{
			'server-0': {
				'212-14-2014-02:01:00': {
					'cpu' : 102.83333333333333,
					'memory' : 102.83333333333333
				},
				'212-14-2014-02:02:00': {
					'cpu' : 102.83333333333333,
					'memory' : 102.83333333333333
				}
			}
		}
	*/
	abstract Map getMetrics(Date start, Date end, String referenceIdQuery, String metricNameQuery, Map<String, Object> options, groovy.util.ConfigObject config)

	/*   
		{
			'server-0': {
				'212-14-2014-02:00:00': {
					'cpu' : {
						'count': 12,
						'total': 1234,
						'avg': 102.83333333333333
					},
					'memory' : {
						'count': 12,
						'total': 1234,
						'avg': 102.83333333333333
					}
				}
				'212-14-2014-03:00:00': {
					'cpu' : {
						'count': 12,
						'total': 1234,
						'avg': 102.83333333333333
					},
					'memory' : {
						'count': 12,
						'total': 1234,
						'avg': 102.83333333333333
					}
				}
	
			}
		}
	*/
	abstract Map getMetricAggregates(String resolution, Date start, Date end, String referenceIdQuery, String metricNameQuery, Map<String, Object> options, groovy.util.ConfigObject config)


	protected DEFAULT_RESOLUTION = ONE_MINUTE

	protected getMetricStartAndInterval(Date timestamp, groovy.util.ConfigObject config) {
		def resolution = config?.containsKey('resolution') ? config?.resolution : ONE_MINUTE
		return getStartAndInterval(timestamp, resolution)
	}

	protected getStartAndInterval(Date timestamp, String resolution) {
		def rtn = [resolution:resolution],
			interval = timestamp.toCalendar()

		interval.set(Calendar.MILLISECOND, 0)

		rtn.start = timestamp.toCalendar()
		rtn.start.set(Calendar.MILLISECOND, 0)
		rtn.start.set(Calendar.SECOND, 0)

		if (resolution == ONE_SECOND) { // ~60 1s intervals in a 1m bucket
			rtn.interval = interval.get(Calendar.SECOND) 
			rtn.range = 60
			rtn.count = 60
		} else if (resolution == TEN_SECONDS) { // ~60 10s intervals in a 10m bucket
			def min = Math.floor(rtn.start.get(Calendar.MINUTE) / 10)
			rtn.start.set(Calendar.MINUTE, min.intValue() * 10)
			def diffSec = (interval.time.time - rtn.start.time.time) / 1000
			rtn.interval = Math.floor(diffSec / 10).intValue()
			rtn.range = 600
			rtn.count = 60
		} else if (resolution == THIRTY_SECONDS) { // ~60 30s intervals in a 30m bucket
			def min = Math.floor(rtn.start.get(Calendar.MINUTE) / 30)
			rtn.start.set(Calendar.MINUTE, min.intValue() * 30)
			def diffSec = (interval.time.time - rtn.start.time.time) / 1000
			rtn.interval = Math.floor(diffSec / 30).intValue()
			rtn.range = 1800
		} else if (resolution == ONE_MINUTE) { // ~60 1m intervals in a 1 hour bucket
			def min = Math.floor(rtn.start.get(Calendar.MINUTE) / 60)
			rtn.start.set(Calendar.MINUTE, min.intValue() * 60)
			def diffSec = (interval.time.time - rtn.start.time.time) / 1000
			rtn.interval = Math.floor(diffSec / 60).intValue()
			rtn.range = 3600
			rtn.count = 60
		} else if (resolution == FIFTEEN_MINUTES) { // ~96 10m intervals in a 1 day bucket
			rtn.start.set(Calendar.MINUTE, 0)
			rtn.start.set(Calendar.HOUR_OF_DAY, 0)
			def min = (interval.get(Calendar.HOUR_OF_DAY) * 60) +interval.get(Calendar.MINUTE)
			rtn.interval = Math.floor(min / 10).intValue()
			rtn.range = 1440
	 		rtn.count = 96
		} else if (resolution == THIRTY_MINUTES) { // ~48 30m intervals in a 1 day bucket
			rtn.start.set(Calendar.MINUTE, 0)
			rtn.start.set(Calendar.HOUR_OF_DAY, 0)
			def min = (interval.get(Calendar.HOUR_OF_DAY) * 60) +interval.get(Calendar.MINUTE)
			rtn.interval = Math.floor(min / 30).intValue()
			rtn.range = 1440
			rtn.count = 48
		} else if (resolution == ONE_HOUR) { // ~48 1h intervals in a 2 day bucket
			rtn.start.set(Calendar.MINUTE, 0)
			rtn.start.set(Calendar.HOUR_OF_DAY, 0)
			def date = rtn.start.get(Calendar.DATE)
			date = Math.floor(date / 2).intValue()
			rtn.start.set(Calendar.DATE, date*2)
			def diffMin = (interval.time.time - rtn.start.time.time) / 60000
			rtn.interval = Math.floor(diffMin / 60).intValue()
			rtn.range = 2880
			rtn.count = 48
		} else if (resolution == TWO_HOURS) { // ~48 2h intervals in a 4 day bucket
			rtn.start.set(Calendar.MINUTE, 0)
			rtn.start.set(Calendar.HOUR_OF_DAY, 0)
			def date = rtn.start.get(Calendar.DATE)
			date = Math.floor(date / 4).intValue()
			rtn.start.set(Calendar.DATE, date*4)
			def diffMin = (interval.time.time - rtn.start.time.time) / 60000
			rtn.interval = Math.floor(diffMin / 120).intValue()
			rtn.range = 1440*4
			rtn.count = 48
		} else if (resolution == FOUR_HOURS) { // ~42 4h intervals in a 7 day bucket
			rtn.start.set(Calendar.MINUTE, 0)
			rtn.start.set(Calendar.HOUR_OF_DAY, 0)
			def date = rtn.start.get(Calendar.DATE)
			date = Math.floor(date / 7).intValue()
			rtn.start.set(Calendar.DATE, date*7)
			def diffMin = (interval.time.time - rtn.start.time.time) / 60000
			rtn.range = 600
			rtn.interval = Math.floor(diffMin / 240).intValue()
			rtn.range = 1440*7
			rtn.count = 42
		} else if (resolution == TWELVE_HOURS) { // ~60 12h intervals in a 1 month bucket
			rtn.start.set(Calendar.MINUTE, 0)
			rtn.start.set(Calendar.HOUR_OF_DAY, 0)
			rtn.start.set(Calendar.DATE, 1)
			def diffMin = (interval.time.time - rtn.start.time.time) / 60000
			rtn.interval = Math.floor(diffMin / 720).intValue()
			rtn.range = 1440 * rtn.start.getActualMaximum(Calendar.DAY_OF_MONTH)
			rtn.count = rtn.range / 720
		} else if (resolution == ONE_DAY) { // ~30 24h intervals in a 1 month bucket
			rtn.start.set(Calendar.MINUTE, 0)
			rtn.start.set(Calendar.HOUR_OF_DAY, 0)
			rtn.start.set(Calendar.DATE, 1)
			def diffMin = (interval.time.time - rtn.start.time.time) / 60000
			rtn.interval = Math.floor(diffMin / 1440).intValue()
			rtn.range = 1440 * rtn.start.getActualMaximum(Calendar.DAY_OF_MONTH)
			rtn.count = rtn.range / 1440
		} else {
			log.warn('timeseries resolution is invalid: ' + resolution)

		}
		rtn.start = rtn.start.time
		return rtn.interval != null ? rtn : null
	}

	/*
		config:
		grails.plugins.timeseries.aggregates = ["{resolution name}": {days to expire}]
		only thing diff from mterics is that count, sum is tracked for each interval
	*/
	protected getAggregateStartsAndIntervals(Date timestamp, groovy.util.ConfigObject config) {
		def rtn = [],
			resolution = config?.containsKey('resolution') ? config?.resolution : DEFAULT_RESOLUTION,
			b

		if (config.containsKey('aggregates')) {
			def aggs = config.aggregates
			if (aggs instanceof Map) {
				aggs.each {k, v->
					if (SUPPORTED_RESOLUTIONS_INTERVAL_SIZE[k] > SUPPORTED_RESOLUTIONS_INTERVAL_SIZE[resolution]) {
						b = getStartAndInterval(timestamp, k)
						if (b) rtn << b
					}
				}
			} else {
				log.warn('grails.plugins.timeseries.aggregates configuration in invalid')
			}
		}
		rtn
	}

	protected getAggregateMillisecondExpirations(groovy.util.ConfigObject config) {
		def rtn = [:]
		if (config.containsKey('aggregates')) {
			def aggs = config.aggregates
			if (config.containsKey('_aggregateExpirations')) {
				rtn = config['_aggregateExpirations']
			} else {
				if (aggs instanceof Map) {
					aggs.each {k, v->
						rtn[k] = parseDuration(v)
					}
					config['_aggregateExpirations'] = rtn
				} else {
					log.warn('grails.plugins.timeseries.aggregates configuration in invalid')
				}
			}
		}
		rtn
	}

}