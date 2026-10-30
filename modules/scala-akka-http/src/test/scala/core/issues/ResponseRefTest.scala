package core.issues

import dev.guardrail.Context
import dev.guardrail.generators.scala.ScalaGeneratorMappings.scalaInterpreter
import dev.guardrail.generators.{ Client, Clients }
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import support.SwaggerSpecRunner

class ResponseRefTest extends AnyFunSpec with Matchers with SwaggerSpecRunner {

  private val spec: String =
    s"""
       |openapi: 3.0.2
       |info:
       |  title: Whatever
       |  version: 1.0.0
       |paths:
       |  /foo:
       |    get:
       |      operationId: getFoo
       |      responses:
       |        '200':
       |          description: Ok
       |          content:
       |            application/json:
       |              schema:
       |                type: string
       |        '400':
       |          $$ref: '#/components/responses/BadRequest'
       |components:
       |  responses:
       |    BadRequest:
       |      description: Invalid parameters
       |      content:
       |        application/json:
       |          schema:
       |            $$ref: '#/components/schemas/BadRequest'
       |  schemas:
       |    BadRequest:
       |      type: object
       |      properties:
       |        message:
       |          type: string
       |""".stripMargin

  describe("Responses defined as a $ref to components/responses") {
    it("should carry the referenced schema as the response value") {
      val (_, Clients(Client(_, _, _, _, _, statements) :: _, Nil), _) = runSwaggerSpec(scalaInterpreter)(spec)(Context.empty, "akka-http")
      val rendered                                                      = statements.map(_.syntax).mkString("\n")

      rendered should include("case class BadRequest(value: ")
      rendered should not include "case object BadRequest"
    }
  }
}
