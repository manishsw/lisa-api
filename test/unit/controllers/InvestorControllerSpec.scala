/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package unit.controllers

import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatest.{ShouldMatchers, WordSpec}
import org.scalatestplus.play.OneAppPerSuite
import play.api.data.validation.ValidationError
import play.api.libs.json.{JsError, JsPath, Json}
import play.api.mvc.{AnyContentAsJson, Results}
import play.api.test.Helpers._
import play.api.test._
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.lisaapi.controllers.InvestorController
import uk.gov.hmrc.lisaapi.models.{CreateLisaInvestorAlreadyExistsResponse, CreateLisaInvestorErrorResponse, CreateLisaInvestorNotFoundResponse, CreateLisaInvestorSuccessResponse}
import uk.gov.hmrc.lisaapi.services.InvestorService

import scala.concurrent.Future


class InvestorControllerSpec extends WordSpec with MockitoSugar with ShouldMatchers with OneAppPerSuite {

  val mockService = mock[InvestorService]

  val mockInvestorController = new InvestorController{
    override val service: InvestorService = mockService
  }

  val acceptHeader: (String, String) = (HeaderNames.ACCEPT, "application/vnd.hmrc.1.0+json")

  val investorJson = """{
                         "investorNINO" : "AB123456D",
                         "firstName" : "Ex first Name",
                         "lastName" : "Ample",
                         "dateOfBirth" : "1973-03-24"
                       }""".stripMargin

  val invalidInvestorJson = """{
                         "investorNINO" : 123456,
                         "firstName" : "Ex first Name",
                         "lastName" : "Ample",
                         "dateOfBirth" : "1973-03-24"
                       }""".stripMargin


  val lisaManager = "Z019283"

  "The Investor Controller" should {
    "return with status 201 created" in
      {
        when(mockService.createInvestor(any(), any())(any())).thenReturn(Future.successful(CreateLisaInvestorSuccessResponse("AB123456")))
        val res = mockInvestorController.createLisaInvestor(lisaManager).apply(FakeRequest(Helpers.PUT,"/").withHeaders(acceptHeader).
          withBody(AnyContentAsJson(Json.parse(investorJson))))
        status(res) should be (CREATED)
      }

    "return with status 403 forbidden" when {
      "given a Not Found response from the service layer" in {
        when(mockService.createInvestor(any(), any())(any())).thenReturn(Future.successful(CreateLisaInvestorNotFoundResponse))
        val res = mockInvestorController.createLisaInvestor(lisaManager).apply(FakeRequest(Helpers.PUT, "/").withHeaders(acceptHeader).
          withBody(AnyContentAsJson(Json.parse(investorJson))))
        status(res) should be(FORBIDDEN)
      }
    }

    "return with status 409 conflict" when {
      "given a Already Exists response from the service layer" in {
        when(mockService.createInvestor(any(), any())(any())).thenReturn(Future.successful(CreateLisaInvestorAlreadyExistsResponse("A92823736")))
        val res = mockInvestorController.createLisaInvestor(lisaManager).apply(FakeRequest(Helpers.PUT, "/").withHeaders(acceptHeader).
          withBody(AnyContentAsJson(Json.parse(investorJson))))
        status(res) should be(CONFLICT)
      }
    }

    "return with status 400 bad request" when {
      "given a json body which does not match the schema" in {
        when(mockService.createInvestor(any(), any())(any())).thenReturn(Future.successful(CreateLisaInvestorSuccessResponse("AB123456")))
        val res = mockInvestorController.createLisaInvestor(lisaManager).apply(FakeRequest(Helpers.PUT, "/").withHeaders(acceptHeader).
          withBody(AnyContentAsJson(Json.parse(invalidInvestorJson))))
        status(res) should be(BAD_REQUEST)
      }

      "given a plain text body" in {
        when(mockService.createInvestor(any(), any())(any())).thenReturn(Future.successful(CreateLisaInvestorSuccessResponse("AB123456")))
        val res = mockInvestorController.createLisaInvestor(lisaManager).apply(FakeRequest(Helpers.PUT, "/").withHeaders(acceptHeader).
          withTextBody("hello"))

        status(res) should be(BAD_REQUEST)
      }
    }

    "return with status 500 internal server error" when {
      "given an invalid json body" in {
        when(mockService.createInvestor(any(), any())(any())).thenReturn(Future.successful(CreateLisaInvestorErrorResponse))
        val res = mockInvestorController.createLisaInvestor(lisaManager).apply(FakeRequest(Helpers.PUT, "/").withHeaders(acceptHeader).
          withBody(AnyContentAsJson(Json.parse(investorJson))))
        status(res) should be(INTERNAL_SERVER_ERROR)
      }

      "given an error response from the service layer" in {
        when(mockService.createInvestor(any(), any())(any())).thenReturn(Future.successful(CreateLisaInvestorErrorResponse))
        val res = mockInvestorController.createLisaInvestor(lisaManager).apply(FakeRequest(Helpers.PUT,"/").withHeaders(acceptHeader).
          withBody(AnyContentAsJson(Json.parse(investorJson))))
        status(res) should be (INTERNAL_SERVER_ERROR)
      }
    }

    "return with status 406 createInvestor " in
      {
        when(mockService.createInvestor(any(), any())(any())).thenReturn(Future.successful(CreateLisaInvestorSuccessResponse("AB123456")))
        val res = mockInvestorController.createLisaInvestor(lisaManager).apply(FakeRequest(Helpers.PUT,"/").withHeaders(("accept","application/vnd.hmrc.2.0+json")))
        status(res) should be (406)
      }

  }
}
