class TimeSeriesGrailsPlugin {
	// the plugin version
	def version = "0.1"
	// the version or versions of Grails the plugin is designed for
	def grailsVersion = "2.3 > *"
	// resources that are excluded from plugin packaging
	def pluginExcludes = [
		"grails-app/views/error.gsp"
	]

	def title = "Time Series Plugin" // Headline display name of the plugin
	def author = "Jeremy Leng"
	def authorEmail = "jleng@bacp.com"
	def description = '''\
The Grails Timeseries Plugin provides a simplified service for reading/writing timeseries data and storing it in a variety of time resolutions. Read method output is intended to support javascript charting libraries. The plugin defines an interface for pluggable storage providers and includes an implementation in-memory storage provider.
'''

	// URL to the plugin's documentation
	def documentation = "http://grails.org/plugin/time-series"

	// License: one of 'APACHE', 'GPL2', 'GPL3'
	def license = "APACHE"

	// Details of company behind the plugin (if there is one)
	def organization = [ name: "BertramLabs", url: "http://www.bertramlabs.com/" ]

	// Any additional developers beyond the author specified above.
	def developers = [ [ name: "Jeremy Leng", email: "jleng@bcap.com" ]]

	// Location of the plugin's issue tracker.
	def issueManagement = [ system: "GIT", url: "https://github.com/bertramdev/timeseries.git" ]

	// Online location of the plugin's browseable source code.
	def scm = [ url: "https://github.com/bertramdev/timeseries.git" ]

	def doWithWebDescriptor = { xml ->
		// TODO Implement additions to web.xml (optional), this event occurs before
	}

	def doWithSpring = {
		// TODO Implement runtime spring config (optional)
	}

	def doWithDynamicMethods = { ctx ->
		// TODO Implement registering dynamic methods to classes (optional)
	}

	def doWithApplicationContext = { ctx ->
		// TODO Implement post initialization spring config (optional)
		def memStoragePath = 'data',
			persist = true
		if (application.config.grails.plugins.timeseries.providers.mem.containsKey('storagePath')) {
			memStoragePath = application.config.grails.plugins.timeseries.providers.mem.storagePath
		}
		if (application.config.grails.plugins.timeseries.providers.mem.containsKey('persist')) {
			persist = application.config.grails.plugins.timeseries.providers.mem.boolean('persist')
		}
		// register provider instance and flag for setting as default provider
		ctx['timeSeriesService'].registerProvider(new grails.plugins.timeseries.mem.MemoryTimeSeriesProvider(persist, memStoragePath), true)
	}

	def onChange = { event ->
		// TODO Implement code that is executed when any artefact that this plugin is
		// watching is modified and reloaded. The event contains: event.source,
		// event.application, event.manager, event.ctx, and event.plugin.
	}

	def onConfigChange = { event ->
		// TODO Implement code that is executed when the project configuration changes.
		// The event is the same as for 'onChange'.
	}

	def onShutdown = { event ->
		// TODO Implement code that is executed when the application shuts down (optional)
	}
}
