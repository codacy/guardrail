package core.issues

import dev.guardrail.Context
import dev.guardrail.generators.{ Client, Clients }
import dev.guardrail.generators.scala.ScalaGeneratorMappings.scalaInterpreter
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import support.SwaggerSpecRunner

class ClientSuccessAccessorTest extends AnyFunSpec with Matchers with SwaggerSpecRunner {

  private val spec: String =
    s"""
       |openapi: 3.0.2
       |info:
       |  title: Whatever
       |  version: 1.0.0
       |paths:
       |  /single:
       |    get:
       |      operationId: getSingle
       |      responses:
       |        '200':
       |          description: Ok
       |          content:
       |            application/json:
       |              schema:
       |                type: string
       |        '400':
       |          description: Bad
       |  /empty:
       |    delete:
       |      operationId: deleteEmpty
       |      responses:
       |        '204':
       |          description: Gone
       |        '404':
       |          description: Missing
       |  /two:
       |    get:
       |      operationId: getTwo
       |      responses:
       |        '200':
       |          description: Ok
       |          content:
       |            application/json:
       |              schema:
       |                type: string
       |        '201':
       |          description: Created
       |          content:
       |            application/json:
       |              schema:
       |                type: string
       |""".stripMargin

  private val rendered: String = {
    val (_, Clients(clients, _), _) = runSwaggerSpec(scalaInterpreter)(spec)(Context.empty, "akka-http")
    clients.flatMap { case Client(_, _, _, _, _, statements) => statements }.map(_.syntax).mkString("\n")
  }

  private def responseType(name: String): String = {
    val start = rendered.indexOf(s"sealed abstract class $name")
    start should be >= 0
    rendered.substring(start, rendered.indexOf("object " + name, start)).replaceAll("\\s+", " ")
  }

  describe("Client response types") {
    it("should expose the single success case through ClientResponse") {
      val single = responseType("GetSingleResponse")
      single should include("extends ClientResponse[String]")
      single should include("def successOrThrow: String")
      single should include("case x: GetSingleResponse.OK => x.value")
      single should include("throw new UnsuccessfulResponseException(other)")
    }

    it("should unwrap a success case without a body to Unit") {
      val empty = responseType("DeleteEmptyResponse")
      empty should include("extends ClientResponse[Unit]")
      empty should include("case DeleteEmptyResponse.NoContent => ()")
    }

    it("should not guess when there are several success cases") {
      val two = responseType("GetTwoResponse")
      two should not include "ClientResponse"
      two should not include "successOrThrow"
    }
  }
}
