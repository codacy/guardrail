package core.AkkaHttp

import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import examples.client.akkaHttp.AkkaHttpImplicits._
import examples.client.akkaHttp.Implicits.Show
import examples.client.akkaHttp.definitions.Order
import examples.client.akkaHttp.store.{ GetOrderByIdResponse, StoreClient }
import io.circe.syntax._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar._

import scala.concurrent.Future

class AkkaHttpClientResultTest extends AnyFunSuite with Matchers with ScalaFutures with ScalatestRouteTest {
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10 seconds, 1 second)

  private def clientReplying(response: => Future[HttpResponse]): StoreClient =
    StoreClient.httpClient(_ => response, "http://localhost:80")

  private def json(status: StatusCode, body: String): Future[HttpResponse] =
    Future.successful(HttpResponse(status, entity = HttpEntity(ContentTypes.`application/json`, body)))

  test("success unwraps the single 2xx case") {
    clientReplying(json(StatusCodes.OK, """{"id": 7}""")).getOrderById(7).success.futureValue shouldBe Order(id = Some(7L), complete = None)
  }

  test("success fails with the response type for a non-2xx case, so it stays matchable") {
    val failure = clientReplying(Future.successful(HttpResponse(StatusCodes.NotFound))).getOrderById(7).success.failed.futureValue

    failure shouldBe a[UnsuccessfulResponseException]
    failure.asInstanceOf[UnsuccessfulResponseException].response shouldBe GetOrderByIdResponse.NotFound
  }

  test("future keeps non-2xx cases as values") {
    clientReplying(Future.successful(HttpResponse(StatusCodes.BadRequest))).getOrderById(7).future.futureValue shouldBe GetOrderByIdResponse.BadRequest
  }

  test("future fails with the offending response when the status is not described by the spec") {
    val failure = clientReplying(Future.successful(HttpResponse(StatusCodes.ImATeapot))).getOrderById(7).future.failed.futureValue

    failure shouldBe a[UnexpectedResponseException]
    failure.asInstanceOf[UnexpectedResponseException].response.status shouldBe StatusCodes.ImATeapot
  }

  test("future fails with the original error when the transport fails") {
    val boom = new RuntimeException("boom")

    clientReplying(Future.failed(boom)).getOrderById(7).future.failed.futureValue shouldBe boom
  }

  test("Instant can be used as a query or path value and matches its JSON encoding") {
    val instant = java.time.Instant.parse("2026-11-12T10:15:30.123Z")

    Show[java.time.Instant].show(instant) shouldBe "2026-11-12T10:15:30.123Z"
    instant.asJson.asString shouldBe Some(Show[java.time.Instant].show(instant))
  }
}
