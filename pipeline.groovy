appcodeRepoUrl = 'https://github.com/leaveys/workflow.git'

notificationEmailAddress = ''
notificationEmailSubjectTemplate = '$PROJECT_NAME - Build # $BUILD_NUMBER - $BUILD_STATUS!'
notificationEmailContentTemplate = """\$PROJECT_NAME - Build # \$BUILD_NUMBER - \$BUILD_STATUS:
                                      Check console output at \$BUILD_URL to view the results."""

jobName = 'poll-version-control'
job() {
  name jobName

  parameters {
    stringParam('APPCODE_BRANCH',
        'master',
        'The branch of application code repo to poll for changes on.')
  }

  //logRotator(daysToKeepInt: -1, numToKeepInt: 5, artifactDaysToKeepInt: -1, artifactNumToKeepInt: 5)
  logRotator(-1, 5, -1, 5)

  wrappers {
    colorizeOutput()
  }

  deliveryPipelineConfiguration('commit', jobName)
  blockOnDownstreamProjects()

  triggers {
    scm('* * * * *')
  }

  multiscm {
    git {
      remote {
        url(appcodeRepoUrl)
      }
      relativeTargetDir('app')
      branch('$APPCODE_BRANCH')

      configure { node ->
        node / skipTag << 'true'
      }
    }
  }

  steps {
    shell('cd app ; echo APPCODE_COMMIT_HASH=$(cat .git/HEAD) >> ../repo.properties')

    downstreamParameterized {
      trigger ('commit', 'ALWAYS'){
        currentBuild()
        propertiesFile('repo.properties')
      }
    }
  }

  publishers {
    extendedEmail(notificationEmailAddress,
        notificationEmailSubjectTemplate,
        notificationEmailContentTemplate) {
      trigger('Failure')
      trigger('Fixed')
    }
  }
}

jobName = 'commit'
job(type: Maven) {
  name jobName

  parameters {
    stringParam('APPCODE_COMMIT_HASH',
        'master',
        'The commit hash or branch head to run the commit actions/build against.')
  }

  //logRotator(daysToKeepInt: -1, numToKeepInt: 5, artifactDaysToKeepInt: -1, artifactNumToKeepInt: 5)
  logRotator(-1, 5, -1, 5)

  deliveryPipelineConfiguration('commit', jobName)
  blockOnDownstreamProjects()

  wrappers {
    colorizeOutput()
  }

  multiscm {
    git {
      remote {
        url(appcodeRepoUrl)
      }
      branch('$APPCODE_COMMIT_HASH')
      configure { node ->
        node / skipTag << 'true'
      }
    }
  }

  goals('-B -Dmaven.test.failure.ignore test')
  publishers {
    downstreamParameterized {
      trigger ('workflow-job', 'ALWAYS'){
        currentBuild()
      }
    }
  }
}

jobName = 'workflow-job'
job() {
  name jobName

  parameters {
    stringParam('APPCODE_COMMIT_HASH',
        'master',
        'The commit hash or branch head to run the commit actions/build against.')
  }

  //logRotator(daysToKeepInt: -1, numToKeepInt: 5, artifactDaysToKeepInt: -1, artifactNumToKeepInt: 5)
  logRotator(-1, 5, -1, 5)

  deliveryPipelineConfiguration('acceptance', jobName)
  blockOnDownstreamProjects()

  wrappers {
    colorizeOutput()
  }

  multiscm {
    git {
      remote {
        url(appcodeRepoUrl)
      }
      branch('$APPCODE_COMMIT_HASH')
      configure { node ->
        node / skipTag << 'true'
      }
    }
  }
}

view(type: DeliveryPipelineView) {
  name 'pipeline'

  pipelineInstances(5)
  showAggregatedPipeline(false)
  columns(1)
  sorting(Sorting.NONE)
  updateInterval(2)
  showAvatars(false)
  showChangeLog(false)
  pipelines {
    component('name', 'poll-version-control')
  }
}
