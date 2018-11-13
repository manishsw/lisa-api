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
import play.api.http.HeaderNames.ACCEPT
import play.api.mvc._

import scala.concurrent.Future

trait APIVersioning {

  val validateVersion: String => Boolean
  val validateContentType: String => Boolean

  def validateHeader(): ActionBuilder[Request] = new ActionBuilder[Request] {
    override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]) = {
      extractAcceptHeader(request) match {
        case Some(AcceptHeader(version, content)) if validateVersion(version) && validateContentType(content) =>
          block(request)
        case Some(AcceptHeader(version, _)) if !validateVersion(version) =>
          Logger.info(s"Request accept header has invalid version: $version")
          Future.successful(ErrorAcceptHeaderVersionInvalid.asResult)
        case Some(AcceptHeader(_, content)) if !validateContentType(content) =>
          Logger.info(s"Request accept header has invalid content type: $content")
          Future.successful(ErrorAcceptHeaderContentInvalid.asResult)
        case None | _ =>
          Logger.info("Request accept header is missing or invalid")
          Future.successful(ErrorAcceptHeaderInvalid.asResult)
      }
    }
  }

  def withApiVersion[A](pf: PartialFunction[Option[String], Future[Result]])
                    (implicit request: Request[A]): Future[Result] = {
    pf.orElse[Option[String], Future[Result]] {
      case Some(version) =>
        Logger.info(s"Request accept header has unimplemented version: $version")
        Future.successful(ErrorAcceptHeaderVersionInvalid.asResult)
      case None =>
        Logger.info("Request accept header is missing or invalid")
        Future.successful(ErrorAcceptHeaderInvalid.asResult)
    }(extractAcceptHeader(request).map(_.version))
  }

  def getAPIVersionFromRequest(implicit request: Request[AnyContent]): Option[String] = {
    extractAcceptHeader(request).map(header => header.version)
  }

  def extractAcceptHeader[A](req: Request[A]): Option[AcceptHeader] = {
    val versionRegex = """^application/vnd\.hmrc\.(\d\.\d)\+(.*)$""".r
    req.headers.get(ACCEPT).flatMap {
      case versionRegex(version, contentType) => Some(AcceptHeader(version, contentType))
      case _ => None
    }
  }

}

case class AcceptHeader(version: String, contentType: String)