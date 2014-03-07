package grails.plugins.timeseries.mem

import grails.plugins.timeseries.*
import grails.converters.*

/*
data model for this provider is really a model for a document-based storage provider
it would be more efficient to store data in a less structured form in a SortedMap kind of thing  
*/
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
/*
{"testSaveMetrics": {
   "__m": {"Wed Mar 05 17:44:00 PST 2014": {
      "__n": 60,
      "poop": {
         "__i": 1,
         "__v": [
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            100
         ],
         "__t": 100
      },
      "__e": "2014-03-06T01:45:00Z",
      "__s": "2014-03-06T01:44:00Z"
   }},
   "__r": "1s",
   "__a": {}
}}
*/
	@Override
	Map<String, Map<String, List<Map<String, Object>>>> getMetrics(Date start, Date end, String referenceIdQuery, String metricNameQuery, Map<String, Object> options, groovy.util.ConfigObject config) {
		// exact match only for the moment
		def rtn = [:],
			res
		internalData.each {refId, db->
			if (referenceIdQuery == null || refId =~ referenceIdQuery) {
				res = db.__r
				rtn[refId] = rtn[refId] ?: [:] 
				db?.__m?.each {timestamp, metrics->
					println 'Testing '+timestamp
					def interval = SUPPORTED_RESOLUTIONS_SIZE[res]
					if (timestamp > start && timestamp < end) {
						println 'passed '+timestamp
						metrics.each {metricName, map ->
							if (!metricName.startsWith('__') && (metricNameQuery == null || metricName =~ metricNameQuery)) {
								def f = false
								map.__v.eachWithIndex {v,i->
									if (v != null || f) {
										def intervalTimestamp = new Date(timestamp.time + (i*interval))
										rtn[refId][metricName] = rtn[refId][metricName] ?: []
										def rec = [s:intervalTimestamp, v:v]
										if (options?.includeEndDate) rec.e = Date(timestamp.time + (i*interval) + interval)
										rtn[refId][metricName] << rec
										f = true
									}
								}
							}
						}

					}
				}				
			}
		}	
		rtn
	}

	@Override
	Map<String, Map<String, List<Map<String, Object>>>> getMetricAggregates(String resolution, Date start, Date end, String referenceIdQuery, String metricNameQuery, Map<String, Object> options, groovy.util.ConfigObject config) {

	}


}