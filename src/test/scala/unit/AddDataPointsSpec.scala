package unit

import java.time.Instant

import io.waylay.kairosdb.driver.KairosDB
import io.waylay.kairosdb.driver.models.KairosCompatibleType.KNumber
import io.waylay.kairosdb.driver.models._
import mockws.MockWS
import org.specs2.mutable.Specification
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.Results._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.{FutureMatchers, ResultMatchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class AddDataPointsSpec extends Specification with FutureMatchers with ResultMatchers {

  // for fixing test runs in travis, could be related to deprecated play global state
  sequential

  "KairosDB#addDataPoints" should {
    "add a single data point" in { implicit ee: ExecutionEnv =>
      val mockWs = MockWS {
        case ("POST", "http://localhost:8080/api/v1/datapoints") => Action { req =>
          val expected = Json.parse(
            """
              |[
              |  {
              |    "name": "archive_file_search",
              |    "timestamp": 1470062449000,
              |    "value": 321,
              |    "tags": {
              |      "host": "server2"
              |    }
              |  }
              |]
            """.stripMargin)
          req.body.asJson.map { json =>
            if(json == expected) NoContent else BadRequest
          } getOrElse BadRequest
        }
      }

      val kairosDb = new KairosDB(StandaloneMockWs(mockWs), KairosDBConfig(), global)
      val datapoint = DataPointWithSingleValue(MetricName("archive_file_search"), KNumber(321), Instant.ofEpochMilli(1470062449000L), Seq(Tag("host", "server2")))

      val r = kairosDb.addDataPoints(Seq(datapoint)) must beEqualTo(()).await(1, 10.seconds)
      mockWs.close()
      r
    }

    "add a multiple data points" in { implicit ee: ExecutionEnv =>
      val mockWs = MockWS {
        case ("POST", "http://localhost:8080/api/v1/datapoints") => Action { req =>
          val expected = Json.parse(
            """
              |[
              |  {
              |      "name": "archive_file_tracked",
              |      "datapoints": [[1359788400000, 123], [1359788300000, 13.2], [1359788410000, 23.1]],
              |      "tags": {
              |          "host": "server1",
              |          "data_center": "DC1"
              |      },
              |      "ttl": 300
              |  },
              |  {
              |    "name": "archive_file_search",
              |    "timestamp": 1470062449000,
              |    "value": 321,
              |    "tags": {
              |      "host": "server2"
              |    }
              |  }
              |]
            """.stripMargin)

          req.body.asJson.map { json =>
            if(json == expected) NoContent else BadRequest
          } getOrElse BadRequest
        }
      }

      val kairosDb = new KairosDB(StandaloneMockWs(mockWs), KairosDBConfig(), global)
      val datapoint1 = DataPointWithMultipleValues(
        MetricName("archive_file_tracked"),
        Seq(
          (Instant.ofEpochMilli(1359788400000L), KNumber(123)),
          (Instant.ofEpochMilli(1359788300000L), KNumber(13.2)),
          (Instant.ofEpochMilli(1359788410000L), KNumber(23.1))
        ),
        Seq(Tag("host", "server1"), Tag("data_center", "DC1")),
        ttl = Some(300.seconds)
      )
      val datapoint2 = DataPointWithSingleValue(
        MetricName("archive_file_search"),
        KNumber(321),
        Instant.ofEpochMilli(1470062449000L),
        Seq(Tag("host", "server2"))
      )

      try {
        kairosDb.addDataPoints(Seq(datapoint1, datapoint2)) must beEqualTo(()).await(1, 10.seconds)
      }finally {
        mockWs.close()
      }
    }
  }
}
