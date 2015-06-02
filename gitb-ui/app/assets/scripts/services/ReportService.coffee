class ReportService

  @headers = {'Accept': 'application/json', 'Content-Type': 'application/json'}
  @defaultConfig = { headers: @headers }

  constructor: (@$log, @RestService) ->
    @$log.debug "Constructing ReportService..."

  getTestResults: (systemId, page, limit) ->
    @RestService.get
      path: jsRoutes.controllers.ReportService.getTestResults().url
      params:
        system_id: systemId
        page: page
        limit: limit
      authenticate: true

  getTestResultOfSession: (sessionId) ->
    @RestService.get({
      path: jsRoutes.controllers.ReportService.getTestResultOfSession(sessionId).url,
      authenticate: true
    })

  createTestReport: (sessionId, systemId, actorId, testId) ->
    @RestService.post({
      path: jsRoutes.controllers.ReportService.createTestReport().url,
      data: {
        session_id: sessionId
        system_id : systemId
        actor_id  : actorId
        test_id   : testId
      },
      authenticate: true
    })

  getTestStepResults: (sessionId) ->
    @RestService.get({
      path: jsRoutes.controllers.ReportService.getTestStepResults(sessionId).url
      authenticate: true
    })

  getTestStepReport: (reportPath) ->
    @RestService.get
      path: jsRoutes.controllers.RepositoryService.getTestStepReport(reportPath).url
      authenticate: true

services.service('ReportService', ReportService)
