package com.github.al.assad.akka.Http

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

/**
 * https://github.com/swagger-akka-http/swagger-akka-http
 *
 * sample: https://github.com/pjfanning/swagger-akka-http-sample
 */

package object d_swagger {

  trait DefaultJsonFormats extends DefaultJsonProtocol with SprayJsonSupport

}
