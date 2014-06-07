package grails.plugins.timeseries

import java.text.ParseException
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import java.util.regex.Pattern

import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.plugins.support.aware.GrailsApplicationAware

abstract class AbstractTimeSeriesProvider implements TimeSeriesProvider, GrailsApplicationAware {

	protected GrailsApplication grailsApplication

	static final long DEFAULT_EXPIRATION = 86400000l

	private static Pattern p = Pattern.compile("(\\d+)?[dhms]")

	static Long parseDuration(duration) throws ParseException {
		if (duration instanceof Long) return duration
		duration = duration.replaceAll(' ','')

		Matcher m = p.matcher(duration)

		Long milliseconds = 0

		if (!m.find()) {
			throw new ParseException("Cannot parse duration " + duration, 0)
		}

		def per = duration[duration.length()-1]
		int inc = Integer.parseInt(m.group(1))

		if (per == 'd') milliseconds += TimeUnit.MILLISECONDS.convert(inc, TimeUnit.DAYS)
		else if (per == 'h') milliseconds += TimeUnit.MILLISECONDS.convert(inc, TimeUnit.HOURS)
		else if (per == 'm') milliseconds += TimeUnit.MILLISECONDS.convert(inc,TimeUnit.MINUTES)
		else if (per == 's') milliseconds += TimeUnit.MILLISECONDS.convert(inc,TimeUnit.SECONDS)
		else throw new ParseException("Cannot parse duration " + duration, 0)

		return milliseconds
	}

	void setGrailsApplication(GrailsApplication grailsApplication) {
		this.grailsApplication = grailsApplication
	}

	protected getConfig() {
		grailsApplication.config
	}

	void flush(ConfigObject config) {}

	void init(ConfigObject config) {}

	void shutdown(ConfigObject config) {}

	void manageStorage(ConfigObject config) {}

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

	protected String DEFAULT_RESOLUTION = ONE_MINUTE

	protected getCounterStartAndInterval(String counterName, Date timestamp, ConfigObject config) {
		def resolution = config[counterName].containsKey('resolution') ? config[counterName].resolution : ONE_MINUTE
		return getStartAndInterval(timestamp, resolution)
	}

