package com.twitter.summingbird.scalding

import cascading.flow.FlowProcess
import com.twitter.scalding.{ RuntimeStats => ScaldingRuntimeStats }
import com.twitter.summingbird.option.JobId
import com.twitter.summingbird.{ CounterIncrementor, SummingbirdRuntimeStats, PlatformStatProvider }
import scala.util.{ Try => ScalaTry, Failure }
import org.slf4j.LoggerFactory

// Incrementor for Scalding Counter (Stat) 
// Returned to the Summingbird Counter object to call incrBy function in Summingbird job code
private[summingbird] case class ScaldingCounterIncrementor(group: String, name: String, fp: FlowProcess[_]) extends CounterIncrementor {
  def incrBy(by: Long): Unit = fp.increment(group, name, by)
}

private[summingbird] object ScaldingStatProvider extends PlatformStatProvider {
  @transient private val logger = LoggerFactory.getLogger(ScaldingStatProvider.getClass)

  private def pullInScaldingRuntimeForJobID(jobID: JobId): Option[FlowProcess[_]] =
    ScalaTry[FlowProcess[_]] { ScaldingRuntimeStats.getFlowProcessForUniqueId(jobID.get) }
      .recoverWith {
        case e: Throwable =>
          logger.debug("Unable to get Scalding FlowProcess for jobID {}, error {}", jobID, e)
          Failure(e)
      }.toOption

  // Incrementor from PlatformStatProvicer
  // We use a partially applied function: if successful, ScaldingRuntimeStats.getFlowProcessForUniqueId
  // returns the FlowProcess for this job. We then create a ScaldingCounterIncrementor object that takes the
  // FlowProcess and Counter group/name and contains an incrBy function to be called from SB job
  def counterIncrementor(jobID: JobId, group: String, name: String): Option[ScaldingCounterIncrementor] =
    pullInScaldingRuntimeForJobID(jobID).map { flowP: FlowProcess[_] => ScaldingCounterIncrementor(group, name, flowP) }
}

private[summingbird] object ScaldingRuntimeStatsProvider {
  SummingbirdRuntimeStats.addPlatformStatProvider(ScaldingStatProvider)
}
