package com.github.al.assad

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, DurationInt}

package object akka {

  implicit class AwaitableFuture[T](future: Future[T]) {
    def waitResult(timeout: Duration = 3.seconds): T = Await.result(future, timeout)
  }


  def Use[T, R](resource: => T)(func: T => R): R = func(resource)

}
