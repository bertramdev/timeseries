class TimeSeriesBootStrap {
	def grailsApplication
	def init = { servletContext ->
		grailsApplication.mainContext['timeSeriesService'].init()
	}
	def destroy = {
		grailsApplication.mainContext['timeSeriesService'].destroy()
	}
}
