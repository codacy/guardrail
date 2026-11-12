package core.issues

import dev.guardrail.Context
import dev.guardrail.core.SupportDefinition
import dev.guardrail.generators.Servers
import dev.guardrail.generators.scala.ScalaGeneratorMappings.scalaInterpreter
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scala.meta._
import support.SwaggerSpecRunner

class ResponderTraitsTest extends AnyFunSpec with Matchers with SwaggerSpecRunner {

  private val spec: String =
    s"""
       |openapi: 3.0.2
       |info:
       |  title: Whatever
       |  version: 1.0.0
       |paths:
       |  /a:
       |    get:
       |      operationId: getA
       |      responses:
       |        '200':
       |          description: Ok
       |          content:
       |            application/json:
       |              schema:
       |                type: string
       |        '400':
       |          description: Bad
       |          content:
       |            application/json:
       |              schema:
       |                $$ref: '#/components/schemas/BadRequest'
       |        '404':
       |          description: Missing
       |  /b:
       |    get:
       |      operationId: getB
       |      responses:
       |        '200':
       |          description: Ok
       |          content:
       |            application/json:
       |              schema:
       |                type: integer
       |        '204':
       |          description: Nothing
       |          headers:
       |            X-Total:
       |              schema:
       |                type: integer
       |        '404':
       |          description: Missing
       |          content:
       |            application/json:
       |              schema:
       |                $$ref: '#/components/schemas/BadRequest'
       |components:
       |  schemas:
       |    BadRequest:
       |      type: object
       |      properties:
       |        message:
       |          type: string
       |""".stripMargin

  private val (traits, companions) = {
    val (_, _, Servers(servers, support)) = runSwaggerSpec(scalaInterpreter)(spec)(Context.empty, "akka-http")
    val traitDefns                        = support.collect { case SupportDefinition(_, _, defn, false) => defn }.flatten.collect { case t: Defn.Trait => t }
    val companionSrc                      =
      servers.flatMap(_.serverDefinitions).flatMap(_.collect { case o: Defn.Object if o.name.value.endsWith("Response") => o.name.value -> o.syntax }).toMap
    (traitDefns.map(t => t.name.value -> t.syntax).toMap, companionSrc)
  }

  describe("Responder traits") {
    it("should have one file-independent trait per status, with or without a body") {
      traits.keySet should contain theSameElementsAs List("RespondsOK", "RespondsBadRequest", "RespondsEmptyNotFound", "RespondsNotFound")
      traits("RespondsBadRequest") should include("trait RespondsBadRequest[V, R]")
      traits("RespondsEmptyNotFound") should include("trait RespondsEmptyNotFound[R]")
    }

    it("should be mixed into each operation's response companion using that operation's value type") {
      val getA = companions("GetAResponse")
      getA should include("support.RespondsOK[String, GetAResponse]")
      getA should include("support.RespondsBadRequest[BadRequest, GetAResponse]")
      getA should include("support.RespondsEmptyNotFound[GetAResponse]")

      val getB = companions("GetBResponse")
      getB should include("support.RespondsOK[BigInt, GetBResponse]")
      getB should include("support.RespondsNotFound[BadRequest, GetBResponse]")
    }

    it("should skip responses that declare headers, because their constructors take extra parameters") {
      companions("GetBResponse") should not include "RespondsNoContent"
      companions("GetBResponse") should not include "RespondsEmptyNoContent"
      traits.keySet should not contain "RespondsNoContent"
      traits.keySet should not contain "RespondsEmptyNoContent"
    }
  }
}