	protected getMetricStartAndInterval(String metricName, Date timestamp, ConfigObject config) {
		def resolution = config[metricName].containsKey('resolution') ? config[metricName].resolution : ONE_MINUTE
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
			rtn.intervalSecs = 1l
			rtn.range = 60
			rtn.count = 60
			rtn.end = new Date(rtn.start.time.time + 60000l)
		} else if (resolution == TEN_SECONDS) { // ~60 10s intervals in a 10m bucket
			def min = Math.floor(rtn.start.get(Calendar.MINUTE) / 10)
			rtn.intervalSecs = 10l
			rtn.start.set(Calendar.MINUTE, min.intValue() * 10)
			rtn.end = new Date(rtn.start.time.time + 600000l)
			def diffSec = (interval.time.time - rtn.start.time.time) / 1000
			rtn.interval = Math.floor(diffSec / 10).intValue()
			rtn.range = 600
			rtn.count = 60
		} else if (resolution == THIRTY_SECONDS) { // ~60 30s intervals in a 30m bucket
			def min = Math.floor(rtn.start.get(Calendar.MINUTE) / 30)
			rtn.intervalSecs = 30l
			rtn.start.set(Calendar.MINUTE, min.intValue() * 30)
			rtn.end = new Date(rtn.start.time.time + 1800000l)
			def diffSec = (interval.time.time - rtn.start.time.time) / 1000
			rtn.interval = Math.floor(diffSec / 30).intValue()
			rtn.range = 1800
		} else if (resolution == ONE_MINUTE) { // ~60 1m intervals in a 1 hour bucket
			def min = Math.floor(rtn.start.get(Calendar.MINUTE) / 60)
			rtn.intervalSecs = 60l
			rtn.start.set(Calendar.MINUTE, min.intValue() * 60)
			rtn.end = new Date(rtn.start.time.time + 3600000l)
			def diffSec = (interval.time.time - rtn.start.time.time) / 1000
			rtn.interval = Math.floor(diffSec / 60).intValue()
			rtn.range = 3600
			rtn.count = 60
		} else if (resolution == FIFTEEN_MINUTES) { // ~96 15m intervals in a 1 day bucket
			rtn.intervalSecs = 900l
			rtn.start.set(Calendar.MINUTE, 0)
			rtn.start.set(Calendar.HOUR_OF_DAY, 0)
			rtn.end = new Date(rtn.start.time.time + 86400000l)
			def min = (interval.get(Calendar.HOUR_OF_DAY) * 60) +interval.get(Calendar.MINUTE)
			rtn.interval = Math.floor(min / 15).intValue()
			rtn.range = 1440
	 		rtn.count = 96
		} else if (resolution == THIRTY_MINUTES) { // ~48 30m intervals in a 1 day bucket
			rtn.intervalSecs = 1800l
			rtn.start.set(Calendar.MINUTE, 0)
			rtn.start.set(Calendar.HOUR_OF_DAY, 0)
			rtn.end = new Date(rtn.start.time.time + 86400000l)
			def min = (interval.get(Calendar.HOUR_OF_DAY) * 60) +interval.get(Calendar.MINUTE)
			rtn.interval = Math.floor(min / 30).intValue()
			rtn.range = 1440
			rtn.count = 48
		} else if (resolution == ONE_HOUR) { // ~48 1h intervals in a 2 day bucket
			rtn.intervalSecs = 3600l
			rtn.start.set(Calendar.MINUTE, 0)
			rtn.start.set(Calendar.HOUR_OF_DAY, 0)
			rtn.end = new Date(rtn.start.time.time + 172800000l)

			def date = rtn.start.get(Calendar.DATE)
			date = Math.floor(date / 2).intValue()
			rtn.start.set(Calendar.DATE, date*2)
			def diffMin = (interval.time.time - rtn.start.time.time) / 60000
			rtn.interval = Math.floor(diffMin / 60).intValue()
			rtn.range = 2880
			rtn.count = 48
		} else if (resolution == TWO_HOURS) { // ~48 2h intervals in a 4 day bucket
			rtn.intervalSecs = 7200l
			rtn.start.set(Calendar.MINUTE, 0)
			rtn.start.set(Calendar.HOUR_OF_DAY, 0)
			rtn.end = new Date(rtn.start.time.time + 345600000l)
			def date = rtn.start.get(Calendar.DATE)
			date = Math.floor(date / 4).intValue()
			rtn.start.set(Calendar.DATE, date*4)
			def diffMin = (interval.time.time - rtn.start.time.time) / 60000
			rtn.interval = Math.floor(diffMin / 120).intValue()
			rtn.range = 1440*4
			rtn.count = 48
		} else if (resolution == FOUR_HOURS) { // ~42 4h intervals in a 7 day bucket
			rtn.intervalSecs = 14400l
			rtn.start.set(Calendar.MINUTE, 0)
			rtn.start.set(Calendar.HOUR_OF_DAY, 0)
			rtn.end = new Date(rtn.start.time.time + 604800000l)
			def date = rtn.start.get(Calendar.DATE)
			date = Math.floor(date / 7).intValue()
			rtn.start.set(Calendar.DATE, date*7)
			def diffMin = (interval.time.time - rtn.start.time.time) / 60000
			rtn.range = 600
			rtn.interval = Math.floor(diffMin / 240).intValue()
			rtn.range = 1440*7
			rtn.count = 42
		} else if (resolution == TWELVE_HOURS) { // ~60 12h intervals in a 1 month bucket
			rtn.intervalSecs = 43200l
			rtn.start.set(Calendar.MINUTE, 0)
			rtn.start.set(Calendar.HOUR_OF_DAY, 0)
			rtn.start.set(Calendar.DATE, 1)
			def diffMin = (interval.time.time - rtn.start.time.time) / 60000
			rtn.interval = Math.floor(diffMin / 720).intValue()
			rtn.range = 1440 * rtn.start.getActualMaximum(Calendar.DAY_OF_MONTH)
			rtn.end = rtn.start.clone()
			rtn.end = rtn.end.time
			rtn.end.set(Calendar.DATE, rtn.start.getActualMaximum(Calendar.DAY_OF_MONTH))
			rtn.count = rtn.range / 720
		} else if (resolution == ONE_DAY) { // ~30 24h intervals in a 1 month bucket
			rtn.intervalSecs = 86400l
			rtn.start.set(Calendar.MINUTE, 0)
			rtn.start.set(Calendar.HOUR_OF_DAY, 0)
			rtn.start.set(Calendar.DATE, 1)
			rtn.range = 1440 * rtn.start.getActualMaximum(Calendar.DAY_OF_MONTH)
			def diffMin = (interval.time.time - rtn.start.time.time) / 60000
			rtn.interval = Math.floor(diffMin / 1440).intValue()
			rtn.end = rtn.start.clone()
			rtn.end.set(Calendar.DATE, rtn.start.getActualMaximum(Calendar.DAY_OF_MONTH))
			rtn.end = rtn.end.time
			rtn.count = rtn.start.getActualMaximum(Calendar.DAY_OF_MONTH)
		} else {
			log.warn('timeseries resolution is invalid: ' + resolution)

		}
		rtn.start = rtn.start.time
		return rtn.interval != null ? rtn : null
	}


	protected getCounterAggregateStartsAndIntervals(String counterName, Date timestamp, ConfigObject config) {
		def rtn = [],
			resolution = config[counterName].containsKey('resolution') ? config[counterName].resolution : DEFAULT_RESOLUTION,
			b

		if (config[counterName].containsKey('aggregates')) {
			def aggs = config.counters[counterName].aggregates
			if (aggs instanceof Map) {
				aggs.each {k, v->
					if (SUPPORTED_RESOLUTIONS_INTERVAL_SIZE[k] > SUPPORTED_RESOLUTIONS_INTERVAL_SIZE[resolution]) {
						b = getStartAndInterval(timestamp, k)
						if (b) rtn << b
					}
				}
			} else {
				log.warn('grails.plugins.timeseries.counter.aggregates configuration in invalid')
			}
		}
		rtn
	}

	/*
		config:
		grails.plugins.timeseries.aggregates = ["{resolution name}": {days to expire}]
		only thing diff from mterics is that count, sum is tracked for each interval
	*/
	protected getAggregateStartsAndIntervals(String metricName, Date timestamp, ConfigObject config) {
		def rtn = [],
			resolution = config[metricName].containsKey('resolution') ? config[metricName].resolution : DEFAULT_RESOLUTION,
			b

		if (config[metricName].containsKey('aggregates')) {
			def aggs = config[metricName].aggregates
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

	protected getCounterMillisecondExpirations(String counterName, ConfigObject config) {
		def ms = DEFAULT_EXPIRATION
		if (config[metricName].containsKey('expiration')) {
			try {
				ms = Long.parseLong(config.counters[metricName].expiration?.toString())
			} catch(e) {
				log.warn('grails.plugins.timeseries.counters.expiration configuration in invalid')
			}
		}
		ms
	}

	protected getMillisecondExpirations(String metricName, ConfigObject config) {
		def ms = DEFAULT_EXPIRATION
		if (config[metricName].containsKey('expiration')) {
			try {
				ms = Long.parseLong(config[metricName].expiration?.toString())
			} catch(e) {
				log.warn('grails.plugins.timeseries.expiration configuration in invalid')
			}
		}
		ms
	}

	protected getCounterAggregateMillisecondExpirations(String metricName, ConfigObject config) {
		def rtn = [:]
		if (config[metricName].containsKey('aggregates')) {
			def aggs = config.counters[metricName].aggregates
			if (config.counters.containsKey('_aggregateExpirations')) {
				rtn = config.counters['_aggregateExpirations']
			} else {
				if (aggs instanceof Map) {
					aggs.each {k, v->
						rtn[k] = parseDuration(v)
					}
					config['_aggregateExpirations'] = rtn
				} else {
					log.warn('grails.plugins.timeseries.counter.aggregates.expirations configuration in invalid')
				}
			}
		}
		rtn
	}

	protected getAggregateMillisecondExpirations(String metricName, ConfigObject config) {
		def rtn = [:]
		if (config[metricName].containsKey('aggregates')) {
			def aggs = config[metricName].aggregates
			if (config.containsKey('_aggregateExpirations')) {
				rtn = config['_aggregateExpirations']
			} else {
				if (aggs instanceof Map) {
					aggs.each {k, v->
						rtn[k] = parseDuration(v)
					}
					config['_aggregateExpirations'] = rtn
				} else {
					log.warn('grails.plugins.timeseries.aggregates.expirations configuration in invalid')
				}
			}
		}
		rtn
	}
}
