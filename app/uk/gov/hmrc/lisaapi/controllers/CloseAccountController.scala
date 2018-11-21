/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.lisaapi.controllers

import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.lisaapi.LisaConstants
import uk.gov.hmrc.lisaapi.metrics.{LisaMetricKeys, LisaMetrics}
import uk.gov.hmrc.lisaapi.models._
import uk.gov.hmrc.lisaapi.services.{AccountService, AuditService}
import uk.gov.hmrc.lisaapi.utils.LisaExtensions._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CloseAccountController extends LisaController with LisaConstants {

  val service: AccountService = AccountService
  val auditService: AuditService = AuditService

  def closeLisaAccount(lisaManager: String, accountId: String): Action[AnyContent] =
    (validateHeader() andThen validateLMRN(lisaManager) andThen validateAccountId(accountId)).async { implicit request =>
      implicit val startTime: Long = System.currentTimeMillis()
      withValidJson[CloseLisaAccountRequest](
        requestData => {
          hasValidDatesForClosure(lisaManager, accountId, requestData) { () =>
            service.closeAccount(lisaManager, accountId, requestData).map {
              case CloseLisaAccountSuccessResponse(`accountId`) =>
                auditService.audit(
                  auditType = "accountClosed",
                  path = getCloseEndpointUrl(lisaManager, accountId),
                  auditData = requestData.toStringMap ++ Map(
                    ZREF -> lisaManager,
                    "accountId" -> accountId
                  )
                )

                LisaMetrics.incrementMetrics(startTime, OK, LisaMetricKeys.CLOSE)

                val data = ApiResponseData(message = "LISA account closed", accountId = Some(accountId))

                Ok(Json.toJson(ApiResponse(data = Some(data), success = true, status = OK)))
              case failure: CloseLisaAccountResponse => {
                handleFailure(lisaManager, accountId, requestData, failure)
              }
            } recover {
              case e: Exception =>
                Logger.error(s"AccountController: closeAccount: An error occurred due to ${e.getMessage} returning internal server error")

                handleFailure(lisaManager, accountId, requestData, CloseLisaAccountErrorResponse)
            }
          }
        },
        lisaManager = lisaManager
      )
    }

  private def hasValidDatesForClosure(lisaManager: String, accountId: String, req: CloseLisaAccountRequest)
                                     (success: () => Future[Result])
                                     (implicit hc: HeaderCarrier, startTime: Long): Future[Result] = {

    if (req.closureDate.isBefore(LISA_START_DATE)) {
      auditService.audit(
        auditType = "accountNotClosed",
        path = getCloseEndpointUrl(lisaManager, accountId),
        auditData = req.toStringMap ++ Map(ZREF -> lisaManager,
          "accountId" -> accountId,
          "reasonNotClosed" -> "FORBIDDEN")
      )

      LisaMetrics.incrementMetrics(startTime, FORBIDDEN, LisaMetricKeys.CLOSE)

      Future.successful(Forbidden(Json.toJson(ErrorForbidden(List(
        ErrorValidation(DATE_ERROR, LISA_START_DATE_ERROR.format("closureDate"), Some("/closureDate"))
      )))))
    }
    else {
      success()
    }
  }

  private def handleFailure(lisaManager: String, accountId: String, request: CloseLisaAccountRequest, failure: CloseLisaAccountResponse)
                           (implicit hc: HeaderCarrier, startTime: Long) = {
    val response: ErrorResponse = apiErrors.getOrElse(failure, ErrorInternalServerError)

    auditService.audit(
      auditType = "accountNotClosed",
      path = getCloseEndpointUrl(lisaManager, accountId),
      auditData = request.toStringMap ++ Map(
        ZREF -> lisaManager,
        "accountId" -> accountId,
        "reasonNotClosed" -> response.errorCode
      )
    )

    LisaMetrics.incrementMetrics(startTime, response.httpStatusCode, LisaMetricKeys.CLOSE)

    response.asResult
  }

  private val apiErrors = Map[CloseLisaAccountResponse, ErrorResponse](
    CloseLisaAccountAlreadyVoidResponse -> ErrorAccountAlreadyVoided,
    CloseLisaAccountAlreadyClosedResponse -> ErrorAccountAlreadyClosed,
    CloseLisaAccountCancellationPeriodExceeded -> ErrorAccountCancellationPeriodExceeded,
    CloseLisaAccountWithinCancellationPeriod -> ErrorAccountWithinCancellationPeriod,
    CloseLisaAccountNotFoundResponse -> ErrorAccountNotFound
  )

  private def getCloseEndpointUrl(lisaManagerReferenceNumber: String, accountID: String): String = {
    s"/manager/$lisaManagerReferenceNumber/accounts/$accountID/close-account"
  }

}
