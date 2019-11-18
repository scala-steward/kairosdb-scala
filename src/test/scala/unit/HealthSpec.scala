package unit

import io.waylay.kairosdb.driver.KairosDB
import io.waylay.kairosdb.driver.models.HealthCheckResult.{AllHealthy, Unhealthy}
import io.waylay.kairosdb.driver.models._
import mockws.MockWS
import org.specs2.mutable.Specification
import play.api.libs.json.Json
import play.api.mvc.Results._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.{FutureMatchers, ResultMatchers}

import scala.concurrent.duration._
import scala.collection.immutable.Seq


class HealthSpec(implicit ee: ExecutionEnv) extends Specification with FutureMatchers with ResultMatchers with MockHelper {

  // for fixing test runs in travis, could be related to deprecated play global state
  sequential

  "KairosDB#healthStatus" should {
    "return the health status" in {
      val expected = HealthStatusResults(Seq("JVM-Thread-Deadlock: OK","Datastore-Query: OK"))

      val mockWs = MockWS {
        case ("GET", "http://localhost:8080/api/v1/health/status") => Action {
          Ok(Json.arr("JVM-Thread-Deadlock: OK","Datastore-Query: OK"))
        }
      }

      val kairosDb = new KairosDB(StandaloneMockWs(mockWs), KairosDBConfig(), ee.ec)

      val r = kairosDb.healthStatus must beEqualTo(expected).await(1, 10.seconds)
      mockWs.close()
      r
    }
  }

  "KairosDB#healthCheck" should {
    "return all healthy if status is 204" in {
      val mockWs = MockWS {
        case ("GET", "http://localhost:8080/api/v1/health/check") => Action {
          NoContent
        }
      }

      val kairosDb = new KairosDB(StandaloneMockWs(mockWs), KairosDBConfig(), ee.ec)

      val r = kairosDb.healthCheck must beEqualTo(AllHealthy).await(1, 10.seconds)
      mockWs.close()
      r
    }

    "return unhealthy if status is 500" in {
      val mockWs = MockWS {
        case ("GET", "http://localhost:8080/api/v1/health/check") => Action {
          InternalServerError
        }
      }

      val kairosDb = new KairosDB(StandaloneMockWs(mockWs), KairosDBConfig(), ee.ec)

      val r = kairosDb.healthCheck must beEqualTo(Unhealthy).await(1, 10.seconds)
      mockWs.close()
      r
    }
  }
}

