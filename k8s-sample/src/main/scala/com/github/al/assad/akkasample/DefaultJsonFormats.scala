package com.github.al.assad.akkasample

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

trait DefaultJsonFormats extends DefaultJsonProtocol with SprayJsonSupport
