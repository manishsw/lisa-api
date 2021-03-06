/*
 * Copyright 2019 HM Revenue & Customs
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

import org.joda.time.DateTime
import org.mockito.Matchers.{any, eq => matchersEquals}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, MustMatchers}
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.mvc.{ControllerComponents, PlayBodyParsers}
import play.api.test.{FakeRequest, Injecting}
import play.api.test.Helpers._
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.api.controllers.ErrorAcceptHeaderInvalid
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.lisaapi.config.AppContext
import uk.gov.hmrc.lisaapi.controllers.{ErrorAccountNotFound, ErrorBadRequestAccountId, ErrorBadRequestLmrn, ErrorBonusPaymentTransactionNotFound, ErrorInternalServerError, ErrorServiceUnavailable, ErrorTransactionNotFound, TransactionController}
import uk.gov.hmrc.lisaapi.metrics.LisaMetrics
import uk.gov.hmrc.lisaapi.models._
import uk.gov.hmrc.lisaapi.services.{AuditService, TransactionService}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TransactionControllerSpec extends PlaySpec
  with MockitoSugar
  with OneAppPerSuite
  with BeforeAndAfter
  with MustMatchers
  with Injecting {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val acceptHeaderV1: (String, String) = (HeaderNames.ACCEPT, "application/vnd.hmrc.1.0+json")
  val acceptHeaderV2: (String, String) = (HeaderNames.ACCEPT, "application/vnd.hmrc.2.0+json")
  val lmrn = "Z1234"
  val accountId = "12345"
  val transactionId = "67890"

  before {
    reset(mockAuditService)
    when(mockAuthCon.authorise[Option[String]](any(),any())(any(), any())).thenReturn(Future(Some("1234")))
  }

  "Get transaction" must {

    "return 200 ok" when {
      "data is returned from the service for v1" in {
        when(mockService.getTransaction(any(), any(), any())(any())).thenReturn(Future.successful(GetTransactionSuccessResponse(
          transactionId = transactionId,
          transactionType = Some("Payment"),
          paymentStatus = "Paid",
          paymentDate = Some(new DateTime("2017-06-20")),
          paymentAmount = Some(1.0),
          paymentReference = Some("ref"),
          supersededBy = Some("0000012345"),
          bonusDueForPeriod = Some(1.0)
        )))

        val res = SUT.getTransaction(lmrn, accountId, transactionId).apply(FakeRequest().withHeaders(acceptHeaderV1))

        status(res) mustBe OK

        val json = contentAsJson(res)

        json mustBe Json.obj(
          "transactionId" -> transactionId,
          "paymentStatus" -> "Paid",
          "paymentDate" -> "2017-06-20",
          "paymentAmount" -> 1.0,
          "paymentReference" -> "ref",
          "bonusDueForPeriod" -> 1.0
        )
      }

      "data is returned from the service for v2" in {
        when(mockService.getTransaction(any(), any(), any())(any())).thenReturn(Future.successful(GetTransactionSuccessResponse(
          transactionId = transactionId,
          transactionType = Some("Payment"),
          paymentStatus = "Paid",
          paymentDate = Some(new DateTime("2017-06-20")),
          paymentAmount = Some(1.0),
          paymentReference = Some("ref"),
          supersededBy = Some("0000012345"),
          bonusDueForPeriod = Some(1.0)
        )))

        val res = SUT.getTransaction(lmrn, accountId, transactionId).apply(FakeRequest().withHeaders(acceptHeaderV2))

        status(res) mustBe OK

        val json = contentAsJson(res)

        json mustBe Json.obj(
          "transactionId" -> transactionId,
          "transactionType" -> "Payment",
          "paymentStatus" -> "Paid",
          "paymentDate" -> "2017-06-20",
          "paymentAmount" -> 1.0,
          "paymentReference" -> "ref",
          "supersededBy" -> "0000012345"
        )
      }
    }

    "return 400 bad request" when {
      "the LMRN in the URL is in an incorrect format" in {
        when(mockService.getTransaction(any(), any(), any())(any())).thenReturn(Future.successful(GetTransactionErrorResponse))

        val res = SUT.getTransaction("1234", accountId, transactionId).apply(FakeRequest().withHeaders(acceptHeaderV2))

        status(res) mustBe BAD_REQUEST

        (contentAsJson(res) \ "message").as[String] mustBe ErrorBadRequestLmrn.message
      }
      "the accountId in the URL is in an incorrect format" in {
        when(mockService.getTransaction(any(), any(), any())(any())).thenReturn(Future.successful(GetTransactionErrorResponse))

        val res = SUT.getTransaction(lmrn, "~=123", transactionId).apply(FakeRequest().withHeaders(acceptHeaderV2))

        status(res) mustBe BAD_REQUEST

        (contentAsJson(res) \ "message").as[String] mustBe ErrorBadRequestAccountId.message
      }
    }

    "return 404 not found" when {
      "the service returns account not found" in {
        when(mockService.getTransaction(any(), any(), any())(any())).thenReturn(Future.successful(GetTransactionAccountNotFoundResponse))

        val res = SUT.getTransaction(lmrn, accountId, transactionId).apply(FakeRequest().withHeaders(acceptHeaderV2))

        status(res) mustBe NOT_FOUND

        (contentAsJson(res) \ "code").as[String] mustBe "INVESTOR_ACCOUNTID_NOT_FOUND"
      }
      "the service returns transaction not found for v1" in {
        when(mockService.getTransaction(any(), any(), any())(any())).thenReturn(Future.successful(GetTransactionTransactionNotFoundResponse))

        val res = SUT.getTransaction(lmrn, accountId, transactionId).apply(FakeRequest().withHeaders(acceptHeaderV1))

        status(res) mustBe NOT_FOUND

        (contentAsJson(res) \ "code").as[String] mustBe "BONUS_PAYMENT_TRANSACTION_NOT_FOUND"
      }
      "the service returns transaction not found for v2" in {
        when(mockService.getTransaction(any(), any(), any())(any())).thenReturn(Future.successful(GetTransactionTransactionNotFoundResponse))

        val res = SUT.getTransaction(lmrn, accountId, transactionId).apply(FakeRequest().withHeaders(acceptHeaderV2))

        status(res) mustBe NOT_FOUND

        (contentAsJson(res) \ "code").as[String] mustBe "TRANSACTION_NOT_FOUND"
      }
    }

    "return 406 not acceptable" when {
      "the http accept header is missing" in {
        when(mockService.getTransaction(any(), any(), any())(any())).thenReturn(Future.successful(GetTransactionErrorResponse))

        val res = SUT.getTransaction(lmrn, accountId, transactionId).apply(FakeRequest())

        status(res) mustBe NOT_ACCEPTABLE

        (contentAsJson(res) \ "message").as[String] mustBe ErrorAcceptHeaderInvalid.message
      }
    }

    "return 500 internal server error" when {
      "the service returns an error" in {
        when(mockService.getTransaction(any(), any(), any())(any())).thenReturn(Future.successful(GetTransactionErrorResponse))

        val res = SUT.getTransaction(lmrn, accountId, transactionId).apply(FakeRequest().withHeaders(acceptHeaderV2))

        status(res) mustBe INTERNAL_SERVER_ERROR

        (contentAsJson(res) \ "code").as[String] mustBe "INTERNAL_SERVER_ERROR"
      }
      "a charge refund cancelled is returned for api v1" in {
        when(mockService.getTransaction(any(), any(), any())(any())).thenReturn(Future.successful(GetTransactionSuccessResponse(
          transactionId = "1234",
          paymentStatus = "Charge refund cancelled"
        )))

        val res = SUT.getTransaction(lmrn, accountId, transactionId).apply(FakeRequest().withHeaders(acceptHeaderV1))

        status(res) mustBe INTERNAL_SERVER_ERROR

        (contentAsJson(res) \ "code").as[String] mustBe "INTERNAL_SERVER_ERROR"
      }
    }

    "return 503 service unavailable" when {
      "the service returns a GetTransactionServiceUnavailableResponse" in {
        when(mockService.getTransaction(any(), any(), any())(any())).
          thenReturn(Future.successful(GetTransactionServiceUnavailableResponse))

        val res = SUT.getTransaction(lmrn, accountId, transactionId).apply(FakeRequest().withHeaders(acceptHeaderV2))

        status(res) mustBe SERVICE_UNAVAILABLE

        (contentAsJson(res) \ "code").as[String] mustBe "SERVER_ERROR"
      }
    }

    "audit getTransactionReported" when {
      "data is returned from the service for v1" in {
        when(mockService.getTransaction(any(), any(), any())(any())).thenReturn(Future.successful(GetTransactionSuccessResponse(
          transactionId = transactionId,
          transactionType = Some("Payment"),
          paymentStatus = "Paid",
          paymentDate = Some(new DateTime("2017-06-20")),
          paymentAmount = Some(1.0),
          paymentReference = Some("ref"),
          supersededBy = Some("0000012345"),
          bonusDueForPeriod = Some(1.0)
        )))
        val res = SUT.getTransaction(lmrn, accountId, transactionId).apply(FakeRequest().withHeaders(acceptHeaderV1))
        await(res)
        verify(mockAuditService).audit(
          auditType = matchersEquals("getTransactionReported"),
          path = matchersEquals(s"/manager/$lmrn/accounts/$accountId/transactions/$transactionId/payments"),
          auditData = matchersEquals(Map(
            "lisaManagerReferenceNumber" -> lmrn,
            "accountId" -> accountId,
            "transactionId" -> transactionId
          )))(any())
      }
      "data is returned from the service for v2" in {
        when(mockService.getTransaction(any(), any(), any())(any())).thenReturn(Future.successful(GetTransactionSuccessResponse(
          transactionId = transactionId,
          transactionType = Some("Payment"),
          paymentStatus = "Paid",
          paymentDate = Some(new DateTime("2017-06-20")),
          paymentAmount = Some(1.0),
          paymentReference = Some("ref"),
          supersededBy = Some("0000012345"),
          bonusDueForPeriod = Some(1.0)
        )))

        val res = SUT.getTransaction(lmrn, accountId, transactionId).apply(FakeRequest().withHeaders(acceptHeaderV2))
        await(res)
        verify(mockAuditService).audit(
          auditType = matchersEquals("getTransactionReported"),
          path = matchersEquals(s"/manager/$lmrn/accounts/$accountId/transactions/$transactionId/payments"),
          auditData = matchersEquals(Map(
            "lisaManagerReferenceNumber" -> lmrn,
            "accountId" -> accountId,
            "transactionId" -> transactionId
          )))(any())
      }
    }

    "audit getTransactionNotReported" when {
      "the service returns account not found" in {
        when(mockService.getTransaction(any(), any(), any())(any())).thenReturn(Future.successful(GetTransactionAccountNotFoundResponse))

        val res = SUT.getTransaction(lmrn, accountId, transactionId).apply(FakeRequest().withHeaders(acceptHeaderV2))
        await(res)
        verify(mockAuditService).audit(
          auditType = matchersEquals("getTransactionNotReported"),
          path = matchersEquals(s"/manager/$lmrn/accounts/$accountId/transactions/$transactionId/payments"),
          auditData = matchersEquals(Map(
            "lisaManagerReferenceNumber" -> lmrn,
            "accountId" -> accountId,
            "transactionId" -> transactionId,
            "reasonNotReported" -> ErrorAccountNotFound.errorCode
          )))(any())
      }
      "the service returns transaction not found for v1" in {
        when(mockService.getTransaction(any(), any(), any())(any())).thenReturn(Future.successful(GetTransactionTransactionNotFoundResponse))

        val res = SUT.getTransaction(lmrn, accountId, transactionId).apply(FakeRequest().withHeaders(acceptHeaderV1))
        await(res)
        verify(mockAuditService).audit(
          auditType = matchersEquals("getTransactionNotReported"),
          path = matchersEquals(s"/manager/$lmrn/accounts/$accountId/transactions/$transactionId/payments"),
          auditData = matchersEquals(Map(
            "lisaManagerReferenceNumber" -> lmrn,
            "accountId" -> accountId,
            "transactionId" -> transactionId,
            "reasonNotReported" -> ErrorBonusPaymentTransactionNotFound.errorCode
          )))(any())
      }
      "the service returns transaction not found for v2" in {
        when(mockService.getTransaction(any(), any(), any())(any())).thenReturn(Future.successful(GetTransactionTransactionNotFoundResponse))

        val res = SUT.getTransaction(lmrn, accountId, transactionId).apply(FakeRequest().withHeaders(acceptHeaderV2))
        await(res)
        verify(mockAuditService).audit(
          auditType = matchersEquals("getTransactionNotReported"),
          path = matchersEquals(s"/manager/$lmrn/accounts/$accountId/transactions/$transactionId/payments"),
          auditData = matchersEquals(Map(
            "lisaManagerReferenceNumber" -> lmrn,
            "accountId" -> accountId,
            "transactionId" -> transactionId,
            "reasonNotReported" -> ErrorTransactionNotFound.errorCode
          )))(any())
      }
      "the service returns an error" in {
        when(mockService.getTransaction(any(), any(), any())(any())).thenReturn(Future.successful(GetTransactionErrorResponse))

        val res = SUT.getTransaction(lmrn, accountId, transactionId).apply(FakeRequest().withHeaders(acceptHeaderV2))
        await(res)
        verify(mockAuditService).audit(
          auditType = matchersEquals("getTransactionNotReported"),
          path = matchersEquals(s"/manager/$lmrn/accounts/$accountId/transactions/$transactionId/payments"),
          auditData = matchersEquals(Map(
            "lisaManagerReferenceNumber" -> lmrn,
            "accountId" -> accountId,
            "transactionId" -> transactionId,
            "reasonNotReported" -> ErrorInternalServerError.errorCode
          )))(any())
      }
      "a charge refund cancelled is returned for api v1" in {
        when(mockService.getTransaction(any(), any(), any())(any())).thenReturn(Future.successful(GetTransactionSuccessResponse(
          transactionId = "1234",
          paymentStatus = "Charge refund cancelled"
        )))

        val res = SUT.getTransaction(lmrn, accountId, transactionId).apply(FakeRequest().withHeaders(acceptHeaderV1))
        await(res)
        verify(mockAuditService).audit(
          auditType = matchersEquals("getTransactionNotReported"),
          path = matchersEquals(s"/manager/$lmrn/accounts/$accountId/transactions/$transactionId/payments"),
          auditData = matchersEquals(Map(
            "lisaManagerReferenceNumber" -> lmrn,
            "accountId" -> accountId,
            "transactionId" -> transactionId,
            "reasonNotReported" -> ErrorInternalServerError.errorCode
          )))(any())
      }
      "the service returns a GetTransactionServiceUnavailableResponse" in {
        when(mockService.getTransaction(any(), any(), any())(any())).
          thenReturn(Future.successful(GetTransactionServiceUnavailableResponse))

        val res = SUT.getTransaction(lmrn, accountId, transactionId).apply(FakeRequest().withHeaders(acceptHeaderV2))
        await(res)
        verify(mockAuditService).audit(
          auditType = matchersEquals("getTransactionNotReported"),
          path = matchersEquals(s"/manager/$lmrn/accounts/$accountId/transactions/$transactionId/payments"),
          auditData = matchersEquals(Map(
            "lisaManagerReferenceNumber" -> lmrn,
            "accountId" -> accountId,
            "transactionId" -> transactionId,
            "reasonNotReported" -> ErrorServiceUnavailable.errorCode
          )))(any())
      }
    }

  }

  val mockService: TransactionService = mock[TransactionService]
  val mockAuditService: AuditService = mock[AuditService]
  val mockAuthCon: AuthConnector = mock[AuthConnector]
  val mockAppContext: AppContext = mock[AppContext]
  val mockLisaMetrics: LisaMetrics = mock[LisaMetrics]
  val mockControllerComponents = inject[ControllerComponents]
  val mockParser = inject[PlayBodyParsers]

  val SUT = new TransactionController(mockAuthCon, mockAppContext, mockService, mockAuditService, mockLisaMetrics, mockControllerComponents, mockParser) {
    override lazy val v2endpointsEnabled = true
  }

}
