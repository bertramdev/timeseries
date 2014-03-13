package grails.plugins.timeseries.mem

import groovy.util.GroovyTestCase
import grails.converters.*
import grails.plugins.timeseries.*

class TimeSeriesIntegrationTests extends GroovyTestCase {
	def timeSeriesService
	def grailsApplication
	def setup() {
	}

	def cleanup() {
	}

	private getTestDate() {
		def c = new GregorianCalendar()
		c.set( Calendar.ERA, GregorianCalendar.AD )
		c.set( Calendar.YEAR, 2013 )
		c.set( Calendar.MONTH, Calendar.OCTOBER )
		c.set( Calendar.DATE, 31 )
		c.set( Calendar.HOUR, 10 )
		c.set( Calendar.SECOND, 31 )
		c.set( Calendar.MINUTE, 31 )
		c.set( Calendar.MILLISECOND, 0 )

		c.time
	}

	void testStartIntervalStuff() {
		def provider = new TestProvider(),
			now = getTestDate()

		println now
		assert provider.test('1s', now).interval == 31
		assert provider.test('10s', now).interval == 9
		assert provider.test('30s', now).interval == 3
		assert provider.test('1m', now).interval == 31
		assert provider.test('15m', now).interval == 63
		assert provider.test('30m', now).interval == 21
		assert provider.test('1h', now).interval == 34
		assert provider.test('2h', now).interval == 41
		assert provider.test('4h', now).interval == 20
		assert provider.test('12h', now).interval == 60
		assert provider.test('1d', now).interval == 30
		assert provider.testAggs(now)[0].interval == 31 && provider.testAggs(now)[1].interval == 34 && provider.testAggs(now)[2].interval == 30
	}

	void testSaveMetrics() {
		timeSeriesService.flush()
		def now = getTestDate()
		println now
		grailsApplication.config.grails.plugins.timeseries.poop.resolution = AbstractTimeSeriesProvider.ONE_SECOND
		timeSeriesService.saveMetric('testSaveMetrics', 'poop', 100d, now)
		println timeSeriesService.getProvider()
	}


	void testSaveMetricsWithHourlyAggregate() {
		timeSeriesService.flush()
		def now = getTestDate()
		println now
		grailsApplication.config.grails.plugins.timeseries.poop.resolution = AbstractTimeSeriesProvider.ONE_SECOND
		grailsApplication.config.grails.plugins.timeseries.poop.aggregates = ['1h':'1d']
		timeSeriesService.saveMetric('testSaveMetrics', 'poop', 100d, now)
		println timeSeriesService.getProvider()
	}


	void testSaveMetricsOverwrite() {
		timeSeriesService.flush()
		def now = getTestDate()
		println now
		grailsApplication.config.grails.plugins.timeseries.poop.resolution = AbstractTimeSeriesProvider.ONE_SECOND
		timeSeriesService.saveMetric('testSaveMetricsOverwrite', 'poop', 100d, now)
		timeSeriesService.saveMetric('testSaveMetricsOverwrite', 'poop', 200d, now)
		println timeSeriesService.getProvider()
	}


	void testSaveMetricsIrregular() {
		timeSeriesService.flush()
		def now = getTestDate()
		[11,17,5,7].each {
			//println now
			grailsApplication.config.grails.plugins.timeseries.poop.resolution = AbstractTimeSeriesProvider.ONE_SECOND
			timeSeriesService.saveMetric('testSaveMetricsIrregular', 'poop', it, now)
			now = new Date(now.time + (it*1000))
		}
		println timeSeriesService.getProvider()
	}

	void testSaveMetricsRegular() {
		timeSeriesService.flush()
		def now = getTestDate()
		(1..15).each {
			//println now
			grailsApplication.config.grails.plugins.timeseries.poop.resolution = AbstractTimeSeriesProvider.ONE_SECOND
			timeSeriesService.saveMetric('testSaveMetricsRegular', 'poop', it, now)
			now = new Date(now.time + 1000)
		}
		println timeSeriesService.getProvider()
	}
	void testSaveMetricsRegularWithAggregates() {
		timeSeriesService.flush()
		grailsApplication.config.grails.plugins.timeseries.met1.aggregates = ['1m':'7d']
		def now = getTestDate()
		(1..121).each {
			//println now
			grailsApplication.config.grails.plugins.timeseries.met1.resolution = AbstractTimeSeriesProvider.ONE_SECOND
			timeSeriesService.saveMetric('testSaveMetricsRegularWithAggregates', 'met1', it, now)
			now = new Date(now.time + 1000)
		}
		println timeSeriesService.getProvider()
	}


