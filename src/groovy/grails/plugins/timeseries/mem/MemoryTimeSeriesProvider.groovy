package grails.plugins.timeseries.mem

import grails.plugins.timeseries.*
import grails.converters.*
import groovy.transform.PackageScope
/*
data model for this provider is really a model for a document-based storage provider
it would be more efficient to store data in a less structured form in a SortedMap kind of thing  
*/
class MemoryTimeSeriesProvider extends AbstractTimeSeriesProvider {

	@PackageScope Map internalData

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
		def expiration = config.containsKey('expiration') ? parseDuration(config.expiration) : DEFAULT_EXPIRATION, // default to a days worth
			aggExpirations = getAggregateMillisecondExpirations(config),
			now = System.currentTimeMillis(),
			oldest = now - expiration,
			aggOldest

		println '****OLDEST:'+new Date(oldest)
		// age out old data
		internalData.each {itemKey, db->
			// remove old measurements
			def rmv = []
			db.__m.each {ts, doc->
				if (oldest > doc.__e.time) {
					println '>>>REMOVING [' +ts+']'+doc.__s+'-'+doc.__e
					rmv << ts 
				}
			}
			rmv.each { db.__m.remove(it) }
			// remove old aggregates
			db.__a.each {res, docs->
				rmv = []
				aggOldest = now - aggExpirations[res]
				docs.each {ts, doc-> if (aggOldest > doc.__e.time) rmv << ts }
				rmv.each { docs.remove(it) }
			}
		}			
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
		internalData[referenceId] = internalData[referenceId] ?: [__i:((Long)(startAndInterval.interval * 1000)), __r:startAndInterval.resolution,__m:[:].asSynchronized(),__a:[:].asSynchronized()].asSynchronized()
		def refidData = internalData[referenceId]
		def storedMetrics = refidData.__m,
			storedAggregates = refidData.__a
		storedMetrics[startAndInterval.start] =  storedMetrics[startAndInterval.start] ?: [__s:startAndInterval.start, __n:startAndInterval.count, __e:new Date(startAndInterval.start.time + (startAndInterval.range * 60000))].asSynchronized()
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
				aggRes[agg.start] = aggRes[agg.start] ?: [__s:agg.start, __n:agg.count, __e:new Date(agg.start.time + (agg.range * 60000))].asSynchronized()
				def currentAgg = aggRes[agg.start]
				currentAgg[k] = currentAgg[k] ?: [__i:0i, __t:0d, __v: new ArrayList(agg.count.intValue()).asSynchronized()]
				currentAgg[k].__v[agg.interval] = currentAgg[k].__v[agg.interval] ?: [__t:0d,__i:0i]
				if (prevExists) {
					currentAgg.__i--
					currentAgg[k].__t = currentAgg[k].__t - prevValue
					currentAgg[k].__v[agg.interval].__i--
					currentAgg[k].__v[agg.interval].__t = currentAgg[k].__v[agg.interval].__t.__t - prevValue
				}
				currentAgg[k].__i++
				currentAgg[k].__t += v
				currentAgg[k].__v[agg.interval].__i++
				currentAgg[k].__v[agg.interval].__t += v
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
	Map getMetrics(Date start, Date end, String referenceIdQuery, String metricNameQuery, Map<String, Object> options, groovy.util.ConfigObject config) {
		def rtn = [:],
			res
		internalData.each {refId, db->
			if (referenceIdQuery == null || refId =~ referenceIdQuery) {
				res = db.__r
				rtn[refId] = rtn[refId] ?: [:] 
				db?.__m?.each {timestamp, metrics->
					def interval = SUPPORTED_RESOLUTIONS_INTERVAL_SIZE[res]
					if (timestamp > start && timestamp < end) {
						metrics.each {metricName, map ->
							if (!metricName.startsWith('__') && (metricNameQuery == null || metricName =~ metricNameQuery)) {
								def f = false
								map.__v.eachWithIndex {v,i->
									def intervalTimestamp = new Date(timestamp.time + (i*interval))
									if (intervalTimestamp > start && (v != null || f)) {
										rtn[refId][metricName] = rtn[refId][metricName] ?: []
										def rec = [timestamp:intervalTimestamp, value:v]
										if (options?.includeEndDate) rec.end = Date(timestamp.time + (i*interval) + interval)
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

		def items =[]
		rtn.each {k, v->
			def tmp = [referenceId: k, series:[]]
			v.each {m, vals->
				tmp.series << [name:m, values:vals]
			}
			items << tmp
		}
		[resolutionName:res, resolutionInterval:SUPPORTED_RESOLUTIONS_INTERVAL_SIZE[res]/1000, start:start, end:end, items:items]

	}

	@Override
	Map getMetricAggregates(String resolution, Date start, Date end, String referenceIdQuery, String metricNameQuery, Map<String, Object> options, groovy.util.ConfigObject config) {
		def rtn = [:]
		internalData.each {refId, db->
			if (referenceIdQuery == null || refId =~ referenceIdQuery) {
				rtn[refId] = rtn[refId] ?: [:] 
				def agg = db.__a[resolution]
				agg?.each {timestamp, metrics->
					def interval = SUPPORTED_RESOLUTIONS_INTERVAL_SIZE[resolution]
					if (timestamp > start && timestamp < end) {
						metrics.each {metricName, map ->
							if (!metricName.startsWith('__') && (metricNameQuery == null || metricName =~ metricNameQuery)) {
								def f = false
								map.__v.eachWithIndex {v,i->
									def intervalTimestamp = new Date(timestamp.time + (i*interval))
									if (intervalTimestamp > start && (v != null || f)) {
										rtn[refId][metricName] = rtn[refId][metricName] ?: []
										def rec = [start:intervalTimestamp, sum:v.__t, count: v.__i, average: (v.__i > 0 ? (v.__t / v.__i) : 0d) ]
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
		def items =[]
		rtn.each {k, v->
			def tmp = [referenceId: k, series:[]]
			v.each {m, vals->
				tmp.series << [name:m, values:vals]
			}
			items << tmp
		}
		[resolutionName:resolution, resolutionInterval:SUPPORTED_RESOLUTIONS_INTERVAL_SIZE[resolution]/1000, start:start, end:end, items:items]
	}


}