package grails.plugins.timeseries.mem

import grails.converters.JSON
import grails.plugins.timeseries.AbstractTimeSeriesProvider
import groovy.transform.PackageScope

/*
data model for this provider is really a model for a document-based storage provider
it would be more efficient to store data in a less structured form in a SortedMap kind of thing
*/
class MemoryTimeSeriesProvider extends AbstractTimeSeriesProvider {

	@PackageScope Map internalData

	private String dataPath
	private Boolean persist

	MemoryTimeSeriesProvider() {
		this(true, null)
	}

	MemoryTimeSeriesProvider(Boolean persist, String dataPath) {
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
			} catch (FileNotFoundException f) {
			} catch(Exception i) {
				log.error(i.message, i)
			}
		}
		internalData = internalData ?: [:].asSynchronized()
	}

	@Override
	void manageStorage(ConfigObject config) {
		long now = System.currentTimeMillis()
		// age out old data
		internalData.each {itemKey, db->
			// remove old measurements
			def rmv = []
			db.__m.each {metricName, doc->
				def expiration = config[metricName].containsKey('expiration') ? parseDuration(config[metricName].expiration) : DEFAULT_EXPIRATION, // default to a days worth
					oldest = now - expiration
				println('****OLDEST for '+ metricName + ':'+new Date(oldest))
				doc.__d.each {ts,doc2->
					if (oldest > doc2.__e.time) rmv << ts
				}
				rmv.each { doc.__d.remove(it) }

			}
			// remove old aggregates
			db.__a.each {metricName, doc->
				def aggExpirations = getAggregateMillisecondExpirations(metricName, config)
				aggExpirations.each {res, duration->
					rmv = []
					def aggOldest = now - duration
					println('****OLDEST for '+ metricName + ':'+res+ ':'+new Date(aggOldest))
					doc[res].each {ts, doc2->
						if (aggOldest > doc2.__e.time) rmv << ts
					}
					rmv.each { doc[res].remove(it) }
				}
			}
		}
		if (this.persist) {
			try {
				File d1 = new File(dataPath)
				d1.mkdir()
				OutputStream fileOut = new FileOutputStream(dataPath + 'MemoryTimeSeriesProvider.ser')
				ObjectOutputStream out = new ObjectOutputStream(fileOut)
				out.writeObject(internalData)
				out.close()
				fileOut.close()
			} catch(IOException i) {
				log.error(i.message, i)
			}
		}
	}

	@Override
	void shutDown(groovy.util.ConfigObject config) {
		manageStorage(config)
	}

	@Override
	void flush(ConfigObject config) {
		internalData.clear()
		manageStorage(config)
	}

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

	void saveCounters(String referenceId, Map<String, Double> counters, Date timestamp, ConfigObject config) {
		def startAndInterval,
			aggregates
		internalData[referenceId] = internalData[referenceId] ?: [__c:[:].asSynchronized(),_ca:[:].asSynchronized(),__m:[:].asSynchronized(),__a:[:].asSynchronized()].asSynchronized()
		def refidData = internalData[referenceId],
		    storedCounters = refidData.__c,
		    storedAggregates = refidData._ca,
		    currentCounters,
		    counterAggregates

		counters.each {k, v->
			startAndInterval = getCounterStartAndInterval(k, timestamp, config)
			storedCounters[k] =  storedCounters[k] ?: [__d:[:].asSynchronized(), __i:((Long)(startAndInterval.interval * 1000)), __r:startAndInterval.resolution].asSynchronized()
			storedCounters[k].__d[startAndInterval.start] = storedCounters[k].__d[startAndInterval.start] ?: [__t:0d, __v: new ArrayList(startAndInterval.count).asSynchronized(),__s:startAndInterval.start, __n:startAndInterval.count, __e:new Date(startAndInterval.start.time + (startAndInterval.range * 60000))]
			currentCounters = storedCounters[k].__d[startAndInterval.start]
			currentCounters.__v[startAndInterval.interval] = currentCounters.__v[startAndInterval.interval] ?: 0d
			currentCounters.__t += v
			currentCounters.__v[startAndInterval.interval] += v

			aggregates = getCounterAggregateStartsAndIntervals(k, timestamp, config)
			aggregates?.each {agg->
				storedAggregates[k] = storedAggregates[k] ?: [:].asSynchronized()
				counterAggregates = storedAggregates[k]
				counterAggregates[agg.resolution] = counterAggregates[agg.resolution] ?: [:].asSynchronized()
				def aggRes = counterAggregates[agg.resolution]
				aggRes[agg.start] = aggRes[agg.start] ?: [__t:0d, __v: new ArrayList(agg.count.intValue()).asSynchronized(), __s:agg.start, __n:agg.count, __e:new Date(agg.start.time + (agg.range * 60000))].asSynchronized()
				def currentAgg = aggRes[agg.start]
				currentAgg.__v[agg.interval] = currentAgg.__v[agg.interval] ?: 0d
				currentAgg.__t += v
				currentAgg.__v[agg.interval] += v
			}
		}

	}

	void bulkSaveCounters(String referenceId, List<Map<Date, Map<String, Double>>> countersByTime, ConfigObject config) {
		countersByTime.each {timestamp, counters->
			saveCounters(referenceId, counters, timestamp, config)
		}
	}


	void saveMetrics(String referenceId, Map<String, Double> metrics, Date timestamp, ConfigObject config) {
		def startAndInterval,
			aggregates
		internalData[referenceId] = internalData[referenceId] ?: [__c:[:].asSynchronized(),_ca:[:].asSynchronized(),__m:[:].asSynchronized(),__a:[:].asSynchronized()].asSynchronized()
		def refidData = internalData[referenceId],
		    storedMetrics = refidData.__m,
		    storedAggregates = refidData.__a,
		    currentMetrics,
		    metricAggregates,
		    prevExists = false,
		    prevValue = 0
		metrics.each {k, v->
			startAndInterval = getMetricStartAndInterval(k, timestamp, config)
			storedMetrics[k] =  storedMetrics[k] ?: [__d:[:].asSynchronized(), __i:((Long)(startAndInterval.interval * 1000)), __r:startAndInterval.resolution].asSynchronized()
			prevExists = false
			storedMetrics[k].__d[startAndInterval.start] = storedMetrics[k].__d[startAndInterval.start] ?: [__i:0i, __t:0d, __v: new ArrayList(startAndInterval.count).asSynchronized(),__s:startAndInterval.start, __n:startAndInterval.count, __e:new Date(startAndInterval.start.time + (startAndInterval.range * 60000))]
			currentMetrics = storedMetrics[k].__d[startAndInterval.start]
			if (currentMetrics.__v[startAndInterval.interval]) {
				prevExists = true
				prevValue = currentMetrics.__v[startAndInterval.interval]
				currentMetrics.__i--
				currentMetrics.__t = currentMetrics.__t - prevValue
			}
			currentMetrics.__i++
			currentMetrics.__t += v
			currentMetrics.__v[startAndInterval.interval] = v

			aggregates = getAggregateStartsAndIntervals(k, timestamp, config)
			aggregates?.each {agg->
				storedAggregates[k] = storedAggregates[k] ?: [:].asSynchronized()
				metricAggregates = storedAggregates[k]
				metricAggregates[agg.resolution] = metricAggregates[agg.resolution] ?: [:].asSynchronized()
				def aggRes = metricAggregates[agg.resolution]
				aggRes[agg.start] = aggRes[agg.start] ?: [__i:0i, __t:0d, __h:null, __l:null, __v: new ArrayList(agg.count.intValue()).asSynchronized(), __s:agg.start, __n:agg.count, __e:new Date(agg.start.time + (agg.range * 60000))].asSynchronized()
				def currentAgg = aggRes[agg.start]
				currentAgg.__v[agg.interval] = currentAgg.__v[agg.interval] ?: [__t:0d,__i:0i,__h:null,__l:null]
				if (prevExists) {
					currentAgg.__i--
					currentAgg.__t = currentAgg.__t - prevValue
					currentAgg.__v[agg.interval].__i--
					currentAgg.__v[agg.interval].__t = currentAgg.__v[agg.interval].__t - prevValue
				}
				currentAgg.__i++
				currentAgg.__t += v
				if (currentAgg.__l == null || v < currentAgg.__l) currentAgg.__l = v
				if (currentAgg.__h == null || v > currentAgg.__h) currentAgg.__h = v
				currentAgg.__v[agg.interval].__i++
				currentAgg.__v[agg.interval].__t += v
				if (currentAgg.__v[agg.interval].__l == null || v < currentAgg.__v[agg.interval].__l) currentAgg.__v[agg.interval].__l = v
				if (currentAgg.__v[agg.interval].__h == null || v > currentAgg.__v[agg.interval].__h) currentAgg.__v[agg.interval].__h = v
			}
		}
	}

	void bulkSaveMetrics(String referenceId, List<Map<Date, Map<String, Double>>> metricsByTime, ConfigObject config) {
		metricsByTime.each {timestamp, metrics->
			saveMetrics(referenceId, metrics, timestamp, config)
		}
	}


	Map getCounters(Date start, Date end, String referenceIdQuery, String counterNameQuery, Map<String, Object> options, ConfigObject config) {
		def rtn = [:],
			res
		internalData.each {refId, db->
			if (referenceIdQuery == null || refId =~ referenceIdQuery) {
				rtn[refId] = rtn[refId] ?: [:]
				db?.__c?.each {counterName, countersDb->
					if (counterNameQuery == null || counterName =~ counterNameQuery) {
						res = countersDb.__r
						def interval = SUPPORTED_RESOLUTIONS_INTERVAL_SIZE[res]
						countersDb.__d.each {timestamp, map->
							if (timestamp > start && timestamp < end) {
								def f = false
								map.__v.eachWithIndex {v,i->
									def intervalTimestamp = new Date(timestamp.time + (i*interval))
									if (intervalTimestamp > start && (v != null || f)) {
										rtn[refId][counterName] = rtn[refId][counterName] ?: []
										def rec = [start:intervalTimestamp, count:v]
										if (options?.includeEndDate) rec.end = Date(timestamp.time + (i*interval) + interval)
										rtn[refId][counterName] << rec
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
		[start:start, end:end, items:items]

	}

	Map getCounterAggregates(String resolution, Date start, Date end, String referenceIdQuery, String counterNameQuery, Map<String, Object> options, ConfigObject config) {
		def rtn = [:],
		    interval = SUPPORTED_RESOLUTIONS_INTERVAL_SIZE[resolution]

		internalData.each {refId, db->
			if (referenceIdQuery == null || refId =~ referenceIdQuery) {
				rtn[refId] = rtn[refId] ?: [:]
				def aggs = db._ca
				aggs?.each {counterName, aggCounters->
					if (counterNameQuery == null || counterName =~ counterNameQuery) {
						def agg = aggCounters[resolution]
						agg?.each {timestamp, map->
							if (timestamp > start && timestamp < end) {
								def f = false
								map.__v.eachWithIndex {v,i->
									def intervalTimestamp = new Date(timestamp.time + (i*interval))
									if (intervalTimestamp > start && (v != null || f)) {
										rtn[refId][counterName] = rtn[refId][counterName] ?: []
										def rec = [start:intervalTimestamp, count:v ]
										if (options?.includeEndDate) rec.e = Date(timestamp.time + (i*interval) + interval)
										rtn[refId][counterName] << rec
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
		[start:start, end:end, items:items]
	}


	Map getMetrics(Date start, Date end, String referenceIdQuery, String metricNameQuery, Map<String, Object> options, ConfigObject config) {
		def rtn = [:],
			res
		internalData.each {refId, db->
			if (referenceIdQuery == null || refId =~ referenceIdQuery) {
				rtn[refId] = rtn[refId] ?: [:]
				db?.__m?.each {metricName, metricsDb->
					if (metricNameQuery == null || metricName =~ metricNameQuery) {
						res = metricsDb.__r
						def interval = SUPPORTED_RESOLUTIONS_INTERVAL_SIZE[res]
						metricsDb.__d.each {timestamp, map->
							if (timestamp > start && timestamp < end) {
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
		[start:start, end:end, items:items]

	}

	Map getMetricAggregates(String resolution, Date start, Date end, String referenceIdQuery, String metricNameQuery, Map<String, Object> options, ConfigObject config) {
		def rtn = [:],
		    interval = SUPPORTED_RESOLUTIONS_INTERVAL_SIZE[resolution]

		internalData.each {refId, db->
			if (referenceIdQuery == null || refId =~ referenceIdQuery) {
				rtn[refId] = rtn[refId] ?: [:]
				def aggs = db.__a
				aggs?.each {metricName, aggMetrics->
					if (metricNameQuery == null || metricName =~ metricNameQuery) {
						def agg = aggMetrics[resolution]
						agg?.each {timestamp, map->
							if (timestamp > start && timestamp < end) {
								def f = false
								map.__v.eachWithIndex {v,i->
									def intervalTimestamp = new Date(timestamp.time + (i*interval))
									if (intervalTimestamp > start && (v != null || f)) {
										rtn[refId][metricName] = rtn[refId][metricName] ?: []
										def rec = [start:intervalTimestamp, sum:v.__t, count: v.__i, max:v.__h, min:v.__l, average: (v.__i > 0 ? (v.__t / v.__i) : 0d) ]
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
		[start:start, end:end, items:items]
	}
}