	void testSaveMetricsRegularWithGet() {
		timeSeriesService.flush()
		def now = getTestDate()
		(1..35).each {
			//println now
			grailsApplication.config.grails.plugins.timeseries.met1.resolution = AbstractTimeSeriesProvider.ONE_SECOND
			grailsApplication.config.grails.plugins.timeseries.met2.resolution = AbstractTimeSeriesProvider.ONE_SECOND
			timeSeriesService.saveMetrics('testSaveMetricsRegularWithGet', ['met1':it, 'met2':(121-it)], now)
			now = new Date(now.time + 1000)
		}
		println new JSON(timeSeriesService.getMetrics(new Date(0), new Date(System.currentTimeMillis() + 180000l))).toString(true)
	}

	void testSaveMetricsRegularWithAggregatesWithGet() {
		timeSeriesService.flush()
		grailsApplication.config.grails.plugins.timeseries.met1.aggregates = ['1m':'7d']
		grailsApplication.config.grails.plugins.timeseries.met2.aggregates = ['1m':'7d']
		grailsApplication.config.grails.plugins.timeseries.met1.resolution = AbstractTimeSeriesProvider.ONE_SECOND
		grailsApplication.config.grails.plugins.timeseries.met2.resolution = AbstractTimeSeriesProvider.ONE_SECOND
		def now = getTestDate()
		(1..121).each {
			//println now
			timeSeriesService.saveMetrics('testSaveMetricsRegularWithAggregatesWithGet', ['met1':it, 'met2':(121-it)], now)
			timeSeriesService.saveMetrics('testSaveMetricsRegularWithAggregatesWithGet2', ['met1':it*1.23, 'met2':(121-it)*1.23], now)
			now = new Date(now.time + 1000)
		}
		println new JSON(timeSeriesService.getMetricAggregates('1m',new Date(0), new Date(System.currentTimeMillis() + 180000l))).toString(true)
	}

	void testManageStorage() {
		timeSeriesService.flush()
		grailsApplication.config.grails.plugins.timeseries.met1.resolution = AbstractTimeSeriesProvider.ONE_HOUR
		grailsApplication.config.grails.plugins.timeseries.met1.expiration = '2d'
		grailsApplication.config.grails.plugins.timeseries.met1.aggregates = ['1d':'2d']
		def now = new Date(),
			old = now - 5,
			it = 1
		while (true) {
//			println 'Saving '+old
			timeSeriesService.saveMetric('testManageStorage', 'met1', it, old)
			old = new Date(old.time + 3600000l)
			if (old > now) break
			it++
		}
		println new JSON(timeSeriesService.getMetrics(new Date(0), new Date(System.currentTimeMillis() + 180000l))).toString(true)
		timeSeriesService.manageStorage()
		println new JSON(timeSeriesService.getMetrics(new Date(0), new Date(System.currentTimeMillis() + 180000l))).toString(true)
	}

}


class TestProvider extends AbstractTimeSeriesProvider {
	@Override
 	String getName() { return 'foo'}
	@Override
	void saveMetrics(String referenceId, Map<String, Double> metrics, Date timestamp, groovy.util.ConfigObject config) {}
	@Override
	void bulkSaveMetrics(String referenceId, List<Map<Date, Map<String, Double>>> metricsByTime, groovy.util.ConfigObject config) {}
	@Override
	Map getMetrics(Date start, Date end, String referenceIdQuery, String metricNameQuery, Map<String, Object> options, groovy.util.ConfigObject config) {return null}
	@Override
	Map getMetricAggregates(String bucketName, Date start, Date end, String referenceIdQuery, String metricNameQuery, Map<String, Object> options, groovy.util.ConfigObject config) {return null}
	def test(resolution, now = new Date()) {
		def conf = new groovy.util.ConfigObject()
		conf.poop.resolution = resolution
		return getMetricStartAndInterval('poop',now, conf)
	}
	def testAggs( now = new Date()) {
		def conf = new groovy.util.ConfigObject()
		conf.poop.resolution = '1s'
		conf.poop.aggregates = ['1m':7, '1h':14, '1d': 90]
		return getAggregateStartsAndIntervals('poop', now, conf)
	}
}