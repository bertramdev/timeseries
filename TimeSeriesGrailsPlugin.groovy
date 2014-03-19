import grails.plugins.timeseries.mem.MemoryTimeSeriesProvider

class TimeSeriesGrailsPlugin {
	def version = "0.1"
	def grailsVersion = "2.0 > *"
	def title = "Time Series Plugin"
	def author = "Jeremy Leng"
	def authorEmail = "jleng@bcap.com"
	def description = 'Provides a simplified service for reading/writing timeseries data and storing it in a variety of time resolutions. Read method output is intended to support javascript charting libraries. The plugin defines an interface for pluggable storage providers and includes an implementation in-memory storage provider'
	def documentation = "http://grails.org/plugin/time-series"
	def license = "APACHE"
	def organization = [ name: "BertramLabs", url: "http://www.bertramlabs.com/" ]
	def issueManagement = [system: "GITHUB", url: "https://github.com/bertramdev/timeseries/issues" ]
	def scm = [ url: "https://github.com/bertramdev/timeseries" ]

	def doWithApplicationContext = { ctx ->
		String memStoragePath = 'data'
		boolean persist = true
		def conf = application.config.grails.plugins.timeseries.providers.mem
		if (conf.containsKey('storagePath')) {
			memStoragePath = conf.storagePath
		}
		if (conf.containsKey('persist')) {
			persist = conf.boolean('persist')
		}
		// register provider instance and flag for setting as default provider
		ctx.timeSeriesService.registerProvider(new MemoryTimeSeriesProvider(persist, memStoragePath), true)
	}
}
