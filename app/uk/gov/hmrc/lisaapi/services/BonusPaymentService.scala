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

package uk.gov.hmrc.lisaapi.services

import com.google.inject.Inject
import uk.gov.hmrc.lisaapi.connectors.DesConnector
import uk.gov.hmrc.lisaapi.models._
import uk.gov.hmrc.lisaapi.models.des._

import scala.concurrent.{ExecutionContext, Future}
import play.api.Logger
import uk.gov.hmrc.http.HeaderCarrier

class BonusPaymentService @Inject()(desConnector: DesConnector)(implicit ec: ExecutionContext) {

  // scalastyle:off cyclomatic.complexity
  def requestBonusPayment(lisaManager: String, accountId: String, request: RequestBonusPaymentRequest)
                         (implicit hc: HeaderCarrier): Future[RequestBonusPaymentResponse] = {
    val response = desConnector.requestBonusPayment(lisaManager, accountId, request)

    response map {
      case successResponse: DesTransactionResponse => {
        Logger.debug("Matched RequestBonusPaymentSuccessResponse and the message is " + successResponse.message)

        (request.bonuses.claimReason, successResponse.message) match {
          case ("Superseded Bonus", _) => RequestBonusPaymentSupersededResponse(successResponse.transactionID)
          case (_, Some("Late")) => RequestBonusPaymentLateResponse(successResponse.transactionID)
          case (_, _) => RequestBonusPaymentOnTimeResponse(successResponse.transactionID)
        }
      }
      case conflictResponse: DesTransactionExistResponse => {
        Logger.debug("Matched DesTransactionExistResponse and the code is " + conflictResponse.code)

        conflictResponse.code match {
          case "BONUS_CLAIM_ALREADY_EXISTS" => RequestBonusPaymentClaimAlreadyExists(conflictResponse.transactionID)
          case "SUPERSEDED_TRANSACTION_ID_ALREADY_SUPERSEDED" => RequestBonusPaymentAlreadySuperseded(conflictResponse.transactionID)
        }
      }
      case DesUnavailableResponse => {
        Logger.debug("Matched DesUnavailableResponse")

        RequestBonusPaymentServiceUnavailable
      }
      case failureResponse: DesFailureResponse => {
        Logger.debug("Matched DesFailureResponse and the code is " + failureResponse.code)

        failureResponse.code match {
          case "INVESTOR_ACCOUNT_ALREADY_CLOSED_OR_VOID" => RequestBonusPaymentAccountClosedOrVoid
          case "INVESTOR_ACCOUNT_ALREADY_CLOSED" => RequestBonusPaymentAccountClosed
          case "INVESTOR_ACCOUNT_ALREADY_CANCELLED" => RequestBonusPaymentAccountCancelled
          case "INVESTOR_ACCOUNT_ALREADY_VOID" => RequestBonusPaymentAccountVoid
          case "LIFE_EVENT_NOT_FOUND" => RequestBonusPaymentLifeEventNotFound
          case "BONUS_CLAIM_ERROR" => RequestBonusPaymentBonusClaimError
          case "INVESTOR_ACCOUNTID_NOT_FOUND" => RequestBonusPaymentAccountNotFound
          case "SUPERSEDING_TRANSACTION_ID_AMOUNT_MISMATCH" => RequestBonusPaymentSupersededAmountMismatch
          case "SUPERSEDING_TRANSACTION_OUTCOME_ERROR" => RequestBonusPaymentSupersededOutcomeError
          case "ACCOUNT_ERROR_NO_SUBSCRIPTIONS_THIS_TAX_YEAR" => RequestBonusPaymentNoSubscriptions
          case _ => {
            Logger.warn(s"Request bonus payment returned error: ${failureResponse.code}")
            RequestBonusPaymentError
          }
        }
      }
    }
  }

}
