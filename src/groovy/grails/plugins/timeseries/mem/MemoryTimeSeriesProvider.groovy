package grails.plugins.timeseries.mem

import grails.plugins.timeseries.*
import grails.converters.*

class MemoryTimeSeriesProvider extends AbstractTimeSeriesProvider {

	private Map internalData

	private String dataPath
	private Boolean persist

	public MemoryTimeSeriesProvider() {
		this(true, null)
	}

	public MemoryTimeSeriesProvider(Boolean persist, String dataPath) {
		super()
		this.dataPath = (dataPath ?: "data")
		this.persist = persist != false
		this.dataPath = this.dataPath.endsWith('/') ? this.dataPath : (this.dataPath + '/')
		if (this.persist) {
			try {
				FileInputStream fileIn = new FileInputStream(this.dataPath + 'MemoryTimeSeriesProvider.ser')
				ObjectInputStream inp = new ObjectInputStream(fileIn)
				internalData = (Map) inp.readObject()
				inp.close()
				fileIn.close()
			} catch (java.io.FileNotFoundException f) {
			} catch(Exception i) {
				i.printStackTrace()
			} 
		}
		internalData = internalData ?: [:].asSynchronized()
	}

	@Override
	void manageStorage(groovy.util.ConfigObject config) {
		if (this.persist) {
			try {
				def d1= new File(dataPath)
				d1.mkdir() 
				FileOutputStream fileOut = new FileOutputStream(dataPath + 'MemoryTimeSeriesProvider.ser')
				ObjectOutputStream out = new ObjectOutputStream(fileOut)
				out.writeObject(internalData)
				out.close()
				fileOut.close()
			} catch(IOException i) {
				log.error(i)
				i.printStackTrace()
			}		
		}
	}

	@Override
	void init(groovy.util.ConfigObject config) {

	}

	@Override
	void shutDown(groovy.util.ConfigObject config) {
		manageStorage(config)
	}

	@Override
	void flush(groovy.util.ConfigObject config) {
		internalData.clear()
		manageStorage(config)
	}


	@Override
	String getName() {
		return 'memory'
	}

	@Override
	String toString() {
		if (internalData) {
			return new JSON(internalData).toString(true)
		} else {
			return super.toString()
		}
	}

	@Override
	void saveMetrics(String referenceId, Map<String, Double> metrics, Date timestamp, groovy.util.ConfigObject config) {
		def startAndInterval = getMetricStartAndInterval(timestamp, config),
			aggregates = getAggregateStartsAndIntervals(timestamp, config)
		internalData[referenceId] = internalData[referenceId] ?: [__r:startAndInterval.resolution,__m:[:].asSynchronized(),__a:[:].asSynchronized()].asSynchronized()
		def refidData = internalData[referenceId]
		def storedMetrics = refidData.__m,
			storedAggregates = refidData.__a
		storedMetrics[startAndInterval.start] =  storedMetrics[startAndInterval.start] ?: [__s:startAndInterval.start, __n:startAndInterval.count, __e:new Date(startAndInterval.start.time + (startAndInterval.range * 1000))].asSynchronized()
		def currentMetrics = storedMetrics[startAndInterval.start],
			prevExists = false,
			prevValue = 0
		metrics.each {k, v->
			prevExists = false
			currentMetrics[k] = currentMetrics[k] ?: [__i:0i, __t:0d, __v: new ArrayList(startAndInterval.count).asSynchronized()]
			if (currentMetrics[k].__v[startAndInterval.interval]) {
				prevExists = true
				prevValue = currentMetrics[k].__v[startAndInterval.interval]
				currentMetrics[k].__i--
				currentMetrics[k].__t = currentMetrics[k].__t - prevValue
			}
			currentMetrics[k].__i++
			currentMetrics[k].__t += v
			currentMetrics[k].__v[startAndInterval.interval] = v
			aggregates?.each {agg->
				storedAggregates[agg.resolution] = storedAggregates[agg.resolution] ?: [:].asSynchronized()
				def aggRes = storedAggregates[agg.resolution]
				aggRes[agg.start] = aggRes[agg.start] ?: [__s:agg.start, __n:agg.count, __e:new Date(agg.start.time + (agg.range * 1000))].asSynchronized()
				def currentAgg = aggRes[agg.start]
				currentAgg[k] = currentAgg[k] ?: [__i:0i, __t:0d]
				if (prevExists) {
					currentAgg.__i--
					currentAgg[k].__t = currentAgg[k].__t - prevValue
				}
				currentAgg[k].__i++
				currentAgg[k].__t += v
			}
		}
	}

	@Override
	void bulkSaveMetrics(String referenceId, List<Map<Date, Map<String, Double>>> metricsByTime, groovy.util.ConfigObject config) {
		metricsByTime.each {timestamp, metrics->
			saveMetrics(referenceId, metrics, timestamp, config)
		}
	}

	@Override
	Map<String, Map<Date, Map<String, Double>>> getMetrics(Date start, Date end, String referenceIdQuery, String metricNameQuery, groovy.util.ConfigObject config) {

	}

	@Override
	Map<String, Map<Date, Map<String, Map<String, Double>>>> getMetricAggregates(String resolution, Date start, Date end, 
		String referenceIdQuery, String metricNameQuery, groovy.util.ConfigObject config) {

	}


}