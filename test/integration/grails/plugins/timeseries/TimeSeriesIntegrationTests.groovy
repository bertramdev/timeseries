package grails.plugins.timeseries

import groovy.util.GroovyTestCase
import grails.plugins.timeseries.mem.MemoryTimeSeriesProvider
import grails.converters.*

class TimeSeriesIntegrationTests extends GroovyTestCase {
	def timeSeriesService
	def grailsApplication
	def setup() {
	}

	def cleanup() {
	}

	void testStartIntervalStuff() {
		def provider = new TestProvider(),
			now = new Date()
		println now
		println provider.test('1s', now)
		println provider.test('10s', now)
		println provider.test('30s', now)
		println provider.test('1m', now)
		println provider.test('15m', now)
		println provider.test('30m', now)
		println provider.test('1h', now)
		println provider.test('2h', now)
		println provider.test('4h', now)
		println provider.test('12h', now)
		println provider.test('1d', now)
		println provider.testAggs(now)
	}

	void testSaveMetrics() {
		timeSeriesService.flush()
		def now = new Date()
		println now
		grailsApplication.config.grails.plugins.timeseries.resolution = AbstractTimeSeriesProvider.ONE_SECOND
		timeSeriesService.saveMetric('testSaveMetrics', 'poop', 100d, now)
		println timeSeriesService.getProvider()
	}


	void testSaveMetricsWithHourlyAggregate() {
		timeSeriesService.flush()
		def now = new Date()
		println now
		grailsApplication.config.grails.plugins.timeseries.resolution = AbstractTimeSeriesProvider.ONE_SECOND
		timeSeriesService.saveMetric('testSaveMetrics', 'poop', 100d, now)
		println timeSeriesService.getProvider()
	}


	void testSaveMetricsOverwrite() {
		timeSeriesService.flush()
		def now = new Date()
		println now
		grailsApplication.config.grails.plugins.timeseries.resolution = AbstractTimeSeriesProvider.ONE_SECOND
		timeSeriesService.saveMetric('testSaveMetricsOverwrite', 'poop', 100d, now)
		timeSeriesService.saveMetric('testSaveMetricsOverwrite', 'poop', 200d, now)
		println timeSeriesService.getProvider()
	}


	void testSaveMetricsIrregular() {
		timeSeriesService.flush()
		def now = new Date()
		[11,17,5,7].each {
			//println now
			grailsApplication.config.grails.plugins.timeseries.resolution = AbstractTimeSeriesProvider.ONE_SECOND
			timeSeriesService.saveMetric('testSaveMetricsIrregular', 'poop', it, now)
			now = new Date(now.time + (it*1000))
		}
		println timeSeriesService.getProvider()
	}

	void testSaveMetricsRegular() {
		timeSeriesService.flush()
		def now = new Date()
		(1..15).each {
			//println now
			grailsApplication.config.grails.plugins.timeseries.resolution = AbstractTimeSeriesProvider.ONE_SECOND
			timeSeriesService.saveMetric('testSaveMetricsRegular', 'poop', it, now)
			now = new Date(now.time + 1000)
		}
		println timeSeriesService.getProvider()
	}
	void testSaveMetricsRegularWithAggregates() {
		timeSeriesService.flush()
		grailsApplication.config.grails.plugins.timeseries.aggregates = ['1m':7]
		def now = new Date()
		(1..121).each {
			//println now
			grailsApplication.config.grails.plugins.timeseries.resolution = AbstractTimeSeriesProvider.ONE_SECOND
			timeSeriesService.saveMetric('testSaveMetricsRegularWithAggregates', 'met1', it, now)
			now = new Date(now.time + 1000)
		}
		println timeSeriesService.getProvider()
	}


	void testSaveMetricsRegularWithGet() {
		timeSeriesService.flush()
		def now = new Date()
		(1..35).each {
			//println now
			grailsApplication.config.grails.plugins.timeseries.resolution = AbstractTimeSeriesProvider.ONE_SECOND
			timeSeriesService.saveMetrics('testSaveMetricsRegularWithGet', ['met1':it, 'met2':(121-it)], now)
			now = new Date(now.time + 1000)
		}
		println new JSON(timeSeriesService.getMetrics(new Date(0), new Date(System.currentTimeMillis() + 180000l))).toString(true)
	}

	void testSaveMetricsRegularWithAggregatesWithGet() {
		timeSeriesService.flush()
		grailsApplication.config.grails.plugins.timeseries.aggregates = ['1m':7]
		def now = new Date()
		(1..121).each {
			//println now
			grailsApplication.config.grails.plugins.timeseries.resolution = AbstractTimeSeriesProvider.ONE_SECOND
			timeSeriesService.saveMetrics('testSaveMetricsRegularWithAggregatesWithGet', ['met1':it, 'met2':(121-it)], now)
			timeSeriesService.saveMetrics('testSaveMetricsRegularWithAggregatesWithGet2', ['met1':it*1.23, 'met2':(121-it)*1.23], now)
			now = new Date(now.time + 1000)
		}
		println new JSON(timeSeriesService.getMetricAggregates('1m',new Date(0), new Date(System.currentTimeMillis() + 180000l))).toString(true)
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
		conf.resolution = resolution
		return getMetricStartAndInterval(now, conf)
	}
	def testAggs( now = new Date()) {
		def conf = new groovy.util.ConfigObject()
		conf.resolution = '1s'
		conf.aggregates = ['1m':7, '1h':14, '1d': 90]
		return getAggregateStartsAndIntervals(now, conf)
	}
}